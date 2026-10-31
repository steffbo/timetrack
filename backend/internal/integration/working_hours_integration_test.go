package integration_test

import (
	"context"
	"testing"

	"github.com/steffbo/timetrack/backend/internal/api"
	"github.com/steffbo/timetrack/backend/internal/domain"
	"github.com/steffbo/timetrack/backend/internal/users"
)

type workingHoursFixture struct {
	packageBFixture
	admin users.User
	user  users.User
}

func newWorkingHoursFixture(t *testing.T) workingHoursFixture {
	t.Helper()
	fixture := newPackageBFixture(t)
	admin := saveTestUser(t, fixture, "admin@test.local", "Admin", "User", domain.Admin)
	user := saveTestUser(t, fixture, "user@test.local", "Test", "User", domain.User)
	createDefaultHours(t, user.ID)
	return workingHoursFixture{packageBFixture: fixture, admin: admin, user: user}
}

func standardUpdateRequest() api.UpdateWorkingHoursRequest {
	return api.UpdateWorkingHoursRequest{WorkingDays: updateDays(
		[]float64{8, 8, 8, 8, 8, 0, 0},
		[]bool{true, true, true, true, true, false, false},
	)}
}

func TestWorkingHoursShouldGetForAuthenticatedUser(t *testing.T) {
	fixture := newWorkingHoursFixture(t)
	response := mustValue(fixture.hours.Get(context.Background(), fixture.user.ID))
	if response.UserId != fixture.user.ID || len(response.WorkingDays) != 7 {
		t.Fatalf("response = %+v", response)
	}
	weekdayCount, weekendCount := 0, 0
	for _, day := range response.WorkingDays {
		if day.Weekday <= 5 {
			weekdayCount++
			if day.Hours != 8 || !day.IsWorkingDay {
				t.Fatalf("weekday = %+v", day)
			}
		} else {
			weekendCount++
			if day.Hours != 0 || day.IsWorkingDay {
				t.Fatalf("weekend = %+v", day)
			}
		}
	}
	if weekdayCount != 5 || weekendCount != 2 {
		t.Fatalf("weekdays=%d weekends=%d", weekdayCount, weekendCount)
	}
}

func TestWorkingHoursShouldThrowWhenUserNotFound(t *testing.T) {
	fixture := newWorkingHoursFixture(t)
	_, err := fixture.hours.Get(context.Background(), 99999)
	requireError(t, err, "USER_NOT_FOUND", "Benutzer nicht gefunden")
}

func TestWorkingHoursAdminShouldGetForSpecificUser(t *testing.T) {
	fixture := newWorkingHoursFixture(t)
	createDefaultHours(t, fixture.admin.ID)
	response := mustValue(fixture.hours.GetForUser(
		context.Background(),
		fixture.admin.Role,
		fixture.user.ID,
	))
	if response.UserId != fixture.user.ID || len(response.WorkingDays) != 7 {
		t.Fatalf("response = %+v", response)
	}
}

func TestWorkingHoursRegularUserShouldNotGetOtherUser(t *testing.T) {
	fixture := newWorkingHoursFixture(t)
	createDefaultHours(t, fixture.admin.ID)
	_, err := fixture.hours.GetForUser(
		context.Background(),
		fixture.user.Role,
		fixture.admin.ID,
	)
	requireError(t, err, "FORBIDDEN", "Keine Berechtigung")
}

func TestWorkingHoursShouldUpdateSuccessfully(t *testing.T) {
	fixture := newWorkingHoursFixture(t)
	request := api.UpdateWorkingHoursRequest{WorkingDays: updateDays(
		[]float64{7, 7, 7, 7, 6, 0, 0},
		[]bool{true, true, true, true, true, false, false},
	)}
	response := mustValue(fixture.hours.Update(context.Background(), fixture.user.ID, request))
	if response.UserId != fixture.user.ID || len(response.WorkingDays) != 7 {
		t.Fatalf("response = %+v", response)
	}
	for weekday := 1; weekday <= 4; weekday++ {
		if workingDay(response, weekday).Hours != 7 {
			t.Fatalf("weekday %d = %+v", weekday, workingDay(response, weekday))
		}
	}
	if workingDay(response, 5).Hours != 6 ||
		workingDay(response, 6).Hours != 0 || workingDay(response, 7).Hours != 0 {
		t.Fatalf("response = %+v", response)
	}
}

func TestWorkingHoursShouldValidateExactlySevenDays(t *testing.T) {
	fixture := newWorkingHoursFixture(t)
	request := standardUpdateRequest()
	request.WorkingDays = request.WorkingDays[:5]
	_, err := fixture.hours.Update(context.Background(), fixture.user.ID, request)
	requireError(t, err, "INVALID_ARGUMENT", "Es müssen genau 7 Wochentage konfiguriert werden")
}

func TestWorkingHoursShouldValidateNoDuplicateWeekdays(t *testing.T) {
	fixture := newWorkingHoursFixture(t)
	request := standardUpdateRequest()
	request.WorkingDays[1].Weekday = 1
	_, err := fixture.hours.Update(context.Background(), fixture.user.ID, request)
	requireError(t, err, "INVALID_ARGUMENT", "Doppelter Wochentag")
}

func TestWorkingHoursShouldValidateHoursWithinRange(t *testing.T) {
	fixture := newWorkingHoursFixture(t)
	request := standardUpdateRequest()
	request.WorkingDays[0].Hours = 25
	_, err := fixture.hours.Update(context.Background(), fixture.user.ID, request)
	requireError(t, err, "INVALID_ARGUMENT", "Stunden müssen zwischen 0 und 24 liegen")
}

func TestWorkingHoursShouldValidateNegativeHours(t *testing.T) {
	fixture := newWorkingHoursFixture(t)
	request := standardUpdateRequest()
	request.WorkingDays[0].Hours = -1
	_, err := fixture.hours.Update(context.Background(), fixture.user.ID, request)
	requireError(t, err, "INVALID_ARGUMENT", "Stunden müssen zwischen 0 und 24 liegen")
}

func TestWorkingHoursShouldValidateInvalidWeekdayValues(t *testing.T) {
	fixture := newWorkingHoursFixture(t)
	request := standardUpdateRequest()
	request.WorkingDays[6].Weekday = 8
	_, err := fixture.hours.Update(context.Background(), fixture.user.ID, request)
	requireError(t, err, "INVALID_ARGUMENT", "Wochentag muss zwischen 1")
}

func TestWorkingHoursShouldAllowPartTimeConfiguration(t *testing.T) {
	fixture := newWorkingHoursFixture(t)
	request := api.UpdateWorkingHoursRequest{WorkingDays: updateDays(
		[]float64{4, 4, 4, 4, 4, 0, 0},
		[]bool{true, true, true, true, true, false, false},
	)}
	response := mustValue(fixture.hours.Update(context.Background(), fixture.user.ID, request))
	for weekday := 1; weekday <= 5; weekday++ {
		if workingDay(response, weekday).Hours != 4 {
			t.Fatalf("weekday %d = %+v", weekday, workingDay(response, weekday))
		}
	}
}

func TestWorkingHoursShouldAllowFlexibleConfiguration(t *testing.T) {
	fixture := newWorkingHoursFixture(t)
	request := api.UpdateWorkingHoursRequest{WorkingDays: updateDays(
		[]float64{8, 6, 8, 6, 4, 0, 0},
		[]bool{true, true, true, true, true, false, false},
	)}
	response := mustValue(fixture.hours.Update(context.Background(), fixture.user.ID, request))
	if len(response.WorkingDays) != 7 || workingDay(response, 1).Hours != 8 ||
		workingDay(response, 2).Hours != 6 || workingDay(response, 3).Hours != 8 ||
		workingDay(response, 4).Hours != 6 || workingDay(response, 5).Hours != 4 {
		t.Fatalf("response = %+v", response)
	}
}

func TestWorkingHoursShouldUpdateSingleDayWithCalculatedNetHours(t *testing.T) {
	fixture := newWorkingHoursFixture(t)
	start, end, pause := "09:00", "17:30", 30
	response := mustValue(fixture.hours.UpdateDay(
		context.Background(),
		fixture.user.ID,
		1,
		api.UpdateWorkingDayConfig{
			Weekday: 1, Hours: 0, IsWorkingDay: true,
			StartTime: &start, EndTime: &end, BreakMinutes: &pause,
		},
	))
	if response.Weekday != 1 || response.Hours != 8 || response.StartTime == nil ||
		*response.StartTime != start || response.EndTime == nil || *response.EndTime != end ||
		response.BreakMinutes == nil || *response.BreakMinutes != 30 {
		t.Fatalf("response = %+v", response)
	}
	var hours float64
	var persistedPause int
	check(t, harness.Pool.QueryRow(
		context.Background(),
		`SELECT hours,break_minutes FROM working_hours WHERE user_id=$1 AND weekday=1`,
		fixture.user.ID,
	).Scan(&hours, &persistedPause))
	if hours != 8 || persistedPause != 30 {
		t.Fatalf("hours=%v pause=%d", hours, persistedPause)
	}
}

func TestWorkingHoursShouldUsePathWeekdayForSingleDay(t *testing.T) {
	fixture := newWorkingHoursFixture(t)
	response := mustValue(fixture.hours.UpdateDay(
		context.Background(),
		fixture.user.ID,
		3,
		api.UpdateWorkingDayConfig{Weekday: 1, Hours: 6, IsWorkingDay: true},
	))
	if response.Weekday != 3 || response.Hours != 6 || response.BreakMinutes == nil ||
		*response.BreakMinutes != 0 {
		t.Fatalf("response = %+v", response)
	}
	current := mustValue(fixture.hours.Get(context.Background(), fixture.user.ID))
	if workingDay(current, 1).Hours != 8 || workingDay(current, 3).Hours != 6 {
		t.Fatalf("current = %+v", current)
	}
}

func TestWorkingHoursShouldCalculateHoursFromTimes(t *testing.T) {
	fixture := newWorkingHoursFixture(t)
	request := standardUpdateRequest()
	mondayStart, mondayEnd := "09:00", "17:00"
	tuesdayStart, tuesdayEnd := "08:00", "16:30"
	request.WorkingDays[0].Hours = 0
	request.WorkingDays[0].StartTime = &mondayStart
	request.WorkingDays[0].EndTime = &mondayEnd
	request.WorkingDays[1].Hours = 0
	request.WorkingDays[1].StartTime = &tuesdayStart
	request.WorkingDays[1].EndTime = &tuesdayEnd
	response := mustValue(fixture.hours.Update(context.Background(), fixture.user.ID, request))
	monday := workingDay(response, 1)
	tuesday := workingDay(response, 2)
	if monday.Hours != 8 || monday.StartTime == nil || *monday.StartTime != mondayStart ||
		monday.EndTime == nil || *monday.EndTime != mondayEnd || tuesday.Hours != 8.5 ||
		tuesday.StartTime == nil || *tuesday.StartTime != tuesdayStart ||
		tuesday.EndTime == nil || *tuesday.EndTime != tuesdayEnd {
		t.Fatalf("monday=%+v tuesday=%+v", monday, tuesday)
	}
}

func TestWorkingHoursShouldValidateBothTimesRequired(t *testing.T) {
	fixture := newWorkingHoursFixture(t)
	request := standardUpdateRequest()
	start := "09:00"
	request.WorkingDays[0].StartTime = &start
	_, err := fixture.hours.Update(context.Background(), fixture.user.ID, request)
	requireError(
		t,
		err,
		"INVALID_ARGUMENT",
		"Start- und Endzeit müssen beide angegeben werden oder beide leer sein",
	)
}

func TestWorkingHoursShouldValidateEndTimeAfterStartTime(t *testing.T) {
	fixture := newWorkingHoursFixture(t)
	request := standardUpdateRequest()
	start, end := "17:00", "09:00"
	request.WorkingDays[0].StartTime = &start
	request.WorkingDays[0].EndTime = &end
	_, err := fixture.hours.Update(context.Background(), fixture.user.ID, request)
	requireError(t, err, "INVALID_ARGUMENT", "Endzeit muss nach Startzeit liegen")
}

func TestWorkingHoursShouldAllowMixedConfiguration(t *testing.T) {
	fixture := newWorkingHoursFixture(t)
	request := standardUpdateRequest()
	start, end := "09:00", "17:00"
	request.WorkingDays[0].Hours = 0
	request.WorkingDays[0].StartTime = &start
	request.WorkingDays[0].EndTime = &end
	for index := 1; index <= 4; index++ {
		request.WorkingDays[index].Hours = 7.5
	}
	response := mustValue(fixture.hours.Update(context.Background(), fixture.user.ID, request))
	monday := workingDay(response, 1)
	tuesday := workingDay(response, 2)
	if monday.Hours != 8 || monday.StartTime == nil || monday.EndTime == nil ||
		tuesday.Hours != 7.5 || tuesday.StartTime != nil || tuesday.EndTime != nil {
		t.Fatalf("monday=%+v tuesday=%+v", monday, tuesday)
	}
}

func TestWorkingHoursShouldSaveAndRetrieveBreakMinutes(t *testing.T) {
	fixture := newWorkingHoursFixture(t)
	request := standardUpdateRequest()
	breaks := []int{30, 45, 0, 0, 0, 0, 0}
	for index := range request.WorkingDays {
		request.WorkingDays[index].BreakMinutes = &breaks[index]
	}
	response := mustValue(fixture.hours.Update(context.Background(), fixture.user.ID, request))
	if *workingDay(response, 1).BreakMinutes != 30 ||
		*workingDay(response, 2).BreakMinutes != 45 ||
		*workingDay(response, 3).BreakMinutes != 0 {
		t.Fatalf("response = %+v", response)
	}
}

func TestWorkingHoursShouldDefaultBreakMinutesToZero(t *testing.T) {
	fixture := newWorkingHoursFixture(t)
	response := mustValue(fixture.hours.Update(
		context.Background(),
		fixture.user.ID,
		standardUpdateRequest(),
	))
	for _, day := range response.WorkingDays {
		if day.BreakMinutes == nil || *day.BreakMinutes != 0 {
			t.Fatalf("day = %+v", day)
		}
	}
}

func TestWorkingHoursShouldValidateNegativeBreakMinutes(t *testing.T) {
	fixture := newWorkingHoursFixture(t)
	request := standardUpdateRequest()
	pause := -30
	request.WorkingDays[0].BreakMinutes = &pause
	_, err := fixture.hours.Update(context.Background(), fixture.user.ID, request)
	requireError(t, err, "INVALID_ARGUMENT", "Pausenzeit darf nicht negativ sein")
}
