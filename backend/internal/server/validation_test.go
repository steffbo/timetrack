package server

import (
	"net/http/httptest"
	"strings"
	"testing"

	"github.com/getkin/kin-openapi/openapi3"
)

func TestSpecValidationConstraints(t *testing.T) {
	maximum := uint64(100)
	maxNumber := float64(24)
	minNumber := float64(0)
	tests := []struct {
		name   string
		value  any
		schema *openapi3.Schema
		want   string
	}{
		{"size", strings.Repeat("😀", 51), &openapi3.Schema{MaxLength: &maximum},
			"field: size must be between 0 and 100"},
		{"min", float64(-1), &openapi3.Schema{Min: &minNumber}, "field: must be greater than or equal to 0"},
		{"max", float64(25), &openapi3.Schema{Max: &maxNumber}, "field: must be less than or equal to 24"},
		{"pattern", "25:00", &openapi3.Schema{Pattern: `^([01]\d|2[0-3]):[0-5]\d$`},
			`field: must match "^([01]\d|2[0-3]):[0-5]\d$"`},
	}
	for _, tc := range tests {
		t.Run(tc.name, func(t *testing.T) {
			var details []string
			checkValue("field", tc.value, tc.schema, &details)
			if len(details) != 1 || details[0] != tc.want {
				t.Fatal(details)
			}
		})
	}
}
func TestGermanValidationMessages(t *testing.T) {
	req := httptest.NewRequest("POST", "/api/auth/register", nil)
	req.Header.Set("Accept-Language", "de-DE,de;q=0.9")
	for _, tc := range []struct{ input, want string }{
		{"field: must not be null", "field: darf nicht null sein"},
		{"field: size must be between 1 and 100", "field: Größe muss zwischen 1 und 100 sein"},
		{`field: must match "[a-z]"`, `field: muss mit "[a-z]" übereinstimmen`},
		{"field: must be greater than or equal to 0", "field: muss größer-gleich 0 sein"},
	} {
		if got := localizeValidation(req, tc.input); got != tc.want {
			t.Fatalf("%q != %q", got, tc.want)
		}
	}
}
