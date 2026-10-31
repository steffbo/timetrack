package server

import (
	"bytes"
	"compress/gzip"
	"net/http"
	"net/http/httptest"
	"strings"
)

func compress(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if !strings.Contains(r.Header.Get("Accept-Encoding"), "gzip") {
			next.ServeHTTP(w, r)
			return
		}
		rec := httptest.NewRecorder()
		next.ServeHTTP(rec, r)
		for k, vs := range rec.Header() {
			w.Header()[k] = vs
		}
		contentType := strings.Split(rec.Header().Get("Content-Type"), ";")[0]
		enabled := false
		switch contentType {
		case "text/html", "text/xml", "text/plain", "text/css", "text/javascript",
			"application/javascript", "application/json":
			enabled = true
		}
		if rec.Body.Len() < 1024 || !enabled {
			w.WriteHeader(rec.Code)
			_, _ = w.Write(rec.Body.Bytes())
			return
		}
		var b bytes.Buffer
		gz := gzip.NewWriter(&b)
		_, _ = gz.Write(rec.Body.Bytes())
		_ = gz.Close()
		w.Header().Del("Content-Length")
		w.Header().Set("Content-Encoding", "gzip")
		w.Header().Add("Vary", "Accept-Encoding")
		w.WriteHeader(rec.Code)
		_, _ = w.Write(b.Bytes())
	})
}
