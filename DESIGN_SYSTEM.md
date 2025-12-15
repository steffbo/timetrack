# Time Tracking Application - Design System

**Single Source of Truth for Visual Design Decisions**

This document defines the visual design patterns, colors, emojis, and precedence rules used throughout the application (frontend and backend). All implementations should reference this document to ensure consistency.

---

## 📊 Entry Type Precedence

The application uses a strict precedence order when displaying multiple entry types for the same day. The highest precedence type determines the visual representation (background color, primary display).

### Precedence Order (Highest to Lowest)

1. **Work** (`work`) - Actual time tracking entries
2. **Public Holiday** (`public-holiday`) - Official public holidays
3. **Time Off** (`timeoff`) - User absence entries
   - Sick (`SICK`)
   - Personal (`PERSONAL`)
   - Vacation (`VACATION`)
4. **Recurring Off-Day** (`recurring-off`) - Scheduled recurring days off
5. **Weekend** (`weekend`) - Non-working days based on working hours config

### Implementation Notes

- When multiple types exist for a single day, **all types are shown** (as emojis)
- The **highest precedence type** determines the row background color
- The precedence order is enforced in:
  - Frontend: `TimeEntriesView.vue` (`displayEntries` computed property)
  - Frontend: `MonthlyCalendar.vue` (`getDayEmojis` function)
  - Backend: PDF export service

---

## 🎨 Color Palette

### Entry Type Colors

Colors use transparency (alpha channel) for subtle backgrounds while maintaining readability.

| Type | RGB Color | Alpha | Hex Equivalent | Usage |
|------|-----------|-------|----------------|-------|
| **Work** | `255, 255, 255` | `1.0` | `#FFFFFF` | Default white background |
| **Public Holiday** | `245, 158, 11` | `0.1` | `#F59E0B` (Amber-500) | Light amber/yellow tint |
| **Vacation** | `16, 185, 129` | `0.1` | `#10B981` (Green-500) | Light green tint |
| **Sick** | `239, 68, 68` | `0.1` | `#EF4444` (Red-500) | Light red tint |
| **Personal** | `59, 130, 246` | `0.1` | `#3B82F6` (Blue-500) | Light blue tint |
| **Recurring Off-Day** | `139, 92, 246` | `0.1` | `#8B5CF6` (Purple-500) | Light purple tint |
| **Weekend** | `107, 114, 128` | `0.1` | `#6B7280` (Gray-500) | Light gray tint |

### CSS Implementation

```css
.row-bg-work { background-color: rgba(255, 255, 255, 1); }
.row-bg-public-holiday { background-color: rgba(245, 158, 11, 0.1); }
.row-bg-vacation { background-color: rgba(16, 185, 129, 0.1); }
.row-bg-sick { background-color: rgba(239, 68, 68, 0.1); }
.row-bg-personal { background-color: rgba(59, 130, 246, 0.1); }
.row-bg-recurring-off { background-color: rgba(139, 92, 246, 0.1); }
.row-bg-weekend { background-color: rgba(107, 114, 128, 0.1); }
```

---

## 😀 Emoji Visual Indicators

### Entry Type Emojis

| Type | Emoji | Unicode | Meaning |
|------|-------|---------|---------|
| **Work** | 🏢 | U+1F3E2 | Office building (work location) |
| **Public Holiday** | 🎊 | U+1F38A | Confetti ball (celebration) |
| **Vacation** | 🏝️ | U+1F3DD | Desert island (holiday) |
| **Sick** | 😵‍💫 | U+1F635 U+200D U+1F4AB | Dizzy face (illness) |
| **Personal** | 🏠 | U+1F3E0 | House (home/personal) |
| **Recurring Off-Day** | 📴 | U+1F4F4 | Mobile phone off (scheduled absence) |
| **Weekend** | 🗓️ | U+1F5D3 | Spiral calendar (non-working day) |

### Implementation Locations

**Frontend:**
- `TimeEntriesView.vue` - `getTypeEmoji()` function
- `MonthlyCalendar.vue` - `getDayEmojis()` and tooltip generation
- Dashboard cards and overlays

**Backend:**
- PDF export service (if applicable)

---

## 🔧 Implementation Guidelines

### Adding a New Entry Type

1. **Define precedence** - Add to the precedence order list above
2. **Choose color** - Select a Tailwind CSS color with 0.1 alpha
3. **Select emoji** - Choose an intuitive, universally recognized emoji
4. **Update all locations**:
   - Frontend: `TimeEntriesView.vue` (emoji, color, precedence)
   - Frontend: `MonthlyCalendar.vue` (emoji, precedence)
   - Backend: PDF export (if applicable)
   - This document: Update tables and lists

### Testing Consistency

- Verify emojis match across:
  - Time entries table
  - Calendar day cards
  - Calendar tooltips
  - PDF exports
- Verify colors match:
  - Time entries row backgrounds
  - PDF export backgrounds (if applicable)
- Verify precedence:
  - Multiple types on same day show correct order
  - Correct background color applied (highest precedence)

---

## 📝 Change History

| Date | Change | Author |
|------|--------|--------|
| 2025-12-15 | Initial design system documentation | Claude Code |
| 2025-12-15 | Updated work emoji from 💼 to 🏢 | Claude Code |
| 2025-12-15 | Updated weekend emoji from 🏖️ to 🗓️ | Claude Code |
| 2025-12-15 | Updated recurring emoji back to 📴 (corrected from 🔄) | Claude Code |
| 2025-12-15 | Added row background colors for time entries | Claude Code |
| 2025-12-15 | Swapped colors: sick to red, public holiday to amber/yellow | Claude Code |

---

**Last Updated:** 2025-12-15
