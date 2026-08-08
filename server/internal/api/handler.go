package api

import (
	"encoding/json"
	"net/http"

	"diary/server/internal/store"
)

// Options carries the dependencies for building the API handler.
type Options struct {
	Token      string
	Store      *store.Store
	ImageStore *ImageStore
}

// NewHandler builds the HTTP handler.
func NewHandler(o Options) http.Handler {
	server := NewServer(o.Store)

	mux := http.NewServeMux()
	mux.HandleFunc("GET /healthz", handleHealth)
	mux.HandleFunc("POST /sync", server.handleSync)
	mux.HandleFunc("GET /changes", server.handleChanges)
	mux.HandleFunc("PUT /images/{id}", server.handleImageUpload(o.ImageStore))
	mux.HandleFunc("GET /images/{id}", server.handleImageGet(o.ImageStore))
	mux.HandleFunc("DELETE /images/{id}", server.handleImageDelete(o.ImageStore))
	mux.HandleFunc("GET /profile", server.handleProfileGet)
	mux.HandleFunc("PUT /profile", server.handleProfilePut)

	var handler http.Handler = mux
	handler = authMiddleware(o.Token, handler)
	return handler
}

func handleHealth(w http.ResponseWriter, r *http.Request) {
	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(map[string]string{"status": "ok"})
}

func authMiddleware(token string, next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.Header.Get("Authorization") != "Bearer "+token {
			http.Error(w, "unauthorized", http.StatusUnauthorized)
			return
		}
		next.ServeHTTP(w, r)
	})
}
