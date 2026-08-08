package api

import (
	"bytes"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"path/filepath"
	"testing"

	"diary/server/internal/store"
)

const testToken = "test-secret-token"

func newTestHandler(t *testing.T) http.Handler {
	t.Helper()
	s, err := store.Open(filepath.Join(t.TempDir(), "test.db"))
	if err != nil {
		t.Fatalf("open store: %v", err)
	}
	t.Cleanup(func() { s.Close() })
	images, err := NewImageStore(filepath.Join(t.TempDir(), "images"))
	if err != nil {
		t.Fatalf("open image store: %v", err)
	}
	return NewHandler(Options{Token: testToken, Store: s, ImageStore: images})
}

func do(t *testing.T, h http.Handler, method, path string, body any, token string) *httptest.ResponseRecorder {
	t.Helper()
	var buf bytes.Buffer
	switch b := body.(type) {
	case nil:
	case []byte:
		buf.Write(b)
	default:
		if err := json.NewEncoder(&buf).Encode(body); err != nil {
			t.Fatalf("encode body: %v", err)
		}
	}
	req := httptest.NewRequest(method, path, &buf)
	if token != "" {
		req.Header.Set("Authorization", "Bearer "+token)
	}
	rec := httptest.NewRecorder()
	h.ServeHTTP(rec, req)
	return rec
}

func TestHealth(t *testing.T) {
	h := newTestHandler(t)
	rec := do(t, h, "GET", "/healthz", nil, testToken)
	if rec.Code != http.StatusOK {
		t.Fatalf("expected 200, got %d", rec.Code)
	}
	var body map[string]string
	if err := json.Unmarshal(rec.Body.Bytes(), &body); err != nil {
		t.Fatalf("invalid json: %v", err)
	}
	if body["status"] != "ok" {
		t.Fatalf("expected status ok, got %q", body["status"])
	}
}

func TestAuth(t *testing.T) {
	h := newTestHandler(t)
	if rec := do(t, h, "GET", "/healthz", nil, ""); rec.Code != http.StatusUnauthorized {
		t.Fatalf("missing token: expected 401, got %d", rec.Code)
	}
	if rec := do(t, h, "GET", "/healthz", nil, "wrong"); rec.Code != http.StatusUnauthorized {
		t.Fatalf("wrong token: expected 401, got %d", rec.Code)
	}
	if rec := do(t, h, "POST", "/sync", syncRequest{}, testToken); rec.Code != http.StatusOK {
		t.Fatalf("valid token: expected 200, got %d", rec.Code)
	}
}

func TestSync_PushesAndReturnsChanges(t *testing.T) {
	h := newTestHandler(t)

	rec := do(t, h, "POST", "/sync", syncRequest{
		Entries: []store.Entry{{
			ID:        "entry-1",
			Title:     "第一篇",
			Body:      "你好",
			DiaryDate: 1_000_000,
			UpdatedAt: 1_000_000,
		}},
	}, testToken)

	var resp syncResponse
	if err := json.Unmarshal(rec.Body.Bytes(), &resp); err != nil {
		t.Fatalf("decode: %v", err)
	}
	if rec.Code != http.StatusOK {
		t.Fatalf("expected 200, got %d: %s", rec.Code, rec.Body.String())
	}
	if len(resp.Changes) != 1 {
		t.Fatalf("expected 1 change, got %d", len(resp.Changes))
	}
	if resp.Changes[0].ID != "entry-1" {
		t.Fatalf("expected entry-1, got %s", resp.Changes[0].ID)
	}
	if resp.ServerSeq == 0 {
		t.Fatal("expected serverSeq")
	}
	if resp.ServerTime == 0 {
		t.Fatal("expected serverTime")
	}
}

func TestSync_LastWriteWins(t *testing.T) {
	h := newTestHandler(t)

	older := store.Entry{ID: "e1", Title: "旧版", Body: "旧", DiaryDate: 1, UpdatedAt: 100}
	newer := store.Entry{ID: "e1", Title: "新版", Body: "新", DiaryDate: 2, UpdatedAt: 200}
	stale := store.Entry{ID: "e1", Title: "过期", Body: "过期", DiaryDate: 3, UpdatedAt: 150}

	do(t, h, "POST", "/sync", syncRequest{Entries: []store.Entry{older}}, testToken)

	rec := do(t, h, "POST", "/sync", syncRequest{Entries: []store.Entry{newer, stale}}, testToken)
	var resp syncResponse
	if err := json.Unmarshal(rec.Body.Bytes(), &resp); err != nil {
		t.Fatalf("decode: %v", err)
	}

	var final *store.Entry
	for _, e := range resp.Changes {
		if e.ID == "e1" {
			final = &e
		}
	}
	if final == nil {
		t.Fatal("e1 not in changes")
	}
	if final.Title != "新版" || final.UpdatedAt != 200 {
		t.Fatalf("expected newer version to win, got %+v", final)
	}
}

func TestSync_TombstoneSurvives(t *testing.T) {
	h := newTestHandler(t)

	live := store.Entry{ID: "e1", Title: "要删的", Body: "x", DiaryDate: 1, UpdatedAt: 100}
	do(t, h, "POST", "/sync", syncRequest{Entries: []store.Entry{live}}, testToken)

	tombstone := store.Entry{ID: "e1", Deleted: true, DeletedAt: 150, UpdatedAt: 150}
	rec := do(t, h, "POST", "/sync", syncRequest{Entries: []store.Entry{tombstone}}, testToken)

	var resp syncResponse
	if err := json.Unmarshal(rec.Body.Bytes(), &resp); err != nil {
		t.Fatalf("decode: %v", err)
	}
	var final *store.Entry
	for _, e := range resp.Changes {
		if e.ID == "e1" {
			final = &e
		}
	}
	if final == nil {
		t.Fatal("e1 not in changes")
	}
	if !final.Deleted {
		t.Fatalf("expected tombstone, got %+v", final)
	}
}

func TestSync_SeqWatermarkFiltersChanges(t *testing.T) {
	h := newTestHandler(t)

	do(t, h, "POST", "/sync", syncRequest{
		Entries: []store.Entry{
			{ID: "e-old", Body: "旧", DiaryDate: 1, UpdatedAt: 100},
			{ID: "e-new", Body: "新", DiaryDate: 2, UpdatedAt: 200},
		},
	}, testToken)

	// Watermark after the first push: subsequent syncs must not re-send e-old.
	rec1 := do(t, h, "POST", "/sync", syncRequest{}, testToken)
	var resp1 syncResponse
	if err := json.Unmarshal(rec1.Body.Bytes(), &resp1); err != nil {
		t.Fatalf("decode: %v", err)
	}

	rec := do(t, h, "POST", "/sync", syncRequest{Entries: []store.Entry{
		{ID: "e-later", Body: "later", DiaryDate: 3, UpdatedAt: 300},
	}, SinceSeq: resp1.ServerSeq}, testToken)
	var resp syncResponse
	if err := json.Unmarshal(rec.Body.Bytes(), &resp); err != nil {
		t.Fatalf("decode: %v", err)
	}
	_ = resp1.ServerSeq
	if len(resp.Changes) != 1 {
		t.Fatalf("expected only new change since watermark, got %d", len(resp.Changes))
	}
	if resp.Changes[0].ID != "e-later" {
		t.Fatalf("expected e-later, got %s", resp.Changes[0].ID)
	}
}

func TestSync_RejectsMissingID(t *testing.T) {
	h := newTestHandler(t)
	rec := do(t, h, "POST", "/sync", syncRequest{
		Entries: []store.Entry{{Body: "no id"}},
	}, testToken)
	if rec.Code != http.StatusBadRequest {
		t.Fatalf("expected 400, got %d", rec.Code)
	}
}
