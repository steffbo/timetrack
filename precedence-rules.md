# Day Type Precedence Rules

## Overview

This document defines the **single source of truth** for determining which type of day takes precedence when multiple conditions apply to the same date. This ensures consistent behavior between backend calculations and frontend display.

## Precedence Hierarchy

The precedence is defined by numeric priority, where **lower numbers have higher priority**.

### Priority Levels

| Priority | Day Type | Description | Overrides |
|----------|----------|-------------|-----------|
| 1 | **WEEKEND** | Not configured as a working day | - |
| 2 | **PUBLIC_HOLIDAY** | Official public holiday for the user's state | Everything |
| 3 | **SICK** | Sick leave | Recurring off-days, vacation, work |
| 4 | **PERSONAL** | Personal time off | Recurring off-days, vacation, work |
| 5 | **RECURRING_OFF_DAY** | Regularly scheduled off-day (e.g., every 2nd Monday) | Vacation, work |
| 6 | **VACATION** | Planned vacation time | Work |
| 7 | **WORK** | Regular work day with time entries | - |
| 8 | **NO_ENTRY** | No data for this day | - |

## Key Rules

### Rule 1: Public Holidays Always Win
Public holidays have the highest priority and override everything, including sick days and personal days.

**Example:** If December 25th is a public holiday and someone also logged sick time, the day displays and counts as a public holiday.

### Rule 2: Sick/Personal Days Override Recurring Off-Days
Medical and personal time-off take precedence over recurring scheduled off-days.

**Example:** If an employee has a recurring off-day every Monday, but is sick on a Monday, the day displays and counts as a sick day (not a recurring off-day).

**Rationale:** When someone is sick on their scheduled off-day, it should still count toward their sick day total.

### Rule 3: Weekends Are Never Working Days
If a day is not configured as a working day (typically weekends), it cannot count toward working day calculations.

**Example:** If someone logs a sick day on Saturday, the calendar displays it in red (sick), but the backend counts it as 0 working days because Saturday is not a working day.

### Rule 4: Recurring Off-Days Override Vacation
Regularly scheduled off-days take precedence over vacation when calculating available vacation days.

**Example:** If someone has a recurring off-day on the 2nd Friday of every month, vacation days planned for that Friday are automatically excluded from vacation balance calculations.

## Implementation Locations

### Backend
- **File:** `backend/src/main/java/cc/remer/timetrack/domain/DayTypePrecedence.java`
- **Usage:** `WorkingDaysCalculator` uses this enum to determine which days count as working days

### Frontend
- **File:** `frontend/src/utils/dayTypePrecedence.ts`
- **Usage:** Calendar views use this utility to determine display colors and day classifications

## Edge Cases

### Case 1: Sick Day on Weekend
- **Display:** Red (sick day color)
- **Calculation:** Counts as 0 working days (weekend takes precedence in calculation)
- **Rationale:** Weekends are never working days per configuration

### Case 2: Sick Day on Public Holiday
- **Display:** Orange (public holiday color)
- **Calculation:** Counts as 0 working days (public holiday)
- **Rationale:** Public holidays have highest priority

### Case 3: Sick Day on Recurring Off-Day
- **Display:** Red (sick day color)
- **Calculation:** Counts as 1 working day (sick day overrides recurring off-day)
- **Rationale:** Sick days take precedence over recurring off-days

### Case 4: Vacation on Recurring Off-Day
- **Display:** Indigo (recurring off-day color)
- **Calculation:** Counts as 0 working days (recurring off-day takes precedence)
- **Rationale:** Don't deduct vacation balance for days that are already scheduled off

## Testing Requirements

When changing precedence logic, ensure:

1. **Backend Tests:** Verify `WorkingDaysCalculator` respects precedence for all combinations
2. **Frontend Tests:** Verify calendar display matches expected colors for all combinations
3. **Integration Tests:** Ensure API responses align with frontend expectations

## Change Log

- **2025-12-15:** Initial precedence rules documented
  - Established SICK/PERSONAL > RECURRING_OFF_DAY precedence
  - Fixed calendar display to match backend calculation logic

---

**Important:** When modifying precedence logic, update BOTH backend and frontend implementations, and update this document.
