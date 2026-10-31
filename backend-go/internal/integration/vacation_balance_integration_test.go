package integration_test

import (
	"context"
	"strings"
	"testing"

	"github.com/steffbo/timetrack/backend-go/internal/api"
	"github.com/steffbo/timetrack/backend-go/internal/auth"
	"github.com/steffbo/timetrack/backend-go/internal/domain"
	"github.com/steffbo/timetrack/backend-go/internal/users"
)

type vacationBalanceFixture struct {
	packageBFixture
	user users.User
}

func newVacationBalanceFixture(t *testing.T) vacationBalanceFixture {
	t.Helper()
	fixture := newPackageBFixture(t)
	user := saveTestUser(t, fixture, "user@test.local", "Test", "User", domain.User)
	createDefaultHours(t, user.ID)
	return vacationBalanceFixture{packageBFixture: fixture, user: user}
}

func (f vacationBalanceFixture) get(t *testing.T, year int) api.VacationBalanceResponse {
	t.Helper()
	return mustValue(f.balance.Balance(context.Background(), f.user.ID, year))
}

func (f vacationBalanceFixture) update(
	t *testing.T,
	principal auth.Principal,
	request api.UpdateVacationBalanceRequest,
) api.VacationBalanceResponse {
	t.Helper()
	return mustValue(f.balance.Update(context.Background(), principal, request))
}

func floatPointer(value float64) *float64 { return &value }

func TestVacationBalanceShouldGetForCurrentYear(t *testing.T) {
	fixture := newVacationBalanceFixture(t)
	insertBalance(t, fixture.user.ID, 2025, 30, 5, 2, 0)
	insertTimeOff(t, fixture.packageBFixture, fixture.user.ID,
		"2025-03-10", "2025-03-14", domain.Vacation)
	insertTimeOff(t, fixture.packageBFixture, fixture.user.ID,
		"2025-07-07", "2025-07-11", domain.Vacation)
	response := fixture.get(t, packageBClock.Now().Year())
	if response.Id == 0 || response.UserId != fixture.user.ID || response.Year != 2025 ||
		response.AnnualAllowanceDays != 30 || response.CarriedOverDays != 5 ||
		response.AdjustmentDays != 2 || response.UsedDays != 10 ||
		response.RemainingDays != 27 {
		t.Fatalf("response = %+v", response)
	}
}

func TestVacationBalanceShouldGetForSpecificYear(t *testing.T) {
	fixture := newVacationBalanceFixture(t)
	insertBalance(t, fixture.user.ID, 2025, 30, 0, 0, 0)
	response := fixture.get(t, 2025)
	if response.Year != 2025 || response.AnnualAllowanceDays != 30 {
		t.Fatalf("response = %+v", response)
	}
}

func TestVacationBalanceShouldAutoCreateIfNotExists(t *testing.T) {
	fixture := newVacationBalanceFixture(t)
	response := fixture.get(t, 2024)
	if response.Id == 0 || response.Year != 2024 || response.AnnualAllowanceDays != 30 ||
		response.UsedDays != 0 || response.RemainingDays != 30 {
		t.Fatalf("response = %+v", response)
	}
}

func TestVacationBalanceShouldCreateNewIfNotExists(t *testing.T) {
	fixture := newVacationBalanceFixture(t)
	response := fixture.update(t, principalFor(fixture.user), api.UpdateVacationBalanceRequest{
		UserId: fixture.user.ID, Year: 2025,
		AnnualAllowanceDays: floatPointer(28),
		CarriedOverDays:     floatPointer(3),
		AdjustmentDays:      floatPointer(1),
	})
	if response.Id == 0 || response.Year != 2025 || response.AnnualAllowanceDays != 28 ||
		response.CarriedOverDays != 3 || response.AdjustmentDays != 1 ||
		response.RemainingDays != 32 {
		t.Fatalf("response = %+v", response)
	}
}

func TestVacationBalanceShouldUpdateExisting(t *testing.T) {
	fixture := newVacationBalanceFixture(t)
	insertBalance(t, fixture.user.ID, 2025, 30, 0, 0, 5)
	response := fixture.update(t, principalFor(fixture.user), api.UpdateVacationBalanceRequest{
		UserId:          fixture.user.ID,
		Year:            2025,
		CarriedOverDays: floatPointer(5),
		AdjustmentDays:  floatPointer(2),
	})
	if response.CarriedOverDays != 5 || response.AdjustmentDays != 2 ||
		response.RemainingDays != 37 {
		t.Fatalf("response = %+v", response)
	}
}

func TestVacationBalanceShouldCalculateRemainingDaysCorrectly(t *testing.T) {
	fixture := newVacationBalanceFixture(t)
	response := fixture.update(t, principalFor(fixture.user), api.UpdateVacationBalanceRequest{
		UserId: fixture.user.ID, Year: 2025,
		AnnualAllowanceDays: floatPointer(30),
		CarriedOverDays:     floatPointer(5),
		AdjustmentDays:      floatPointer(-1),
	})
	if response.RemainingDays != 34 {
		t.Fatalf("response = %+v", response)
	}
}

func TestVacationBalanceShouldHandlePartialUpdates(t *testing.T) {
	fixture := newVacationBalanceFixture(t)
	insertBalance(t, fixture.user.ID, 2025, 30, 5, 0, 10)
	response := fixture.update(t, principalFor(fixture.user), api.UpdateVacationBalanceRequest{
		UserId: fixture.user.ID, Year: 2025, AdjustmentDays: floatPointer(3),
	})
	if response.AnnualAllowanceDays != 30 || response.CarriedOverDays != 5 ||
		response.AdjustmentDays != 3 || response.UsedDays != 10 ||
		response.RemainingDays != 38 {
		t.Fatalf("response = %+v", response)
	}
}

func TestVacationBalanceShouldUseDefaultThirtyDaysForNewBalance(t *testing.T) {
	fixture := newVacationBalanceFixture(t)
	response := fixture.update(t, principalFor(fixture.user), api.UpdateVacationBalanceRequest{
		UserId: fixture.user.ID, Year: 2025,
	})
	if response.AnnualAllowanceDays != 30 {
		t.Fatalf("response = %+v", response)
	}
}

func TestVacationBalanceShouldHandleNegativeAdjustments(t *testing.T) {
	fixture := newVacationBalanceFixture(t)
	insertBalance(t, fixture.user.ID, 2025, 30, 0, 0, 0)
	response := fixture.update(t, principalFor(fixture.user), api.UpdateVacationBalanceRequest{
		UserId: fixture.user.ID, Year: 2025, AdjustmentDays: floatPointer(-5),
	})
	if response.AdjustmentDays != -5 || response.RemainingDays != 25 {
		t.Fatalf("response = %+v", response)
	}
}

func TestVacationBalanceShouldAutoUpdateWhenCreatingVacation(t *testing.T) {
	fixture := newVacationBalanceFixture(t)
	insertBalance(t, fixture.user.ID, 2025, 30, 0, 0, 0)
	insertTimeOff(t, fixture.packageBFixture, fixture.user.ID,
		"2025-01-06", "2025-01-10", domain.Vacation)
	balance := fixture.get(t, 2025)
	if balance.UsedDays != 5 || balance.RemainingDays != 25 {
		t.Fatalf("balance = %+v", balance)
	}
}

func TestVacationBalanceShouldAutoUpdateWhenDeletingVacation(t *testing.T) {
	fixture := newVacationBalanceFixture(t)
	insertBalance(t, fixture.user.ID, 2025, 30, 0, 0, 0)
	timeOff := insertTimeOff(t, fixture.packageBFixture, fixture.user.ID,
		"2025-01-06", "2025-01-10", domain.Vacation)
	afterCreate := fixture.get(t, 2025)
	if afterCreate.UsedDays != 5 {
		t.Fatalf("after create = %+v", afterCreate)
	}
	deleteTimeOff(t, timeOff.ID)
	afterDelete := fixture.get(t, 2025)
	if afterDelete.UsedDays != 0 || afterDelete.RemainingDays != 30 {
		t.Fatalf("after delete = %+v", afterDelete)
	}
}

func TestVacationBalanceShouldHandleMultipleVacationsInSameYear(t *testing.T) {
	fixture := newVacationBalanceFixture(t)
	insertBalance(t, fixture.user.ID, 2025, 30, 0, 0, 0)
	insertTimeOff(t, fixture.packageBFixture, fixture.user.ID,
		"2025-03-10", "2025-03-12", domain.Vacation)
	insertTimeOff(t, fixture.packageBFixture, fixture.user.ID,
		"2025-07-01", "2025-07-04", domain.Vacation)
	balance := fixture.get(t, 2025)
	if balance.UsedDays != 7 || balance.RemainingDays != 23 {
		t.Fatalf("balance = %+v", balance)
	}
}

func TestVacationBalanceShouldIgnoreNonVacationTypes(t *testing.T) {
	fixture := newVacationBalanceFixture(t)
	insertBalance(t, fixture.user.ID, 2025, 30, 0, 0, 0)
	insertTimeOff(t, fixture.packageBFixture, fixture.user.ID,
		"2025-02-10", "2025-02-14", domain.Sick)
	balance := fixture.get(t, 2025)
	if balance.UsedDays != 0 || balance.RemainingDays != 30 {
		t.Fatalf("balance = %+v", balance)
	}
}

func TestVacationBalanceShouldCreateWhenVacationExists(t *testing.T) {
	fixture := newVacationBalanceFixture(t)
	insertTimeOff(t, fixture.packageBFixture, fixture.user.ID,
		"2025-06-02", "2025-06-06", domain.Vacation)
	balance := fixture.get(t, 2025)
	if balance.Id == 0 || balance.AnnualAllowanceDays != 30 ||
		balance.UsedDays != 5 || balance.RemainingDays != 25 {
		t.Fatalf("balance = %+v", balance)
	}
}

func TestVacationBalanceShouldExcludeWeekends(t *testing.T) {
	fixture := newVacationBalanceFixture(t)
	insertBalance(t, fixture.user.ID, 2025, 30, 0, 0, 0)
	insertTimeOff(t, fixture.packageBFixture, fixture.user.ID,
		"2025-01-06", "2025-01-17", domain.Vacation)
	balance := fixture.get(t, 2025)
	if balance.UsedDays != 10 || balance.RemainingDays != 20 {
		t.Fatalf("balance = %+v", balance)
	}
}

func TestVacationBalanceShouldExcludePublicHolidays(t *testing.T) {
	fixture := newVacationBalanceFixture(t)
	insertBalance(t, fixture.user.ID, 2025, 30, 0, 0, 0)
	insertTimeOff(t, fixture.packageBFixture, fixture.user.ID,
		"2025-01-01", "2025-01-03", domain.Vacation)
	balance := fixture.get(t, 2025)
	if balance.UsedDays != 2 || balance.RemainingDays != 28 {
		t.Fatalf("balance = %+v", balance)
	}
}

func TestVacationBalanceShouldExcludeRecurringOffDays(t *testing.T) {
	fixture := newVacationBalanceFixture(t)
	insertBalance(t, fixture.user.ID, 2025, 30, 0, 0, 0)
	insertRecurringOffDay(t, fixture.user.ID, 1, 2, "2025-01-06", "2025-01-01")
	insertTimeOff(t, fixture.packageBFixture, fixture.user.ID,
		"2025-01-06", "2025-01-09", domain.Vacation)
	balance := fixture.get(t, 2025)
	if balance.UsedDays != 3 || balance.RemainingDays != 27 {
		t.Fatalf("balance = %+v", balance)
	}
}

func TestVacationBalanceShouldExcludeSickDays(t *testing.T) {
	fixture := newVacationBalanceFixture(t)
	insertBalance(t, fixture.user.ID, 2025, 30, 0, 0, 0)
	insertTimeOff(t, fixture.packageBFixture, fixture.user.ID,
		"2025-01-06", "2025-01-17", domain.Vacation)
	insertTimeOff(t, fixture.packageBFixture, fixture.user.ID,
		"2025-01-09", "2025-01-10", domain.Sick)
	balance := fixture.get(t, 2025)
	if balance.UsedDays != 8 || balance.RemainingDays != 22 {
		t.Fatalf("balance = %+v", balance)
	}
}

func TestVacationBalanceShouldExcludePersonalDays(t *testing.T) {
	fixture := newVacationBalanceFixture(t)
	insertBalance(t, fixture.user.ID, 2025, 30, 0, 0, 0)
	insertTimeOff(t, fixture.packageBFixture, fixture.user.ID,
		"2025-02-03", "2025-02-21", domain.Vacation)
	insertTimeOff(t, fixture.packageBFixture, fixture.user.ID,
		"2025-02-10", "2025-02-12", domain.Personal)
	balance := fixture.get(t, 2025)
	if balance.UsedDays != 12 || balance.RemainingDays != 18 {
		t.Fatalf("balance = %+v", balance)
	}
}

func TestVacationBalanceShouldHandleComplexExclusions(t *testing.T) {
	fixture := newVacationBalanceFixture(t)
	insertBalance(t, fixture.user.ID, 2025, 30, 0, 0, 0)
	insertRecurringOffDay(t, fixture.user.ID, 3, 1, "2025-02-05", "2025-02-01")
	insertTimeOff(t, fixture.packageBFixture, fixture.user.ID,
		"2025-02-03", "2025-02-21", domain.Vacation)
	insertTimeOff(t, fixture.packageBFixture, fixture.user.ID,
		"2025-02-10", "2025-02-11", domain.Sick)
	balance := fixture.get(t, 2025)
	if balance.UsedDays != 10 || balance.RemainingDays != 20 {
		t.Fatalf("balance = %+v", balance)
	}
}

func TestVacationBalanceShouldCalculateChristmasWeek2025(t *testing.T) {
	fixture := newVacationBalanceFixture(t)
	insertBalance(t, fixture.user.ID, 2025, 30, 0, 0, 0)
	insertRecurringOffDay(t, fixture.user.ID, 1, 5, "2025-12-22", "2025-12-01")
	insertTimeOff(t, fixture.packageBFixture, fixture.user.ID,
		"2025-12-21", "2025-12-31", domain.Vacation)
	balance := fixture.get(t, 2025)
	if balance.PlannedDays != 5 || balance.UsedDays != 0 || balance.RemainingDays != 25 {
		t.Fatalf("balance = %+v", balance)
	}
}

func TestVacationBalanceShouldCountDecember24AsHalfDay(t *testing.T) {
	fixture := newVacationBalanceFixture(t)
	setHalfDayHolidays(t, fixture.user.ID, true)
	insertBalance(t, fixture.user.ID, 2025, 30, 0, 0, 0)
	timeOff := insertTimeOff(t, fixture.packageBFixture, fixture.user.ID,
		"2025-12-23", "2025-12-27", domain.Vacation)
	if timeOff.Days != 1.5 {
		t.Fatalf("time-off days = %v", timeOff.Days)
	}
	balance := fixture.get(t, 2025)
	if balance.PlannedDays != 1.5 || balance.RemainingDays != 28.5 {
		t.Fatalf("balance = %+v", balance)
	}
}

func TestVacationBalanceShouldCountDecember31AsHalfDay(t *testing.T) {
	fixture := newVacationBalanceFixture(t)
	setHalfDayHolidays(t, fixture.user.ID, true)
	insertBalance(t, fixture.user.ID, 2025, 30, 0, 0, 0)
	timeOff := insertTimeOff(t, fixture.packageBFixture, fixture.user.ID,
		"2025-12-29", "2025-12-31", domain.Vacation)
	if timeOff.Days != 2.5 {
		t.Fatalf("time-off days = %v", timeOff.Days)
	}
	balance := fixture.get(t, 2025)
	if balance.PlannedDays != 2.5 || balance.RemainingDays != 27.5 {
		t.Fatalf("balance = %+v", balance)
	}
}

func TestVacationBalanceShouldCountBothHalfDayHolidays(t *testing.T) {
	fixture := newVacationBalanceFixture(t)
	setHalfDayHolidays(t, fixture.user.ID, true)
	insertBalance(t, fixture.user.ID, 2025, 30, 0, 0, 0)
	timeOff := insertTimeOff(t, fixture.packageBFixture, fixture.user.ID,
		"2025-12-22", "2025-12-31", domain.Vacation)
	if timeOff.Days != 5 {
		t.Fatalf("time-off days = %v", timeOff.Days)
	}
	balance := fixture.get(t, 2025)
	if balance.PlannedDays != 5 || balance.RemainingDays != 25 {
		t.Fatalf("balance = %+v", balance)
	}
}

func TestVacationBalanceShouldCountFullDaysWhenHalfDaysDisabled(t *testing.T) {
	fixture := newVacationBalanceFixture(t)
	insertBalance(t, fixture.user.ID, 2025, 30, 0, 0, 0)
	timeOff := insertTimeOff(t, fixture.packageBFixture, fixture.user.ID,
		"2025-12-23", "2025-12-27", domain.Vacation)
	if timeOff.Days != 2 {
		t.Fatalf("time-off days = %v", timeOff.Days)
	}
	balance := fixture.get(t, 2025)
	if balance.PlannedDays != 2 || balance.RemainingDays != 28 {
		t.Fatalf("balance = %+v", balance)
	}
}

func TestVacationBalanceShouldCountSingleDecember24AsHalfDay(t *testing.T) {
	fixture := newVacationBalanceFixture(t)
	setHalfDayHolidays(t, fixture.user.ID, true)
	insertBalance(t, fixture.user.ID, 2025, 30, 0, 0, 0)
	timeOff := insertTimeOff(t, fixture.packageBFixture, fixture.user.ID,
		"2025-12-24", "2025-12-24", domain.Vacation)
	if timeOff.Days != 0.5 {
		t.Fatalf("time-off days = %v", timeOff.Days)
	}
	balance := fixture.get(t, 2025)
	if balance.PlannedDays != 0.5 || balance.RemainingDays != 29.5 {
		t.Fatalf("balance = %+v", balance)
	}
}

func TestVacationBalanceShouldRecalculateDeletingHalfDayVacation(t *testing.T) {
	fixture := newVacationBalanceFixture(t)
	setHalfDayHolidays(t, fixture.user.ID, true)
	insertBalance(t, fixture.user.ID, 2025, 30, 0, 0, 0)
	timeOff := insertTimeOff(t, fixture.packageBFixture, fixture.user.ID,
		"2025-12-24", "2025-12-24", domain.Vacation)
	afterCreate := fixture.get(t, 2025)
	if afterCreate.PlannedDays != 0.5 || afterCreate.RemainingDays != 29.5 {
		t.Fatalf("after create = %+v", afterCreate)
	}
	deleteTimeOff(t, timeOff.ID)
	afterDelete := fixture.get(t, 2025)
	if afterDelete.PlannedDays != 0 || afterDelete.RemainingDays != 30 {
		t.Fatalf("after delete = %+v", afterDelete)
	}
}

func TestVacationBalanceShouldHandleMixedHalfAndFullDayVacations(t *testing.T) {
	fixture := newVacationBalanceFixture(t)
	setHalfDayHolidays(t, fixture.user.ID, true)
	insertBalance(t, fixture.user.ID, 2025, 30, 0, 0, 0)
	insertTimeOff(t, fixture.packageBFixture, fixture.user.ID,
		"2025-12-24", "2025-12-24", domain.Vacation)
	insertTimeOff(t, fixture.packageBFixture, fixture.user.ID,
		"2025-10-20", "2025-10-24", domain.Vacation)
	insertTimeOff(t, fixture.packageBFixture, fixture.user.ID,
		"2025-12-31", "2025-12-31", domain.Vacation)
	balance := fixture.get(t, 2025)
	if balance.PlannedDays != 6 || balance.RemainingDays != 24 {
		t.Fatalf("balance = %+v", balance)
	}
}

func TestVacationBalanceShouldCalculateDecember21To30WithHalfDays(t *testing.T) {
	fixture := newVacationBalanceFixture(t)
	setHalfDayHolidays(t, fixture.user.ID, true)
	insertBalance(t, fixture.user.ID, 2025, 30, 0, 0, 0)
	timeOff := insertTimeOff(t, fixture.packageBFixture, fixture.user.ID,
		"2025-12-21", "2025-12-30", domain.Vacation)
	if timeOff.Days != 4.5 {
		t.Fatalf("time-off days = %v", timeOff.Days)
	}
	balance := fixture.get(t, 2025)
	if balance.PlannedDays != 4.5 || balance.RemainingDays != 25.5 {
		t.Fatalf("balance = %+v", balance)
	}
}

func TestVacationBalanceShouldAllowUserToUpdateOwnBalance(t *testing.T) {
	fixture := newVacationBalanceFixture(t)
	response := fixture.update(t, principalFor(fixture.user), api.UpdateVacationBalanceRequest{
		UserId: fixture.user.ID, Year: 2025, AnnualAllowanceDays: floatPointer(28),
	})
	if response.AnnualAllowanceDays != 28 {
		t.Fatalf("response = %+v", response)
	}
}

func TestVacationBalanceShouldPreventUserUpdatingOtherBalance(t *testing.T) {
	fixture := newVacationBalanceFixture(t)
	other := saveTestUser(t, fixture.packageBFixture,
		"other@timetrack.local", "Other", "User", domain.User)
	_, err := fixture.balance.Update(
		context.Background(),
		principalFor(fixture.user),
		api.UpdateVacationBalanceRequest{
			UserId: other.ID, Year: 2025, AnnualAllowanceDays: floatPointer(28),
		},
	)
	if err == nil || !strings.Contains(err.Error(), "You can only update your own vacation balance") {
		t.Fatalf("err = %v", err)
	}
}

func TestVacationBalanceShouldAllowAdminUpdatingAnyBalance(t *testing.T) {
	fixture := newVacationBalanceFixture(t)
	admin := saveTestUser(t, fixture.packageBFixture,
		"admin2@timetrack.local", "Admin", "User", domain.Admin)
	response := fixture.update(t, principalFor(admin), api.UpdateVacationBalanceRequest{
		UserId: fixture.user.ID, Year: 2025, AnnualAllowanceDays: floatPointer(35),
	})
	if response.UserId != fixture.user.ID || response.AnnualAllowanceDays != 35 {
		t.Fatalf("response = %+v", response)
	}
}
