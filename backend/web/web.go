// Package web embeds the frontend built by the Docker frontend stage.
package web

import (
	"bytes"
	"embed"
	"io/fs"
	"net/http"
	"path"
	"strings"
	"time"
)

// all: is required, otherwise files starting with _ or . (e.g. Vite's
// _plugin-vue_export-helper-*.js chunk) are silently left out.
//
//go:embed all:dist
var assets embed.FS

func Handler() http.Handler {
	sub, _ := fs.Sub(assets, "dist")
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if strings.HasPrefix(r.URL.Path, "/api/") {
			http.NotFound(w, r)
			return
		}
		name := strings.TrimPrefix(path.Clean(r.URL.Path), "/")
		if name == "" || name == "." {
			name = "index.html"
		}
		data, err := fs.ReadFile(sub, name)
		if err != nil && !strings.HasPrefix(name, "assets/") {
			name = "index.html"
			data, err = fs.ReadFile(sub, name)
		}
		if err != nil {
			http.NotFound(w, r)
			return
		}
		if strings.HasPrefix(name, "assets/") {
			w.Header().Set("Cache-Control", "max-age=31536000, public, immutable")
		} else {
			w.Header().Set("Cache-Control", "no-cache, must-revalidate")
		}
		http.ServeContent(w, r, name, time.Time{}, bytes.NewReader(data))
	})
}
