package api

import (
	"encoding/json"
	"log"
	"net/http"
	"strconv"
	"time"

	"diary/server/internal/store"
)

type Server struct {
	store *store.Store
}

func NewServer(s *store.Store) *Server {
	return &Server{store: s}
}

type syncRequest struct {
	Entries []store.Entry `json:"entries"`
	// SinceSeq is the client's last-seen server change watermark (rowid).
	SinceSeq int64 `json:"sinceSeq"`
}

type syncResponse struct {
	Changes    []store.Entry `json:"changes"`
	ServerSeq  int64         `json:"serverSeq"`
	ServerTime int64         `json:"serverTime"`
}

// handleSync handles POST /sync: the client pushes its local changes
// (last-write-wins on the server) and receives everything the server knows
// changed since the client's watermark, in one round trip.
func (s *Server) handleSync(w http.ResponseWriter, r *http.Request) {
	var req syncRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		http.Error(w, "invalid body", http.StatusBadRequest)
		return
	}

	for _, e := range req.Entries {
		if e.ID == "" {
			http.Error(w, "entry id required", http.StatusBadRequest)
			return
		}
		if _, err := s.store.Upsert(e); err != nil {
			log.Printf("upsert failed: %v", err)
			http.Error(w, "internal error", http.StatusInternalServerError)
			return
		}
	}

	changes, err := s.store.EntriesSinceSeq(req.SinceSeq)
	if err != nil {
		log.Printf("query changes failed: %v", err)
		http.Error(w, "internal error", http.StatusInternalServerError)
		return
	}
	serverSeq, err := s.store.ServerSeq()
	if err != nil {
		log.Printf("query server seq failed: %v", err)
		http.Error(w, "internal error", http.StatusInternalServerError)
		return
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(syncResponse{
		Changes:    changes,
		ServerSeq:  serverSeq,
		ServerTime: time.Now().UnixMilli(),
	})
}

// handleChanges handles GET /changes?sinceSeq=<n>: read-only change stream.
func (s *Server) handleChanges(w http.ResponseWriter, r *http.Request) {
	sinceSeq, err := strconv.ParseInt(r.URL.Query().Get("sinceSeq"), 10, 64)
	if err != nil {
		sinceSeq = 0
	}

	changes, err := s.store.EntriesSinceSeq(sinceSeq)
	if err != nil {
		log.Printf("query changes failed: %v", err)
		http.Error(w, "internal error", http.StatusInternalServerError)
		return
	}
	serverSeq, err := s.store.ServerSeq()
	if err != nil {
		log.Printf("query server seq failed: %v", err)
		http.Error(w, "internal error", http.StatusInternalServerError)
		return
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(syncResponse{
		Changes:    changes,
		ServerSeq:  serverSeq,
		ServerTime: time.Now().UnixMilli(),
	})
}

// handleProfileGet handles GET /profile.
func (s *Server) handleProfileGet(w http.ResponseWriter, r *http.Request) {
	p, err := s.store.GetProfile()
	if err != nil {
		log.Printf("get profile failed: %v", err)
		http.Error(w, "internal error", http.StatusInternalServerError)
		return
	}
	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(p)
}

// handleProfilePut handles PUT /profile: overwrite the single profile.
func (s *Server) handleProfilePut(w http.ResponseWriter, r *http.Request) {
	var p store.Profile
	if err := json.NewDecoder(r.Body).Decode(&p); err != nil {
		http.Error(w, "invalid body", http.StatusBadRequest)
		return
	}
	if err := s.store.SaveProfile(p); err != nil {
		log.Printf("save profile failed: %v", err)
		http.Error(w, "internal error", http.StatusInternalServerError)
		return
	}
	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(p)
}
