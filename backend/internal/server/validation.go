package server

import (
	"bytes"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"regexp"
	"sort"
	"strconv"
	"strings"
	"time"
	"unicode/utf16"

	"github.com/getkin/kin-openapi/openapi3"
	"github.com/getkin/kin-openapi/routers/legacy"
	"github.com/steffbo/timetrack/backend-go/internal/api"
	"github.com/steffbo/timetrack/backend-go/internal/httpx"
)

// validate applies the constraints used by generated Java bean validation.
func (s *Server) validate(next http.Handler) http.Handler {
	spec, err := api.GetSwagger()
	if err != nil {
		panic(err)
	}
	spec.Servers = nil // Host-independent routing: paths already contain /api.
	router, err := legacy.NewRouter(spec)
	if err != nil {
		panic(err)
	}
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if !strings.HasPrefix(r.URL.Path, "/api/") {
			next.ServeHTTP(w, r)
			return
		}
		route, params, err := router.FindRoute(r)
		if err != nil {
			next.ServeHTTP(w, r)
			return
		}
		var details []string
		for _, parameter := range route.Operation.Parameters {
			p := parameter.Value
			value := r.URL.Query().Get(p.Name)
			if p.In == "path" {
				value = params[p.Name]
			}
			if value == "" {
				if p.Required {
					details = append(details, p.Name+": must not be null")
				}
				continue
			}
			var v any = value
			if p.Schema != nil && p.Schema.Value.Type.Is("integer") {
				number, e := strconv.ParseFloat(value, 64)
				if e == nil {
					v = number
				}
			}
			if p.Schema != nil {
				checkValue(p.Name, v, p.Schema.Value, &details)
			}
		}
		if route.Operation.RequestBody != nil {
			body, e := io.ReadAll(r.Body)
			if e != nil {
				httpx.WriteError(w, s.Clock, e)
				return
			}
			r.Body = io.NopCloser(bytes.NewReader(body))
			var v any
			if e = json.Unmarshal(body, &v); e != nil {
				httpx.WriteError(w, s.Clock, e)
				return
			}
			if media := route.Operation.RequestBody.Value.Content.Get("application/json"); media != nil {
				checkValue("", v, media.Schema.Value, &details)
			}
		}
		if len(details) > 0 {
			httpx.WriteError(w, s.Clock, httpx.Validation(localizeValidation(r, strings.Join(details, ", "))))
			return
		}
		next.ServeHTTP(w, r)
	})
}
func checkValue(field string, v any, schema *openapi3.Schema, details *[]string) {
	if schema == nil || v == nil {
		return
	}
	add := func(msg string) { *details = append(*details, field+": "+msg) }
	switch x := v.(type) {
	case map[string]any:
		keys := make([]string, 0, len(schema.Properties))
		for k := range schema.Properties {
			keys = append(keys, k)
		}
		sort.Strings(keys)
		for _, key := range keys {
			child := key
			if field != "" {
				child = field + "." + key
			}
			value, exists := x[key]
			required := false
			for _, k := range schema.Required {
				if k == key {
					required = true
				}
			}
			if required && (!exists || value == nil) {
				*details = append(*details, child+": must not be null")
				continue
			}
			if exists {
				checkValue(child, value, schema.Properties[key].Value, details)
			}
		}
	case []any:
		if uint64(len(x)) < schema.MinItems || (schema.MaxItems != nil && uint64(len(x)) > *schema.MaxItems) {
			max := uint64(2147483647)
			if schema.MaxItems != nil {
				max = *schema.MaxItems
			}
			add(fmt.Sprintf("size must be between %d and %d", schema.MinItems, max))
		}
		if schema.Items != nil {
			for i, item := range x {
				checkValue(fmt.Sprintf("%s[%d]", field, i), item, schema.Items.Value, details)
			}
		}
	case string:
		length := uint64(len(utf16.Encode([]rune(x))))
		if length < schema.MinLength || (schema.MaxLength != nil && length > *schema.MaxLength) {
			max := uint64(2147483647)
			if schema.MaxLength != nil {
				max = *schema.MaxLength
			}
			add(fmt.Sprintf("size must be between %d and %d", schema.MinLength, max))
		}
		if schema.Pattern != "" {
			if ok, _ := regexp.MatchString(schema.Pattern, x); !ok {
				add("must match \"" + schema.Pattern + "\"")
			}
		}
		if schema.Format == "email" && x != "" {
			if ok, _ := regexp.MatchString(`^[^@\s]+@[^@\s]+$`, x); !ok {
				add("must be a well-formed email address")
			}
		}
		if schema.Format == "date" {
			if _, err := time.Parse(time.DateOnly, x); err != nil {
				add(err.Error())
			}
		}
	case float64:
		if schema.Min != nil && x < *schema.Min {
			add(fmt.Sprintf("must be greater than or equal to %v", *schema.Min))
		}
		if schema.Max != nil && x > *schema.Max {
			add(fmt.Sprintf("must be less than or equal to %v", *schema.Max))
		}
	}
}

// Hibernate's English and German default messages; the frontend supports both locales.
func localizeValidation(r *http.Request, details string) string {
	if !httpx.IsGerman(httpx.WithLocale(r.Context(), r.Header.Get("Accept-Language"))) {
		return details
	}
	pairs := []string{
		"must not be null", "darf nicht null sein",
		"must be a well-formed email address", "muss eine korrekt formatierte E-Mail-Adresse sein",
		"must be greater than or equal to ", "muss größer-gleich ",
		"must be less than or equal to ", "muss kleiner-gleich ",
	}
	details = strings.NewReplacer(pairs...).Replace(details)
	details = regexp.MustCompile(`size must be between (\d+) and (\d+)`).ReplaceAllString(details,
		"Größe muss zwischen $1 und $2 sein")
	// The numeric range messages end with 'sein' in Hibernate's German bundle.
	details = regexp.MustCompile(`(muss (?:größer|kleiner)-gleich [^,]+)`).ReplaceAllString(details, "$1 sein")
	details = regexp.MustCompile(`must match ("[^"\n]*")`).ReplaceAllString(details, "muss mit $1 übereinstimmen")
	return details
}
