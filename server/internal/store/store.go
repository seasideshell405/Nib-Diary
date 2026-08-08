package store

import (
	"database/sql"
	"errors"
	"fmt"
	"os"
	"path/filepath"
	"time"

	_ "modernc.org/sqlite"
)

// Entry mirrors the client's diary entry shape.
type Entry struct {
	ID         string `json:"id"`
	Title      string `json:"title"`
	Body       string `json:"body"`
	Mood       string `json:"mood"`
	Weather    string `json:"weather"`
	DiaryDate  int64  `json:"diaryDate"`
	UpdatedAt  int64  `json:"updatedAt"`
	Deleted    bool   `json:"deleted"`
	DeletedAt  int64  `json:"deletedAt,omitempty"`
}

// Store is the server-side persistence layer.
type Store struct {
	db *sql.DB
}

// Open opens (or creates) the SQLite database at the given path.
func Open(path string) (*Store, error) {
	if err := os.MkdirAll(filepath.Dir(path), 0o755); err != nil {
		return nil, fmt.Errorf("create db dir: %w", err)
	}
	db, err := sql.Open("sqlite", path)
	if err != nil {
		return nil, fmt.Errorf("open db: %w", err)
	}
	if err := db.Ping(); err != nil {
		return nil, fmt.Errorf("ping db: %w", err)
	}
	s := &Store{db: db}
	if err := s.migrate(); err != nil {
		return nil, err
	}
	return s, nil
}

func (s *Store) migrate() error {
	_, err := s.db.Exec(`
CREATE TABLE IF NOT EXISTS entries (
	id         TEXT PRIMARY KEY,
	title      TEXT NOT NULL DEFAULT '',
	body       TEXT NOT NULL,
	mood       TEXT NOT NULL DEFAULT '',
	weather    TEXT NOT NULL DEFAULT '',
	diary_date INTEGER NOT NULL,
	updated_at INTEGER NOT NULL,
	deleted    INTEGER NOT NULL DEFAULT 0,
	deleted_at INTEGER NOT NULL DEFAULT 0,
	seq        INTEGER NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_entries_updated_at ON entries(updated_at);
CREATE INDEX IF NOT EXISTS idx_entries_seq ON entries(seq);
CREATE TABLE IF NOT EXISTS images (
	id       TEXT PRIMARY KEY,
	entry_id TEXT NOT NULL,
	size     INTEGER NOT NULL DEFAULT 0
);
CREATE TABLE IF NOT EXISTS meta (
	key   TEXT PRIMARY KEY,
	value INTEGER NOT NULL
);
CREATE TABLE IF NOT EXISTS profile (
	id         INTEGER PRIMARY KEY CHECK (id = 1),
	nickname   TEXT NOT NULL DEFAULT '',
	signature  TEXT NOT NULL DEFAULT '',
	avatar_url TEXT NOT NULL DEFAULT '',
	updated_at INTEGER NOT NULL DEFAULT 0
);
`)
	if err != nil {
		return fmt.Errorf("migrate: %w", err)
	}

	// Upgrade path: add the seq column if this database predates it,
	// backfilling by rowid so the watermark stays monotonic.
	var hasSeqCol int
	if err := s.db.QueryRow(
		"SELECT COUNT(*) FROM pragma_table_info('entries') WHERE name = 'seq'",
	).Scan(&hasSeqCol); err != nil {
		return fmt.Errorf("check seq column: %w", err)
	}
	if hasSeqCol == 0 {
		if _, err := s.db.Exec("ALTER TABLE entries ADD COLUMN seq INTEGER NOT NULL DEFAULT 0"); err != nil {
			return fmt.Errorf("add seq column: %w", err)
		}
		if _, err := s.db.Exec("UPDATE entries SET seq = rowid"); err != nil {
			return fmt.Errorf("backfill seq: %w", err)
		}
	}

	var maxSeq int64
	if err := s.db.QueryRow("SELECT COALESCE(MAX(seq), 0) FROM entries").Scan(&maxSeq); err != nil {
		return fmt.Errorf("check seq: %w", err)
	}
	if _, err := s.db.Exec(
		"INSERT INTO meta (key, value) VALUES ('seq', ?) ON CONFLICT(key) DO NOTHING", maxSeq,
	); err != nil {
		return fmt.Errorf("init meta seq: %w", err)
	}
	return nil
}

// nextSeq allocates the next monotonic change sequence number.
func (s *Store) nextSeq() (int64, error) {
	var seq int64
	err := s.db.QueryRow(`
INSERT INTO meta (key, value) VALUES ('seq', 1)
ON CONFLICT(key) DO UPDATE SET value = value + 1
RETURNING value`, "seq").Scan(&seq)
	if err != nil {
		return 0, fmt.Errorf("next seq: %w", err)
	}
	return seq, nil
}

// Image describes an uploaded image file.
type Image struct {
	ID      string `json:"id"`
	EntryID string `json:"entryId"`
	Size    int64  `json:"size"`
}

// Profile is the single-user profile (nickname, signature, avatar URL).
type Profile struct {
	Nickname  string `json:"nickname"`
	Signature string `json:"signature"`
	AvatarURL string `json:"avatarUrl"`
	UpdatedAt int64  `json:"updatedAt"`
}

// GetProfile returns the stored profile, or a zero value when unset.
func (s *Store) GetProfile() (Profile, error) {
	var p Profile
	err := s.db.QueryRow(
		"SELECT nickname, signature, avatar_url, updated_at FROM profile WHERE id = 1",
	).Scan(&p.Nickname, &p.Signature, &p.AvatarURL, &p.UpdatedAt)
	if errors.Is(err, sql.ErrNoRows) {
		return Profile{}, nil
	}
	if err != nil {
		return Profile{}, fmt.Errorf("get profile: %w", err)
	}
	return p, nil
}

// SaveProfile overwrites the single profile row.
func (s *Store) SaveProfile(p Profile) error {
	_, err := s.db.Exec(`
INSERT INTO profile (id, nickname, signature, avatar_url, updated_at)
VALUES (1, ?, ?, ?, ?)
ON CONFLICT(id) DO UPDATE SET
	nickname = excluded.nickname,
	signature = excluded.signature,
	avatar_url = excluded.avatar_url,
	updated_at = excluded.updated_at`,
		p.Nickname, p.Signature, p.AvatarURL, p.UpdatedAt)
	if err != nil {
		return fmt.Errorf("save profile: %w", err)
	}
	return nil
}

// SaveImage records an image and returns the previous size (0 if new).
func (s *Store) SaveImage(id, entryID string, size int64) (Image, error) {
	var prev int64
	err := s.db.QueryRow("SELECT COALESCE(MAX(size), 0) FROM images WHERE id = ?", id).Scan(&prev)
	if err != nil {
		return Image{}, fmt.Errorf("query image: %w", err)
	}
	_, err = s.db.Exec(`
INSERT INTO images (id, entry_id, size) VALUES (?, ?, ?)
ON CONFLICT(id) DO UPDATE SET entry_id = excluded.entry_id, size = excluded.size`,
		id, entryID, size)
	if err != nil {
		return Image{}, fmt.Errorf("save image: %w", err)
	}
	return Image{ID: id, EntryID: entryID, Size: size}, nil
}

// GetImage returns the image metadata.
func (s *Store) GetImage(id string) (Image, error) {
	var img Image
	err := s.db.QueryRow("SELECT id, entry_id, size FROM images WHERE id = ?", id).Scan(&img.ID, &img.EntryID, &img.Size)
	if err != nil {
		return Image{}, err
	}
	return img, nil
}

// DeleteImage removes an image record; returns the deleted metadata.
func (s *Store) DeleteImage(id string) (Image, error) {
	img, err := s.GetImage(id)
	if err != nil {
		return Image{}, err
	}
	_, err = s.db.Exec("DELETE FROM images WHERE id = ?", id)
	if err != nil {
		return Image{}, fmt.Errorf("delete image: %w", err)
	}
	return img, nil
}

// DeleteEntryImages removes all images belonging to an entry.
func (s *Store) DeleteEntryImages(entryID string) ([]Image, error) {
	rows, err := s.db.Query("SELECT id, entry_id, size FROM images WHERE entry_id = ?", entryID)
	if err != nil {
		return nil, fmt.Errorf("query entry images: %w", err)
	}
	defer rows.Close()
	var out []Image
	for rows.Next() {
		var img Image
		if err := rows.Scan(&img.ID, &img.EntryID, &img.Size); err != nil {
			return nil, fmt.Errorf("scan image: %w", err)
		}
		out = append(out, img)
	}
	if err := rows.Err(); err != nil {
		return nil, err
	}
	if _, err := s.db.Exec("DELETE FROM images WHERE entry_id = ?", entryID); err != nil {
		return nil, fmt.Errorf("delete entry images: %w", err)
	}
	return out, nil
}

// Upsert applies an incoming client entry with last-write-wins semantics:
// an existing row is replaced only when the incoming updatedAt is newer
// (or equal, to make sync idempotent).
func (s *Store) Upsert(e Entry) (Entry, error) {
	var current Entry
	row := s.db.QueryRow(
		"SELECT title, body, mood, weather, diary_date, updated_at, deleted, deleted_at FROM entries WHERE id = ?",
		e.ID,
	)
	err := row.Scan(&current.Title, &current.Body, &current.Mood, &current.Weather,
		&current.DiaryDate, &current.UpdatedAt, &current.Deleted, &current.DeletedAt)
	switch {
	case errors.Is(err, sql.ErrNoRows):
		current.UpdatedAt = -1
	case err != nil:
		return Entry{}, fmt.Errorf("query entry: %w", err)
	}

	if e.UpdatedAt < current.UpdatedAt {
		return current, nil
	}

	seq, err := s.nextSeq()
	if err != nil {
		return Entry{}, err
	}

	_, err = s.db.Exec(`
INSERT INTO entries (id, title, body, mood, weather, diary_date, updated_at, deleted, deleted_at, seq)
VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
ON CONFLICT(id) DO UPDATE SET
	title = excluded.title,
	body = excluded.body,
	mood = excluded.mood,
	weather = excluded.weather,
	diary_date = excluded.diary_date,
	updated_at = excluded.updated_at,
	deleted = excluded.deleted,
	deleted_at = excluded.deleted_at,
	seq = excluded.seq`,
		e.ID, e.Title, e.Body, e.Mood, e.Weather, e.DiaryDate, e.UpdatedAt,
		toInt(e.Deleted), e.DeletedAt, seq,
	)
	if err != nil {
		return Entry{}, fmt.Errorf("upsert entry: %w", err)
	}
	return e, nil
}

// EntriesSinceSeq returns all entries (including tombstones) whose change
// sequence number is greater than sinceSeq, in seq order. Every upsert
// (insert or LWW update) allocates a fresh seq, so the watermark never
// misses an update to an existing entry.
func (s *Store) EntriesSinceSeq(sinceSeq int64) ([]Entry, error) {
	rows, err := s.db.Query(
		"SELECT id, title, body, mood, weather, diary_date, updated_at, deleted, deleted_at FROM entries WHERE seq > ? ORDER BY seq ASC",
		sinceSeq,
	)
	if err != nil {
		return nil, fmt.Errorf("query changes: %w", err)
	}
	defer rows.Close()

	// Never return a nil slice: JSON clients expect an array, not null.
	out := make([]Entry, 0)
	for rows.Next() {
		var e Entry
		if err := rows.Scan(&e.ID, &e.Title, &e.Body, &e.Mood, &e.Weather,
			&e.DiaryDate, &e.UpdatedAt, &e.Deleted, &e.DeletedAt); err != nil {
			return nil, fmt.Errorf("scan entry: %w", err)
		}
		out = append(out, e)
	}
	return out, rows.Err()
}

// ServerSeq returns the highest change sequence number, the server-side watermark.
func (s *Store) ServerSeq() (int64, error) {
	var seq int64
	err := s.db.QueryRow("SELECT COALESCE(MAX(seq), 0) FROM entries").Scan(&seq)
	if err != nil {
		return 0, fmt.Errorf("query server seq: %w", err)
	}
	return seq, nil
}

func (s *Store) Close() error {
	return s.db.Close()
}

func toInt(b bool) int {
	if b {
		return 1
	}
	return 0
}

func NowMillis() int64 {
	return time.Now().UnixMilli()
}
