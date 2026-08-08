package api

import (
	"encoding/json"
	"io"
	"log"
	"net/http"
	"os"
	"path/filepath"
	"strconv"
	"strings"
)

// ImageStore serves as the on-disk image repository.
type ImageStore struct {
	dir string
}

func NewImageStore(dir string) (*ImageStore, error) {
	if err := os.MkdirAll(dir, 0o755); err != nil {
		return nil, err
	}
	return &ImageStore{dir: dir}, nil
}

func (is *ImageStore) path(id string) string {
	return filepath.Join(is.dir, sanitize(id)+".img")
}

func (is *ImageStore) Save(id string, r io.Reader) (int64, error) {
	f, err := os.Create(is.path(id))
	if err != nil {
		return 0, err
	}
	defer f.Close()
	n, err := io.Copy(f, r)
	if err != nil {
		return 0, err
	}
	return n, nil
}

func (is *ImageStore) Open(id string) (*os.File, int64, error) {
	f, err := os.Open(is.path(id))
	if err != nil {
		return nil, 0, err
	}
	st, err := f.Stat()
	if err != nil {
		f.Close()
		return nil, 0, err
	}
	return f, st.Size(), nil
}

func (is *ImageStore) Delete(id string) error {
	err := os.Remove(is.path(id))
	if os.IsNotExist(err) {
		return nil
	}
	return err
}

func sanitize(id string) string {
	return strings.Map(func(r rune) rune {
		if r >= 'a' && r <= 'z' || r >= 'A' && r <= 'Z' || r >= '0' && r <= '9' || r == '-' {
			return r
		}
		return -1
	}, id)
}

// handleImageUpload handles PUT /images/{id}.
func (s *Server) handleImageUpload(images *ImageStore) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		id := r.PathValue("id")
		entryID := r.URL.Query().Get("entryId")
		if id == "" || entryID == "" {
			http.Error(w, "id and entryId required", http.StatusBadRequest)
			return
		}

		size, err := images.Save(id, r.Body)
		if err != nil {
			log.Printf("save image failed: %v", err)
			http.Error(w, "internal error", http.StatusInternalServerError)
			return
		}
		if _, err := s.store.SaveImage(id, entryID, size); err != nil {
			log.Printf("save image record failed: %v", err)
			http.Error(w, "internal error", http.StatusInternalServerError)
			return
		}

		w.Header().Set("Content-Type", "application/json")
		json.NewEncoder(w).Encode(map[string]any{"id": id, "entryId": entryID, "size": size})
	}
}

// handleImageGet handles GET /images/{id}.
func (s *Server) handleImageGet(images *ImageStore) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		id := r.PathValue("id")
		f, size, err := images.Open(id)
		if err != nil {
			http.Error(w, "not found", http.StatusNotFound)
			return
		}
		defer f.Close()
		w.Header().Set("Content-Type", "image/webp")
		w.Header().Set("Content-Length", strconv.FormatInt(size, 10))
		io.Copy(w, f)
	}
}

// handleImageDelete handles DELETE /images/{id}.
func (s *Server) handleImageDelete(images *ImageStore) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		id := r.PathValue("id")
		if _, err := s.store.DeleteImage(id); err != nil {
			http.Error(w, "not found", http.StatusNotFound)
			return
		}
		_ = images.Delete(id)
		w.WriteHeader(http.StatusNoContent)
	}
}
