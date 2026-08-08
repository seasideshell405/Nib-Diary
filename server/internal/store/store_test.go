package store

import (
	"path/filepath"
	"testing"
)

func openTest(t *testing.T) *Store {
	t.Helper()
	s, err := Open(filepath.Join(t.TempDir(), "test.db"))
	if err != nil {
		t.Fatalf("open: %v", err)
	}
	t.Cleanup(func() { s.Close() })
	return s
}

func TestUpsert_CreatesNewEntry(t *testing.T) {
	s := openTest(t)
	e := Entry{ID: "a", Title: "t", Body: "b", DiaryDate: 1, UpdatedAt: 10}

	got, err := s.Upsert(e)
	if err != nil {
		t.Fatalf("upsert: %v", err)
	}
	if got.ID != "a" {
		t.Fatalf("expected id a, got %s", got.ID)
	}

	changes, err := s.EntriesSinceSeq(0)
	if err != nil {
		t.Fatalf("since: %v", err)
	}
	if len(changes) != 1 || changes[0].Body != "b" {
		t.Fatalf("unexpected changes: %+v", changes)
	}
}

func TestUpsert_RejectsStale(t *testing.T) {
	s := openTest(t)

	if _, err := s.Upsert(Entry{ID: "a", Body: "v1", DiaryDate: 1, UpdatedAt: 200}); err != nil {
		t.Fatalf("upsert: %v", err)
	}
	got, err := s.Upsert(Entry{ID: "a", Body: "v2-stale", DiaryDate: 1, UpdatedAt: 100})
	if err != nil {
		t.Fatalf("upsert: %v", err)
	}
	if got.Body != "v1" {
		t.Fatalf("expected v1 to win, got %q", got.Body)
	}

	got, err = s.Upsert(Entry{ID: "a", Body: "v3", DiaryDate: 1, UpdatedAt: 300})
	if err != nil {
		t.Fatalf("upsert: %v", err)
	}
	if got.Body != "v3" {
		t.Fatalf("expected v3 to win, got %q", got.Body)
	}
}

func TestUpsert_EqualTimestampIsIdempotent(t *testing.T) {
	s := openTest(t)

	if _, err := s.Upsert(Entry{ID: "a", Body: "x", DiaryDate: 1, UpdatedAt: 100}); err != nil {
		t.Fatalf("upsert: %v", err)
	}
	if _, err := s.Upsert(Entry{ID: "a", Body: "x", DiaryDate: 1, UpdatedAt: 100}); err != nil {
		t.Fatalf("upsert: %v", err)
	}

	changes, err := s.EntriesSinceSeq(0)
	if err != nil {
		t.Fatalf("since: %v", err)
	}
	if len(changes) != 1 {
		t.Fatalf("expected single row, got %d", len(changes))
	}
}

func TestEntriesSinceSeq_MonotonicWatermark(t *testing.T) {
	s := openTest(t)

	for _, e := range []Entry{
		{ID: "1", Body: "a", DiaryDate: 1, UpdatedAt: 100},
		{ID: "2", Body: "b", DiaryDate: 2, UpdatedAt: 200},
		{ID: "3", Body: "c", DiaryDate: 3, UpdatedAt: 300},
	} {
		if _, err := s.Upsert(e); err != nil {
			t.Fatalf("upsert: %v", err)
		}
	}

	seq, err := s.ServerSeq()
	if err != nil {
		t.Fatalf("server seq: %v", err)
	}

	changes, err := s.EntriesSinceSeq(seq - 1)
	if err != nil {
		t.Fatalf("since: %v", err)
	}
	if len(changes) != 1 {
		t.Fatalf("expected 1 change after watermark, got %d", len(changes))
	}
	if changes[0].ID != "3" {
		t.Fatalf("expected entry 3, got %+v", changes)
	}

	// Updating an existing row (LWW update) MUST issue a new change so
	// other devices see the modification.
	_, _ = s.Upsert(Entry{ID: "2", Body: "b2", DiaryDate: 2, UpdatedAt: 250})
	after, err := s.EntriesSinceSeq(seq - 1)
	if err != nil {
		t.Fatalf("since: %v", err)
	}
	if len(after) != 2 {
		t.Fatalf("expected 2 changes (update re-issues), got %d", len(after))
	}
	if after[1].ID != "2" || after[1].Body != "b2" {
		t.Fatalf("expected updated entry 2 last, got %+v", after)
	}
}

func TestEntriesSinceSeq_IncludesTombstones(t *testing.T) {
	s := openTest(t)

	_, _ = s.Upsert(Entry{ID: "a", Body: "x", DiaryDate: 1, UpdatedAt: 100})
	_, _ = s.Upsert(Entry{ID: "a", Deleted: true, DeletedAt: 150, UpdatedAt: 150})

	changes, err := s.EntriesSinceSeq(0)
	if err != nil {
		t.Fatalf("since: %v", err)
	}
	if len(changes) != 1 {
		t.Fatalf("expected 1 row (tombstone replaces live), got %d", len(changes))
	}
	if !changes[0].Deleted {
		t.Fatalf("expected tombstone, got %+v", changes[0])
	}
}
