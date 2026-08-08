package main

import (
	"log"
	"net/http"
	"os"

	"diary/server/internal/api"
	"diary/server/internal/store"
)

func main() {
	token := os.Getenv("DIARY_TOKEN")
	if token == "" {
		log.Fatal("DIARY_TOKEN environment variable is required")
	}

	addr := os.Getenv("DIARY_ADDR")
	if addr == "" {
		addr = ":8080"
	}

	dbPath := os.Getenv("DIARY_DB")
	if dbPath == "" {
		dbPath = "data/diary.db"
	}

	imgDir := os.Getenv("DIARY_IMAGES")
	if imgDir == "" {
		imgDir = "data/images"
	}

	s, err := store.Open(dbPath)
	if err != nil {
		log.Fatalf("open store: %v", err)
	}
	defer s.Close()

	images, err := api.NewImageStore(imgDir)
	if err != nil {
		log.Fatalf("open image store: %v", err)
	}

	handler := api.NewHandler(api.Options{
		Token:      token,
		Store:      s,
		ImageStore: images,
	})
	log.Printf("diary server listening on %s", addr)
	if err := http.ListenAndServe(addr, handler); err != nil {
		log.Fatal(err)
	}
}
