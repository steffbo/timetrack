package integration_test

import (
	"context"
	"errors"
	"fmt"
	"strings"
	"testing"
	"time"

	"github.com/steffbo/timetrack/backend-go/internal/api"
	"github.com/steffbo/timetrack/backend-go/internal/auth"
	"github.com/steffbo/timetrack/backend-go/internal/clock"
	"github.com/steffbo/timetrack/backend-go/internal/domain"
	"github.com/steffbo/timetrack/backend-go/internal/httpx"
	"github.com/steffbo/timetrack/backend-go/internal/server"
	"github.com/steffbo/timetrack/backend-go/internal/users"
	"github.com/steffbo/timetrack/backend-go/internal/vacationbalance"
	"github.com/steffbo/timetrack/backend-go/internal/workinghours"
	"golang.org/x/crypto/bcrypt"
)

var packageBClock = clock.Fixed{
	Time: time.Date(2025, time.August, 1, 10, 0, 0, 0, time.UTC),
}

type packageBFixture struct {
	app       *server.Server
	users     users.Service
	userRepo  users.Repository
	hoursRepo workinghours.Repository
	hours     workinghours.Service
	balance   vacationbalance.Repository
}

func newPackageBFixture(t *testing.T) packageBFixture {
	t.Helper()
	harness.Reset(t)
	app, _ := harness.Server(t, packageBClock)
	hoursRepo := workinghours.Repository{Q: harness.Pool, Clock: packageBClock}
	return packageBFixture{
		app:       app,
		users:     users.Service{Pool: harness.Pool, Clock: packageBClock, Auth: app.Auth},
		userRepo:  users.Repository{Q: harness.Pool, Clock: packageBClock},
		hoursRepo: hoursRepo,
		hours:     workinghours.Service{Repository: hoursRepo},
		balance:   vacationbalance.Repository{Pool: harness.Pool, Clock: packageBClock},
	}
}

func mustValue[T any](value T, err error) T {
	if err != nil {
		panic(err)
	}
	return value
}

func requireError(t *testing.T, err error, code, messagePart string) {
	t.Helper()
	if err == nil {
		t.Fatal("expected error")
	}
	var public *httpx.Error
	if code != "" && (!errors.As(err, &public) || public.Code != code) {
		t.Fatalf("error code = %v, want %s", err, code)
	}
	if !strings.Contains(err.Error(), messagePart) {
		t.Fatalf("error %q does not contain %q", err, messagePart)
	}
}

func testPasswordHash(t *testing.T, password string) *string {
	t.Helper()
	hash, err := bcrypt.GenerateFromPassword([]byte(password), 10)
	if err != nil {
		t.Fatal(err)
	}
	value := string(hash)
	return &value
}

func saveTestUser(
	t *testing.T,
	fixture packageBFixture,
	email, firstName, lastName string,
	role domain.Role,
) users.User {
	t.Helper()
	return mustValue(fixture.userRepo.Save(context.Background(), users.User{
		Email:        email,
		PasswordHash: testPasswordHash(t, "password"),
		FirstName:    firstName,
		LastName:     lastName,
		Role:         role,
		Active:       true,
		State:        domain.Berlin,
	}))
}

func principalFor(user users.User) auth.Principal {
	return auth.Principal{
		ID:     user.ID,
		Email:  user.Email,
		Role:   user.Role,
		Active: user.Active,
	}
}

func createDefaultHours(t *testing.T, userID int64) {
	t.Helper()
	for weekday := 1; weekday <= 7; weekday++ {
		hours := 8.0
		working := true
		if weekday > 5 {
			hours = 0
			working = false
		}
		_, err := harness.Pool.Exec(
			context.Background(),
			`INSERT INTO working_hours(user_id,weekday,hours,is_working_day)
VALUES($1,$2,$3,$4)`,
			userID,
			weekday,
			hours,
			working,
		)
		if err != nil {
			t.Fatal(err)
		}
	}
}

func updateDays(hours []float64, working []bool) []api.UpdateWorkingDayConfig {
	days := make([]api.UpdateWorkingDayConfig, 7)
	for index := range days {
		days[index] = api.UpdateWorkingDayConfig{
			Weekday:      index + 1,
			Hours:        hours[index],
			IsWorkingDay: working[index],
		}
	}
	return days
}

func workingDay(response api.WorkingHoursResponse, weekday int) api.WorkingDayConfig {
	for _, day := range response.WorkingDays {
		if day.Weekday == weekday {
			return day
		}
	}
	panic(fmt.Sprintf("weekday %d missing", weekday))
}

func insertBalance(
	t *testing.T,
	userID int64,
	year int,
	annual, carried, adjustment, used float64,
) {
	t.Helper()
	_, err := harness.Pool.Exec(
		context.Background(),
		`INSERT INTO vacation_balance(
user_id,year,annual_allowance_days,carried_over_days,adjustment_days,used_days,remaining_days
) VALUES($1,$2,$3,$4,$5,$6,$7)`,
		userID,
		year,
		annual,
		carried,
		adjustment,
		used,
		annual+carried+adjustment-used,
	)
	if err != nil {
		t.Fatal(err)
	}
}

type insertedTimeOff struct {
	ID   int64
	Days float64
}

func insertTimeOff(
	t *testing.T,
	fixture packageBFixture,
	userID int64,
	start, end string,
	typeName domain.TimeOffType,
) insertedTimeOff {
	t.Helper()
	var id int64
	err := harness.Pool.QueryRow(
		context.Background(),
		`INSERT INTO time_off(user_id,start_date,end_date,time_off_type)
VALUES($1,$2,$3,$4) RETURNING id`,
		userID,
		start,
		end,
		typeName,
	).Scan(&id)
	if err != nil {
		t.Fatal(err)
	}
	days := mustValue(fixture.balance.WorkingDays(
		context.Background(),
		userID,
		mustDate(start),
		mustDate(end),
	),
	)
	return insertedTimeOff{ID: id, Days: days}
}

func deleteTimeOff(t *testing.T, id int64) {
	t.Helper()
	_, err := harness.Pool.Exec(context.Background(), `DELETE FROM time_off WHERE id=$1`, id)
	if err != nil {
		t.Fatal(err)
	}
}

func insertRecurringOffDay(
	t *testing.T,
	userID int64,
	weekday, interval int,
	referenceDate, startDate string,
) {
	t.Helper()
	_, err := harness.Pool.Exec(
		context.Background(),
		`INSERT INTO recurring_off_days(
user_id,recurrence_pattern,weekday,week_interval,reference_date,start_date,is_active
) VALUES($1,'EVERY_NTH_WEEK',$2,$3,$4,$5,true)`,
		userID,
		weekday,
		interval,
		referenceDate,
		startDate,
	)
	if err != nil {
		t.Fatal(err)
	}
}

func setHalfDayHolidays(t *testing.T, userID int64, enabled bool) {
	t.Helper()
	_, err := harness.Pool.Exec(
		context.Background(),
		`UPDATE users SET half_day_holidays_enabled=$1 WHERE id=$2`,
		enabled,
		userID,
	)
	if err != nil {
		t.Fatal(err)
	}
}

func mustDate(value string) time.Time {
	date, err := time.Parse(time.DateOnly, value)
	if err != nil {
		panic(err)
	}
	return date
}
