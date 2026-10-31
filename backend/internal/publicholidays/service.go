package publicholidays

import (
	"sort"
	"strconv"

	openapi_types "github.com/oapi-codegen/runtime/types"
	"github.com/steffbo/timetrack/backend/internal/api"
	"github.com/steffbo/timetrack/backend/internal/domain"
)

func All() api.PublicHolidaysResponse {
	out := api.PublicHolidaysResponse{
		HolidaysByYearAndState: map[string]map[string][]api.PublicHolidayResponse{}}
	names := map[string]string{"01-01": "Neujahr", "05-01": "Tag der Arbeit",
		"10-03": "Tag der Deutschen Einheit",
		"12-25": "1. Weihnachtstag", "12-26": "2. Weihnachtstag", "03-08": "Internationaler Frauentag",
		"10-31": "Reformationstag"}
	for year := 2023; year <= 2027; year++ {
		states := map[string][]api.PublicHolidayResponse{}
		for _, state := range []domain.GermanState{domain.Berlin, domain.Brandenburg} {
			days := (domain.GermanPublicHolidays{}).GetPublicHolidays(year, state)
			sort.Slice(days, func(i, j int) bool { return days[i].Before(days[j]) })
			holidays := []api.PublicHolidayResponse{}
			for _, d := range days {
				key := d.Format("01-02")
				name := names[key]
				if name == "" {
					name = "Feiertag"
					easter := domain.EasterSunday(year)
					movable := map[int]string{-2: "Karfreitag", 1: "Ostermontag",
						39: "Christi Himmelfahrt", 50: "Pfingstmontag"}
					for offset, title := range movable {
						if d.Equal(easter.AddDate(0, 0, offset)) {
							name = title
							break
						}
					}
				}
				holidays = append(holidays, api.PublicHolidayResponse{Date: openapi_types.Date{Time: d}, Name: name,
					IsStateSpecific: key == "03-08" || key == "10-31"})
			}
			states[string(state)] = holidays
		}
		out.HolidaysByYearAndState[strconv.Itoa(year)] = states
	}
	return out
}
