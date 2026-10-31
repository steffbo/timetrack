package integration_test

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"net/http/httptest"
	"net/url"
	"os"
	"sort"
	"strings"
	"testing"
	"time"

	"github.com/getkin/kin-openapi/openapi3"
	"github.com/steffbo/timetrack/backend-go/internal/api"
	"github.com/steffbo/timetrack/backend-go/internal/auth"
	"github.com/steffbo/timetrack/backend-go/internal/clock"
	"github.com/steffbo/timetrack/backend-go/internal/domain"
	"github.com/steffbo/timetrack/backend-go/internal/httpx"
	"github.com/steffbo/timetrack/backend-go/internal/migrate"
	"github.com/steffbo/timetrack/backend-go/internal/testutil"
)

var harness *testutil.Harness
var fixed = clock.Fixed{Time: time.Date(2030, 1, 15, 12, 0, 0, 0, time.UTC)}

func TestMain(m *testing.M) {
	ctx := context.Background()
	var err error
	harness, err = testutil.Start(ctx)
	if err != nil {
		fmt.Fprintln(os.Stderr, err)
		os.Exit(1)
	}
	code := m.Run()
	harness.Close(ctx)
	os.Exit(code)
}
func check(t *testing.T, err error) {
	t.Helper()
	if err != nil {
		t.Fatal(err)
	}
}
func execSQL(t *testing.T, sql string, args ...any) {
	t.Helper()
	_, err := harness.Pool.Exec(context.Background(), sql, args...)
	check(t, err)
}
func errorCode(t *testing.T, status int, b []byte, want int, code, msg string) {
	t.Helper()
	if status != want {
		t.Fatalf("status %d != %d: %s", status, want, b)
	}
	var e struct {
		Code, Message string
		Details       *string
		Timestamp     string
	}
	check(t, json.Unmarshal(b, &e))
	if e.Code != code || e.Message != msg || e.Timestamp == "" {
		t.Fatalf("error: %s", b)
	}
}
func Test00DefaultAdminAndMigrationIdempotency(t *testing.T) {
	_, srv := harness.Server(t, fixed)
	session := testutil.Login(t, srv.URL, "admin@timetrack.local", "admin1")
	if session.User.Role != api.ADMIN {
		t.Fatal(session)
	}
	var before, after int
	check(t, harness.Pool.QueryRow(context.Background(), `SELECT count(*) FROM flyway_schema_history`).Scan(&before))
	check(t, migrate.Run(context.Background(), harness.Pool))
	check(t, harness.Pool.QueryRow(context.Background(), `SELECT count(*) FROM flyway_schema_history`).Scan(&after))
	migrations, err := migrate.Migrations()
	check(t, err)
	if before != len(migrations) || after != before {
		t.Fatal(before, after)
	}
}
func TestLoginSecurityLogout(t *testing.T) {
	harness.Reset(t)
	pw := "password"
	id := harness.User(t, "user@test.local", domain.User, &pw)
	app, srv := harness.Server(t, fixed)
	session := testutil.Login(t, srv.URL, "user@test.local", pw)
	claims, err := app.Auth.Tokens.Parse(session.AccessToken)
	check(t, err)
	if claims.Authorities != "ROLE_USER,FACTOR_PASSWORD" {
		t.Fatal(claims)
	}
	if session.User.Id != id || session.ExpiresIn != 86400 || session.TokenType != "Bearer" {
		t.Fatal(session)
	}
	status, b := testutil.Request(t, srv.URL, "POST", "/api/auth/login", "", map[string]string{"email": "user@test.local", "password": "wrong"})
	errorCode(t, status, b, 401, "INVALID_CREDENTIALS", "Ungültige Anmeldedaten")
	status, b = testutil.Request(t, srv.URL, "POST", "/api/auth/login", "", map[string]string{"email": "missing@test.local", "password": pw})
	errorCode(t, status, b, 401, "INVALID_CREDENTIALS", "Ungültige Anmeldedaten")
	status, b = testutil.Request(t, srv.URL, "POST", "/api/auth/login", "", map[string]string{"email": "invalid"})
	errorCode(t, status, b, 400, "VALIDATION_ERROR", "Validierungsfehler")
	for _, token := range []string{"", "broken", session.AccessToken + "broken"} {
		status, b = testutil.Request(t, srv.URL, "GET", "/api/users/me", token, nil)
		if status != 401 || !strings.Contains(string(b), `"error":"Unauthorized"`) || strings.Contains(string(b), `"code"`) {
			t.Fatalf("%d %s", status, b)
		}
	}
	status, b = testutil.Request(t, srv.URL, "GET", "/api/users", session.AccessToken, nil)
	errorCode(t, status, b, 500, "INTERNAL_ERROR", "Ein interner Fehler ist aufgetreten")
	status, b = testutil.Request(t, srv.URL, "GET", "/api/users/me", session.RefreshToken, nil)
	if status != 200 {
		t.Fatalf("refresh JWT should authenticate current user: %d %s", status, b)
	} // Java accepts refresh JWTs as bearer JWTs.
	status, b = testutil.Request(t, srv.URL, "POST", "/api/auth/logout", session.AccessToken, nil)
	if status != 204 {
		t.Fatalf("%d %s", status, b)
	}
	var count int
	check(t, harness.Pool.QueryRow(context.Background(), `SELECT count(*) FROM refresh_tokens`).Scan(&count))
	if count != 0 {
		t.Fatal(count)
	}
	status, b = testutil.Request(t, srv.URL, "POST", "/api/auth/refresh", "", map[string]string{"refreshToken": session.RefreshToken})
	errorCode(t, status, b, 400, "INVALID_ARGUMENT", "Refresh-Token nicht gefunden")
	status, b = testutil.Request(t, srv.URL, "GET", "/dashboard", "", nil)
	if status != 200 || !strings.Contains(string(b), "TimeTrack") {
		t.Fatalf("%d %s", status, b)
	}
	req := httptest.NewRequest("GET", "/actuator/health", nil)
	rec := httptest.NewRecorder()
	app.ManagementRouter().ServeHTTP(rec, req)
	if rec.Code != 200 || !strings.Contains(rec.Body.String(), `"status":"UP"`) {
		t.Fatal(rec.Body)
	}
}
func TestRefreshCases(t *testing.T) {
	for _, tc := range []struct {
		name, msg string
		mutate    string
	}{
		{"valid", "", ""}, {"expired persisted", "Refresh-Token ist abgelaufen", `UPDATE refresh_tokens SET expires_at='2025-07-31'`},
		{"inactive", "Benutzerkonto ist inaktiv", `UPDATE users SET active=false`},
		{"missing", "Refresh-Token nicht gefunden", `DELETE FROM refresh_tokens`}} {
		t.Run(tc.name, func(t *testing.T) {
			harness.Reset(t)
			pw := "password"
			harness.User(t, "user@test.local", domain.User, &pw)
			_, srv := harness.Server(t, fixed)
			session := testutil.Login(t, srv.URL, "user@test.local", pw)
			if tc.mutate != "" {
				execSQL(t, tc.mutate)
			}
			status, b := testutil.Request(t, srv.URL, "POST", "/api/auth/refresh", "", map[string]string{"refreshToken": session.RefreshToken})
			if tc.msg != "" {
				errorCode(t, status, b, 400, "INVALID_ARGUMENT", tc.msg)
				return
			}
			if status != 200 {
				t.Fatalf("%d %s", status, b)
			}
			var refreshed api.AuthResponse
			check(t, json.Unmarshal(b, &refreshed))
			provider := auth.Tokens{Secret: []byte("your-secret-key-change-this-in-production-min-256-bits"), Clock: fixed}
			claims, err := provider.Parse(refreshed.AccessToken)
			check(t, err)
			if claims.Authorities != "ROLE_USER" {
				t.Fatal(claims)
			}
			if refreshed.RefreshToken != session.RefreshToken || refreshed.User.Id != session.User.Id {
				t.Fatal(refreshed)
			}
		})
	}
	_, srv := harness.Server(t, fixed)
	status, b := testutil.Request(t, srv.URL, "POST", "/api/auth/refresh", "", map[string]string{"refreshToken": "bad"})
	errorCode(t, status, b, 400, "INVALID_ARGUMENT", "Ungültiges Refresh-Token")
}
func TestInviteInfoAndRegistration(t *testing.T) {
	for _, reset := range []bool{false, true} {
		t.Run(fmt.Sprint(reset), func(t *testing.T) {
			harness.Reset(t)
			var password *string
			if reset {
				v := "old-password"
				password = &v
			}
			id := harness.User(t, "invite@test.local", domain.User, password)
			app, srv := harness.Server(t, fixed)
			invite, err := app.Auth.GenerateInvite(context.Background(), id)
			check(t, err)
			token := strings.TrimPrefix(invite.InviteUrl, "http://localhost:5173/register/")
			if !invite.ExpiresAt.Equal(fixed.Now().AddDate(0, 0, 30)) {
				t.Fatal(invite)
			}
			status, b := testutil.Request(t, srv.URL, "GET", "/api/auth/invite/"+token, "", nil)
			if status != 200 {
				t.Fatalf("%d %s", status, b)
			}
			var info api.InviteInfoResponse
			check(t, json.Unmarshal(b, &info))
			if info.IsPasswordReset != reset {
				t.Fatal(info)
			}
			body := map[string]string{"token": token, "password": "newpassword123", "firstName": "New", "lastName": "Name"}
			status, b = testutil.Request(t, srv.URL, "POST", "/api/auth/register", "", body)
			if status != 200 {
				t.Fatalf("%d %s", status, b)
			}
			var session api.AuthResponse
			check(t, json.Unmarshal(b, &session))
			testutil.Request(t, srv.URL, "POST", "/api/auth/logout", session.AccessToken, nil)
			testutil.Login(t, srv.URL, "invite@test.local", "newpassword123")
			var first, last string
			check(t, harness.Pool.QueryRow(context.Background(), `SELECT first_name,last_name FROM users WHERE id=$1`, id).Scan(&first, &last))
			if first != "New" || last != "Name" {
				t.Fatal(first, last)
			}
			status, b = testutil.Request(t, srv.URL, "POST", "/api/auth/register", "", body)
			errorCode(t, status, b, 404, "INVITE_TOKEN_NOT_FOUND", "Einladungslink nicht gefunden")
		})
	}
}
func TestInviteFailuresAndRegeneration(t *testing.T) {
	harness.Reset(t)
	id := harness.User(t, "pending@test.local", domain.User, nil)
	app, srv := harness.Server(t, fixed)
	first, err := app.Auth.GenerateInvite(context.Background(), id)
	check(t, err)
	second, err := app.Auth.GenerateInvite(context.Background(), id)
	check(t, err)
	if first.InviteUrl == second.InviteUrl {
		t.Fatal("token was reused")
	}
	firstToken := strings.Split(first.InviteUrl, "/register/")[1]
	token := strings.Split(second.InviteUrl, "/register/")[1]
	for _, tok := range []string{firstToken, "missing"} {
		status, b := testutil.Request(t, srv.URL, "GET", "/api/auth/invite/"+tok, "", nil)
		errorCode(t, status, b, 404, "INVITE_TOKEN_NOT_FOUND", "Einladungslink nicht gefunden")
	}
	execSQL(t, `UPDATE invite_tokens SET expires_at='2025-07-31'`)
	status, b := testutil.Request(t, srv.URL, "GET", "/api/auth/invite/"+token, "", nil)
	errorCode(t, status, b, 410, "INVITE_TOKEN_EXPIRED", "Einladungslink ist abgelaufen")
	for _, tc := range []struct {
		token     string
		status    int
		code, msg string
	}{
		{token, 410, "INVITE_TOKEN_EXPIRED", "Einladungslink ist abgelaufen"},
		{"ghost", 404, "INVITE_TOKEN_NOT_FOUND", "Einladungslink nicht gefunden"}} {
		status, b = testutil.Request(t, srv.URL, "POST", "/api/auth/register", "", map[string]string{
			"token": tc.token, "password": "newpass", "firstName": "A", "lastName": "B"})
		errorCode(t, status, b, tc.status, tc.code, tc.msg)
	}
	_, err = app.Auth.GenerateInvite(context.Background(), 999)
	if e, ok := err.(*httpx.Error); !ok || e.Code != "USER_NOT_FOUND" {
		t.Fatal(err)
	}
}
func TestImpersonationService(t *testing.T) {
	harness.Reset(t)
	pw := "password"
	adminID := harness.User(t, "admin@test.local", domain.Admin, &pw)
	otherAdminID := harness.User(t, "admin2@test.local", domain.Admin, &pw)
	userID := harness.User(t, "user@test.local", domain.User, &pw)
	app, _ := harness.Server(t, fixed)
	ctx := auth.WithPrincipal(context.Background(), auth.Principal{ID: adminID, Role: domain.Admin})
	session, err := app.Auth.Impersonate(ctx, userID)
	check(t, err)
	claims, err := app.Auth.Tokens.Parse(session.AccessToken)
	check(t, err)
	if session.User.Id != userID || claims.ImpersonatedBy == nil || *claims.ImpersonatedBy != adminID {
		t.Fatal(session, claims)
	}
	_, err = app.Auth.Impersonate(ctx, otherAdminID)
	if e, ok := err.(*httpx.Error); !ok || e.Message != "Administratoren können nicht andere Administratoren verkörpern" {
		t.Fatal(err)
	}
	ctx = auth.WithPrincipal(context.Background(), auth.Principal{ID: userID, Role: domain.User})
	_, err = app.Auth.Impersonate(ctx, adminID)
	if e, ok := err.(*httpx.Error); !ok || e.Message != "Nur Administratoren können andere Benutzer verkörpern" {
		t.Fatal(err)
	}
}
func TestMigrationValidationAndLock(t *testing.T) {
	ctx := context.Background()
	execSQL(t, `UPDATE flyway_schema_history SET checksum=checksum+1 WHERE version='1'`)
	if err := migrate.Run(ctx, harness.Pool); err == nil {
		t.Fatal("checksum mismatch accepted")
	}
	execSQL(t, `UPDATE flyway_schema_history SET checksum=checksum-1 WHERE version='1'`)
	execSQL(t, `UPDATE flyway_schema_history SET success=false WHERE version='1'`)
	if err := migrate.Run(ctx, harness.Pool); err == nil {
		t.Fatal("failed migration accepted")
	}
	execSQL(t, `UPDATE flyway_schema_history SET success=true WHERE version='1'`)
	done := make(chan error, 2)
	for range 2 {
		go func() { done <- migrate.Run(ctx, harness.Pool) }()
	}
	check(t, <-done)
	check(t, <-done)
}

func sample(schema *openapi3.Schema) any {
	if schema.Example != nil {
		return schema.Example
	}
	switch {
	case schema.Type.Is("object"):
		out := map[string]any{}
		for _, name := range schema.Required {
			out[name] = sample(schema.Properties[name].Value)
		}
		return out
	case schema.Type.Is("array"):
		out := []any{}
		for range schema.MinItems {
			out = append(out, sample(schema.Items.Value))
		}
		return out
	case schema.Type.Is("integer"), schema.Type.Is("number"):
		if schema.Min != nil {
			return *schema.Min
		}
		return 1
	case schema.Type.Is("boolean"):
		return true
	default:
		if len(schema.Enum) > 0 {
			return schema.Enum[0]
		}
		switch schema.Format {
		case "date":
			return "2030-01-15"
		case "date-time":
			return "2030-01-15T12:00:00Z"
		case "email":
			return "sample@test.local"
		}
		if schema.Pattern != "" {
			return "09:00"
		}
		return "sample"
	}
}
func TestPackageBOperationsAreImplemented(t *testing.T) {
	harness.Reset(t)
	pw := "password"
	harness.User(t, "admin@test.local", domain.Admin, &pw)
	_, srv := harness.Server(t, fixed)
	session := testutil.Login(t, srv.URL, "admin@test.local", pw)
	spec, err := api.GetSwagger()
	check(t, err)
	count := 0
	bOperations := map[string]bool{"GetAllUsers": true, "CreateUser": true, "GetCurrentUser": true,
		"DeleteUser": true, "GetUserById": true, "UpdateUser": true, "ImpersonateUser": true,
		"GenerateInviteToken": true, "GetWorkingHours": true, "UpdateWorkingHours": true,
		"UpdateWorkingDay": true, "GetWorkingHoursByUserId": true, "GetVacationBalance": true,
		"UpdateVacationBalance": true}
	for path, item := range spec.Paths.Map() {
		if strings.HasPrefix(path, "/api/auth/") {
			continue
		}
		for method, operation := range item.Operations() {
			if !bOperations[operation.OperationID] {
				continue
			}
			count++
			t.Run(operation.OperationID, func(t *testing.T) {
				uri := path
				query := url.Values{}
				for _, ref := range operation.Parameters {
					p := ref.Value
					if p.In == "path" {
						uri = strings.ReplaceAll(uri, "{"+p.Name+"}", "1")
					}
					if p.In == "query" && p.Required {
						query.Set(p.Name, fmt.Sprint(sample(p.Schema.Value)))
					}
				}
				if len(query) > 0 {
					uri += "?" + query.Encode()
				}
				var body any
				if operation.RequestBody != nil {
					body = sample(operation.RequestBody.Value.Content.Get("application/json").Schema.Value)
				}
				status, b := testutil.Request(t, srv.URL, method, uri, session.AccessToken, body)
				if status == 501 {
					t.Fatalf("%s %s remains unimplemented: %s", method, uri, b)
				}
			})
		}
	}
	if count != len(bOperations) {
		t.Fatal("operation count", count)
	}
}
func TestAuthJSONNullsAndCors(t *testing.T) {
	harness.Reset(t)
	pw := "password"
	harness.User(t, "user@test.local", domain.User, &pw)
	app, srv := harness.Server(t, fixed)
	status, b := testutil.Request(t, srv.URL, "POST", "/api/auth/login", "", map[string]string{
		"email": "user@test.local", "password": pw})
	if status != 200 {
		t.Fatalf("%d %s", status, b)
	}
	var response map[string]any
	check(t, json.Unmarshal(b, &response))
	user := response["user"].(map[string]any)
	for _, field := range []string{"firstName", "lastName", "state", "createdAt", "updatedAt", "pending", "inviteUrl"} {
		value, present := user[field]
		if !present || value != nil {
			t.Fatal(field, user)
		}
	}
	if user["halfDayHolidaysEnabled"] != false {
		t.Fatal(user)
	}
	req := httptest.NewRequest("OPTIONS", "/api/users", nil)
	req.Header.Set("Origin", "https://example.test")
	req.Header.Set("Access-Control-Request-Method", "POST")
	req.Header.Set("Access-Control-Request-Headers", "authorization,content-type")
	rec := httptest.NewRecorder()
	app.Router().ServeHTTP(rec, req)
	if rec.Code != 200 || rec.Header().Get("Access-Control-Allow-Origin") != "https://example.test" ||
		rec.Header().Get("Access-Control-Allow-Credentials") != "true" || rec.Header().Get("Access-Control-Max-Age") != "3600" {
		t.Fatal(rec)
	}
}

func TestFlywayBaselineAndSchemaRows(t *testing.T) {
	ctx := context.Background()
	rows, err := harness.Pool.Query(ctx, `SELECT installed_rank,version,description,type,script,checksum,
 installed_by,installed_on,execution_time,success FROM flyway_schema_history WHERE version::int<=6`)
	check(t, err)
	type row struct {
		rank                              int
		version, description, typ, script string
		checksum                          int32
		by                                string
		on                                time.Time
		ms                                int
		ok                                bool
	}
	var saved []row
	for rows.Next() {
		var r row
		check(t, rows.Scan(&r.rank, &r.version, &r.description, &r.typ, &r.script,
			&r.checksum, &r.by, &r.on, &r.ms, &r.ok))
		saved = append(saved, r)
	}
	check(t, rows.Err())
	rows.Close()
	defer func() {
		execSQL(t, `DELETE FROM flyway_schema_history WHERE type IN ('BASELINE','SCHEMA')`)
		for _, r := range saved {
			execSQL(t, `INSERT INTO flyway_schema_history VALUES($1,$2,$3,$4,$5,$6,$7,$8,$9,$10)`,
				r.rank, r.version, r.description, r.typ, r.script, r.checksum, r.by, r.on, r.ms, r.ok)
		}
	}()
	execSQL(t, `DELETE FROM flyway_schema_history WHERE version::int<=6`)
	execSQL(t, `INSERT INTO flyway_schema_history
 (installed_rank,version,description,type,script,installed_by,execution_time,success)
 VALUES (0,NULL,'<< Flyway Schema Creation >>','SCHEMA','public',current_user,0,true),
 (1,'6','<< Flyway Baseline >>','BASELINE','<< Flyway Baseline >>',current_user,0,true)`)
	check(t, migrate.Run(ctx, harness.Pool))
}

func TestJavaHTTPGoldenErrors(t *testing.T) {
	harness.Reset(t)
	_, srv := harness.Server(t, fixed)
	raw, err := os.ReadFile("testdata/java-http-reference.json")
	check(t, err)
	var fixtures map[string]struct {
		Status int
		Body   map[string]any
	}
	check(t, json.Unmarshal(raw, &fixtures))
	cases := []struct {
		name, path string
		body       any
	}{
		{"anonymous", "/api/users/me", nil},
		{"logout anonymous", "/api/auth/logout", map[string]any{}},
		{"wrong password", "/api/auth/login", map[string]string{"email": "missing@test.local", "password": "wrong"}},
		{"missing fields", "/api/auth/login", map[string]string{}},
		{"invalid email", "/api/auth/login", map[string]string{"email": "bad", "password": "x"}},
		{"missing invite", "/api/auth/invite/missing", nil},
		{"invalid refresh", "/api/auth/refresh", map[string]string{"refreshToken": "bad"}},
		{"register invalid", "/api/auth/register", map[string]string{
			"token": "missing", "password": "x", "firstName": "", "lastName": ""}},
	}
	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			method := "GET"
			if tc.body != nil {
				method = "POST"
			}
			status, b := testutil.Request(t, srv.URL, method, tc.path, "", tc.body)
			want := fixtures[tc.name]
			if status != want.Status {
				t.Fatalf("%d %s", status, b)
			}
			var got map[string]any
			check(t, json.Unmarshal(b, &got))
			delete(got, "timestamp")
			// Hibernate violation iteration order is not deterministic; compare its comma-separated detail set.
			if tc.name == "register invalid" {
				a := strings.Split(got["details"].(string), ", ")
				b := strings.Split(want.Body["details"].(string), ", ")
				sort.Strings(a)
				sort.Strings(b)
				got["details"] = a
				want.Body["details"] = b
			}
			actual, _ := json.Marshal(got)
			expected, _ := json.Marshal(want.Body)
			if !bytes.Equal(actual, expected) {
				t.Fatalf("got %s; Java %s", actual, expected)
			}
		})
	}
}

func TestPendingAndInactiveLogin(t *testing.T) {
	harness.Reset(t)
	pw := "password"
	harness.User(t, "pending@test.local", domain.User, nil)
	harness.User(t, "inactive@test.local", domain.User, &pw)
	execSQL(t, `UPDATE users SET active=false WHERE email='inactive@test.local'`)
	_, srv := harness.Server(t, fixed)
	status, b := testutil.Request(t, srv.URL, "POST", "/api/auth/login", "", map[string]string{
		"email": "pending@test.local", "password": pw})
	errorCode(t, status, b, 401, "INVALID_CREDENTIALS", "Ungültige Anmeldedaten")
	status, b = testutil.Request(t, srv.URL, "POST", "/api/auth/login", "", map[string]string{
		"email": "inactive@test.local", "password": pw})
	errorCode(t, status, b, 500, "INTERNAL_ERROR", "Ein interner Fehler ist aufgetreten")
	var body map[string]any
	check(t, json.Unmarshal(b, &body))
	if body["details"] != "User is disabled" {
		t.Fatal(body)
	}
}
