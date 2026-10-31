package reports

import (
	"fmt"
	"math/big"
	"strconv"
	"strings"
)

var months = []string{"", "Januar", "Februar", "März", "April", "Mai", "Juni", "Juli",
	"August", "September", "Oktober", "November", "Dezember"}

func name(v *string) string {
	if v == nil {
		return "null"
	}
	return *v
}

// Decimal formatting rounds half up; fmt's binary
// rounding would turn e.g. 2.675 into 2.67 and 0.125 into 0.12.
func decimal(v float64) string {
	negative := v < 0 || (v == 0 && strings.HasPrefix(strconv.FormatFloat(v, 'f', -1, 64), "-"))
	if negative {
		v = -v
	}
	rational, ok := new(big.Rat).SetString(strconv.FormatFloat(v, 'f', -1, 64))
	if !ok {
		return strings.ReplaceAll(fmt.Sprintf("%.2f", v), ".", ",")
	}
	rational.Mul(rational, big.NewRat(100, 1))
	rational.Add(rational, big.NewRat(1, 2))
	rounded := new(big.Int).Quo(rational.Num(), rational.Denom())
	digits := rounded.String()
	for len(digits) < 3 {
		digits = "0" + digits
	}
	result := digits[:len(digits)-2] + "," + digits[len(digits)-2:]
	if negative {
		result = "-" + result
	}
	return result
}
func signedDecimal(v float64) string {
	if v >= 0 {
		return "+" + decimal(v)
	}
	return decimal(v)
}
func optionalDecimal(v *float64) string {
	if v == nil {
		return ""
	}
	return decimal(*v)
}
func totals(entries []DailyReportEntry, regularOnly bool) (actual, expected, overtime float64) {
	for _, e := range entries {
		if e.TotalHours != nil {
			actual += *e.TotalHours
		}
		if e.ExpectedHours != nil && (!regularOnly || e.DayType == "REGULAR") {
			expected += *e.ExpectedHours
		}
		if e.Overtime != nil {
			overtime += *e.Overtime
		}
	}
	return
}
func GenerateCSV(year, month int, user User, entries []DailyReportEntry) []byte {
	var b strings.Builder
	fmt.Fprintf(&b, "\uFEFFStundenzettel %s %d\nMitarbeiter;%s %s\n\n", months[month], year,
		name(user.FirstName), name(user.LastName))
	b.WriteString("Datum;Anfang;Pause (min);Ende;Gesamt;Überstunden\n")
	for _, e := range entries {
		start, end := "", ""
		if e.StartTime != nil {
			start = e.StartTime.Format("15:04")
		}
		if e.EndTime != nil {
			end = e.EndTime.Format("15:04")
		}
		fmt.Fprintf(&b, "%s;%s;%d;%s;%s;%s\n", e.Date.Format("02.01.2006"), start, e.BreakMinutes, end,
			optionalDecimal(e.TotalHours), optionalDecimal(e.Overtime))
	}
	actual, expected, ot := totals(entries, false)
	fmt.Fprintf(&b, "\nZusammenfassung\nGesamtstunden;%s\nSollstunden;%s\nGesamtüberstunden;%s\n",
		decimal(actual), decimal(expected), signedDecimal(ot))
	return []byte(b.String())
}
