package api

import (
	"bytes"
	"net/http"
	"path/filepath"
	"testing"

	"diary/server/internal/store"
)

func TestImageLifecycle(t *testing.T) {
	h := newTestHandler(t)

	// Upload
	payload := []byte("fake-jpeg-bytes-12345")
	up := do(t, h, "PUT", "/images/img-1?entryId=entry-1", payload, testToken)
	if up.Code != 200 {
		t.Fatalf("expected 200 on upload, got %d", up.Code)
	}

	// Download
	down := do(t, h, "GET", "/images/img-1", nil, testToken)
	if down.Code != 200 {
		t.Fatalf("expected 200 on download, got %d", down.Code)
	}
	got := down.Body.Bytes()
	if !bytes.Equal(got, payload) {
		t.Fatalf("downloaded bytes differ: %q vs %q", got, payload)
	}

	// Delete
	del := do(t, h, "DELETE", "/images/img-1", nil, testToken)
	if del.Code != 204 {
		t.Fatalf("expected 204 on delete, got %d", del.Code)
	}

	// Download after delete -> 404
	after := do(t, h, "GET", "/images/img-1", nil, testToken)
	if after.Code != 404 {
		t.Fatalf("expected 404 after delete, got %d", after.Code)
	}
}

func TestImageUpload_RequiresAuth(t *testing.T) {
	h := newTestHandler(t)
	resp := do(t, h, "PUT", "/images/img-1?entryId=e1", []byte("x"), "")
	if resp.Code != 401 {
		t.Fatalf("expected 401, got %d", resp.Code)
	}
}

func TestImageUpload_MissingEntryID(t *testing.T) {
	h := newTestHandler(t)
	resp := do(t, h, "PUT", "/images/img-1", []byte("x"), testToken)
	if resp.Code != 400 {
		t.Fatalf("expected 400, got %d", resp.Code)
	}
}

func TestEntryImagesDeletedWithEntry(t *testing.T) {
	s, err := store.Open(filepath.Join(t.TempDir(), "t.db"))
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() { s.Close() })
	images, err := NewImageStore(filepath.Join(t.TempDir(), "img"))
	if err != nil {
		t.Fatal(err)
	}
	h := NewHandler(Options{Token: testToken, Store: s, ImageStore: images})

	upload(t, h, "a-1", "entry-x")
	upload(t, h, "a-2", "entry-x")
	upload(t, h, "b-1", "entry-y")

	deleted, err := s.DeleteEntryImages("entry-x")
	if err != nil {
		t.Fatalf("delete entry images: %v", err)
	}
	if len(deleted) != 2 {
		t.Fatalf("expected 2 deleted, got %d", len(deleted))
	}

	if _, err := s.GetImage("b-1"); err != nil {
		t.Fatalf("b-1 should remain: %v", err)
	}
}

func upload(t *testing.T, h http.Handler, id, entryID string) {
	t.Helper()
	resp := do(t, h, "PUT", "/images/"+id+"?entryId="+entryID, []byte("data"), testToken)
	if resp.Code != 200 {
		t.Fatalf("upload %s failed: %d", id, resp.Code)
	}
}
