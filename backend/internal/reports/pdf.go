package reports

import (
	"bytes"
	"fmt"
	"math"
	"strings"

	"github.com/go-pdf/fpdf"
)

func hours(v float64, sign bool) string {
	h := int(math.Floor(math.Abs(v)))
	m := int(math.Floor((math.Abs(v)-float64(h))*60 + 0.5))
	if m == 60 {
		h++
		m = 0
	}
	prefix := ""
	if v < 0 {
		prefix = "-"
	} else if sign {
		prefix = "+"
	}
	if sign && h == 0 {
		return fmt.Sprintf("%s%d min", prefix, m)
	}
	return fmt.Sprintf("%s%dh %dm", prefix, h, m)
}
func optionalHours(v *float64) string {
	if v == nil {
		return "-"
	}
	return hours(*v, false)
}

// GeneratePDF uses A4 points and the exact PDFBox coordinates, fonts, widths and
// row heights. Core Helvetica uses WinAnsi, matching PDFBox's Standard14 font.
func GeneratePDF(year, month int, user User, entries []DailyReportEntry, compress bool) ([]byte, error) {
	pdf := fpdf.New("P", "pt", "A4", "")
	pdf.SetCompression(compress)
	pdf.SetAutoPageBreak(false, 0)
	pdf.SetMargins(50, 50, 50)
	pdf.AddPage()
	encode := pdf.UnicodeTranslatorFromDescriptor("cp1252")
	text := func(x, y, size float64, bold bool, value string) {
		style := ""
		if bold {
			style = "B"
		}
		pdf.SetFont("Helvetica", style, size)
		pdf.SetTextColor(0, 0, 0)
		pdf.Text(x, y, encode(value))
	}
	text(200, 50, 18, true, fmt.Sprintf("Stundenzettel %s %d", months[month], year))
	text(50, 80, 11, false, "Mitarbeiter: "+name(user.FirstName)+" "+name(user.LastName))
	pageWidth, _ := pdf.GetPageSize()
	tableWidth := pageWidth - 100
	widths := []float64{.24, .10, .10, .10, .10, .10, .26}
	y := 120.0
	rect := func(height float64, color []int) {
		pdf.SetDrawColor(200, 200, 200)
		if color != nil {
			pdf.SetFillColor(color[0], color[1], color[2])
			pdf.Rect(50, y, tableWidth, height, "FD")
		} else {
			pdf.Rect(50, y, tableWidth, height, "D")
		}
	}
	cells := func(values []string, size, baseline float64, bold bool) {
		x := 55.0
		for i, v := range values {
			text(x, y+baseline, size, bold, v)
			x += widths[i] * tableWidth
		}
	}
	rect(20, []int{220, 220, 220})
	cells([]string{"Datum", "Anfang", "Pause", "Ende", "Gesamt", "Überstd.", "Notiz"}, 10, 14, true)
	y += 20
	weekdays := []string{"So.", "Mo.", "Di.", "Mi.", "Do.", "Fr.", "Sa."}
	colors := map[string][]int{"WEEKEND": {235, 235, 235}, "SICK": {255, 230, 230},
		"VACATION": {230, 255, 230}, "PUBLIC_HOLIDAY": {242, 242, 255}, "RECURRING_OFF_DAY": {230, 255, 230}}
	labels := map[string]string{"SICK": "Krank", "CHILD_SICK": "Kind krank", "VACATION": "Urlaub",
		"PUBLIC_HOLIDAY": "Feiertag", "EDUCATION": "Fortbildung"}
	for _, e := range entries {
		note := ""
		if e.Notes != nil {
			note = *e.Notes
		}
		if strings.TrimSpace(note) == "" && e.TimeOffType != "" {
			note = labels[e.TimeOffType]
			if note == "" {
				note = e.TimeOffType
			}
		}
		if strings.TrimSpace(note) == "" && e.DayType == "RECURRING_OFF_DAY" {
			note = "Freier Tag"
		}
		start, end, pause := "-", "-", "-"
		if e.StartTime != nil {
			start = e.StartTime.Format("15:04")
		}
		if e.EndTime != nil {
			end = e.EndTime.Format("15:04")
		}
		if e.BreakMinutes != 0 {
			pause = fmt.Sprintf("%d min", e.BreakMinutes)
		}
		rect(18, colors[e.DayType])
		cells([]string{weekdays[e.Date.Weekday()] + ", " + e.Date.Format("02.01.2006"), start, pause, end,
			optionalHours(e.TotalHours), optionalHours(e.Overtime), note}, 9, 12, false)
		y += 18
	}
	actual, expected, ot := totals(entries, true)
	y += 25
	text(50, y, 11, true, "Zusammenfassung:")
	y += 18
	text(50, y, 10, false, "Gesamtstunden: "+hours(actual, false))
	y += 15
	text(50, y, 10, false, "Sollstunden: "+hours(expected, false))
	y += 15
	text(50, y, 10, false, "Gesamtüberstunden: "+hours(ot, true))
	var b bytes.Buffer
	if err := pdf.Output(&b); err != nil {
		return nil, fmt.Errorf("Failed to generate PDF report: %w", err)
	}
	return b.Bytes(), nil
}
