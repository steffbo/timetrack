// Package httpx centralizes the Java exception-to-JSON mapping.
package httpx

import (
	"encoding/json"
	"errors"
	"net/http"

	"github.com/steffbo/timetrack/backend-go/internal/clock"
)

type Error struct {
	Status        int
	Code, Message string
	Details       *string
}

func (e *Error) Error() string { return e.Message }
func New(status int, code, msg string) *Error {
	return &Error{Status: status, Code: code, Message: msg}
}
func NotFound(code, msg string) *Error  { return New(404, code, msg) }
func Conflict(code, msg string) *Error  { return New(409, code, msg) }
func Forbidden(msg string) *Error       { return New(403, "FORBIDDEN", msg) }
func InvalidArgument(msg string) *Error { return New(400, "INVALID_ARGUMENT", msg) }
func Validation(details string) *Error {
	e := New(400, "VALIDATION_ERROR", "Validierungsfehler")
	e.Details = &details
	return e
}
func NotImplemented() *Error { return New(501, "NOT_IMPLEMENTED", http.StatusText(501)) }
func WriteError(w http.ResponseWriter, c clock.Clock, err error) {
	var e *Error
	if !errors.As(err, &e) {
		d := err.Error()
		e = New(500, "INTERNAL_ERROR", "Ein interner Fehler ist aufgetreten")
		e.Details = &d
	}
	JSON(w, e.Status, struct {
		Code      string  `json:"code"`
		Message   string  `json:"message"`
		Details   *string `json:"details"`
		Timestamp string  `json:"timestamp"`
	}{e.Code, e.Message, e.Details, c.Now().Format("2006-01-02T15:04:05.999999999Z07:00")})
}
func JSON(w http.ResponseWriter, status int, v any) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(status)
	if v != nil {
		_ = json.NewEncoder(w).Encode(v)
	}
}

// EntryPoint models SecurityConfig.sendError and Spring Boot's production error attributes.
func EntryPoint(w http.ResponseWriter, r *http.Request, c clock.Clock) {
	JSON(w, 401, map[string]any{"timestamp": c.Now().UTC().Format("2006-01-02T15:04:05.000Z"),
		"status": 401, "error": "Unauthorized", "path": r.URL.Path})
}
