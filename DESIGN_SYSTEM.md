# Time Tracking Application - Design System

**Single Source of Truth for Visual Design Decisions**

This document defines the visual design patterns, colors, emojis, and precedence rules used throughout the application (frontend and backend). All implementations should reference this document to ensure consistency.

## 🎨 Design Philosophy

The application follows a **logo-inspired design system** with a cohesive lime-to-teal gradient palette and the **60-30-10 color distribution rule**:

- **60% (Dominant)**: Light/neutral colors for main content areas (white, light grays)
- **30% (Secondary)**: Medium contrast colors for structure and text (dark grays, slate)
- **10% (Accent)**: Bold gradients and bright colors for primary actions and key metrics

This creates a balanced, professional interface with clear visual hierarchy and excellent readability.

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

### Brand Colors (Logo Gradient)

The primary color system is derived from the application logo's lime-to-teal gradient:

| Color | From → To | Hex Range | Usage |
|-------|-----------|-----------|-------|
| **Lime Green** | `#a3e635` → `#84cc16` | Primary brand color | Key metrics, primary actions |
| **Teal** | `#14b8a6` → `#0d9488` | Secondary brand color | Recurring patterns, secondary actions |
| **Cyan** | `#22d3ee` → `#06b6d4` | Bridge color | Personal/user-related items |
| **Emerald** | `#10b981` → `#059669` | Teal-green blend | Vacation, success states |
| **Yellow** | `#eab308` → `#ca8a04` | Warmer lime variant | Public holidays, warnings |
| **Coral** | `#f97316` → `#ea580c` | Warm complement | Sick days, alerts |
| **Slate** | `#64748b` → `#475569` | Neutral gray | Weekends, disabled states |

### 60-30-10 Distribution

| Percentage | Usage | Colors | CSS Variables |
|------------|-------|--------|---------------|
| **60%** | Main content, backgrounds | White, Light gray | `--tt-color-60-primary`, `--tt-color-60-secondary` |
| **30%** | Structure, text, borders | Dark gray, Slate | `--tt-color-30-primary/secondary/tertiary` |
| **10%** | Accents, CTAs, key metrics | Bold gradients (Lime, Teal, Emerald, Cyan, Yellow, Coral) | `--tt-color-10-primary/hover` |

### Entry Type Colors

Colors use transparency (alpha 0.12) for subtle backgrounds while maintaining readability, aligned with the logo-inspired palette:

| Type | RGB Color | Alpha | Hex Base | Palette Source |
|------|-----------|-------|----------|----------------|
| **Work** | `255, 255, 255` | `1.0` | `#FFFFFF` | Default white (60%) |
| **Public Holiday** | `234, 179, 8` | `0.12` | `#eab308` | Yellow (logo-inspired) |
| **Vacation** | `16, 185, 129` | `0.12` | `#10b981` | Emerald (logo-inspired) |
| **Sick** | `249, 115, 22` | `0.12` | `#f97316` | Coral (warm complement) |
| **Personal** | `34, 211, 238` | `0.12` | `#22d3ee` | Cyan (logo-inspired) |
| **Recurring Off-Day** | `20, 184, 166` | `0.12` | `#14b8a6` | Teal (logo-inspired) |
| **Weekend** | `100, 116, 139` | `0.12` | `#64748b` | Slate (neutral) |

### CSS Implementation

```css
/* CSS Variables (variables.css) */
--tt-row-bg-work: rgba(255, 255, 255, 1);
--tt-row-bg-public-holiday: rgba(234, 179, 8, 0.12);   /* Yellow */
--tt-row-bg-vacation: rgba(16, 185, 129, 0.12);        /* Emerald */
--tt-row-bg-sick: rgba(249, 115, 22, 0.12);            /* Coral */
--tt-row-bg-personal: rgba(34, 211, 238, 0.12);        /* Cyan */
--tt-row-bg-recurring-off: rgba(20, 184, 166, 0.12);   /* Teal */
--tt-row-bg-weekend: rgba(100, 116, 139, 0.12);        /* Slate */

/* Usage in Components */
.row-bg-work { background-color: var(--tt-row-bg-work); }
.row-bg-public-holiday { background-color: var(--tt-row-bg-public-holiday); }
.row-bg-vacation { background-color: var(--tt-row-bg-vacation); }
.row-bg-sick { background-color: var(--tt-row-bg-sick); }
.row-bg-personal { background-color: var(--tt-row-bg-personal); }
.row-bg-recurring-off { background-color: var(--tt-row-bg-recurring-off); }
.row-bg-weekend { background-color: var(--tt-row-bg-weekend); }
```

---

## 📏 Spacing System

The application uses an **8px/4px grid system** for consistent spacing throughout the interface.

### Base Spacing Units

| Name | Value | Pixels | Usage |
|------|-------|--------|-------|
| `--tt-spacing-2xs` | `0.25rem` | 4px | Tight spacing, icon gaps |
| `--tt-spacing-xs` | `0.5rem` | 8px | Small gaps, inline elements |
| `--tt-spacing-sm` | `0.75rem` | 12px | Compact sections |
| `--tt-spacing-md` | `1rem` | 16px | Standard spacing, form fields |
| `--tt-spacing-lg` | `1.5rem` | 24px | Section separation |
| `--tt-spacing-xl` | `2rem` | 32px | Large gaps, card spacing |
| `--tt-spacing-2xl` | `3rem` | 48px | Major section breaks |

### View Padding

| Breakpoint | Padding | Usage |
|------------|---------|-------|
| Desktop | `1rem 2rem 2rem 2rem` (16px 32px 32px 32px) | Standard view padding |
| Mobile | `0.75rem 1rem 1rem 1rem` (12px 16px 16px 16px) | Tablet and smaller |
| Extra Small | `0.5rem 0.75rem 0.75rem 0.75rem` (8px 12px 12px 12px) | Phone screens |

### Card Spacing

| Property | Value | Pixels | Usage |
|----------|-------|--------|-------|
| `--tt-card-gap` | `2rem` | 32px | Space between cards |
| `--tt-card-padding` | `1.5rem` | 24px | Standard card internal padding |
| `--tt-card-padding-sm` | `1rem` | 16px | Compact card padding |

---

## 📝 Typography

### Title Styling

| Property | Value | Usage |
|----------|-------|-------|
| `--tt-title-size` | `1.75rem` (28px) | Page titles |
| `--tt-title-weight` | `600` | Title font weight |
| `--tt-title-margin` | `0 0 1.5rem 0` (0 0 24px 0) | Bottom spacing |

### Text Colors

| Color | Value | Usage |
|-------|-------|-------|
| `--tt-text-primary` | `#1f2937` | Main text, headings |
| `--tt-text-secondary` | `#6c757d` | Secondary information |
| `--tt-text-muted` | `#888` | Disabled, placeholder text |

---

## 🎭 Component Patterns

### Action Cards

Action cards use the **10% accent principle** with bold gradients for primary actions:

| Card Type | Gradient | Usage |
|-----------|----------|-------|
| **Clock In** | Lime Green → Lime | Primary action (start work) |
| **Clock Out** | Coral → Coral Dark | Stop action (end work) |
| **Quick Entry** | Teal → Teal Dark | Secondary quick action |
| **Manual Entry** | Cyan → Cyan Dark | Manual data entry |

### Stat Cards

Stat cards follow the **60-30-10 rule**:

- **Primary metrics** (Vacation balance, Current overtime): Bold gradients (Emerald, Cyan)
- **Secondary stats** (Last month, 12-month average): Subtle backgrounds (light teal, light slate)
- **Info cards** (Warnings, Next vacation): Context-appropriate colors (coral, yellow)

### Dialog Sizing

Standardized dialog widths for consistency:

| Dialog Type | Width | Usage |
|-------------|-------|-------|
| Forms | `550px` | Create/Edit forms (users, time-off, etc.) |
| Editors | `600px` | Complex editors (time entries, schedules) |

### Shadows

| Shadow | Value | Usage |
|--------|-------|-------|
| `--tt-shadow-sm` | `0 2px 6px rgba(0,0,0,0.08)` | Subtle elevation |
| `--tt-shadow-md` | `0 2px 8px rgba(0,0,0,0.1)` | Standard cards |
| `--tt-shadow-hover` | `0 4px 10px rgba(0,0,0,0.12)` | Hover states |
| `--tt-shadow-hover-lg` | `0 4px 12px rgba(0,0,0,0.15)` | Large hover elevation |

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
| 2025-12-19 | **Major redesign**: Logo-inspired gradient palette (lime-to-teal) | Claude Code |
| 2025-12-19 | Applied 60-30-10 color distribution rule | Claude Code |
| 2025-12-19 | Updated all entry type colors to align with new palette | Claude Code |
| 2025-12-19 | Changed alpha from 0.1 to 0.12 for better visibility | Claude Code |
| 2025-12-19 | Added spacing system documentation (8px/4px grid) | Claude Code |
| 2025-12-19 | Added typography guidelines | Claude Code |
| 2025-12-19 | Added component patterns (action cards, stat cards, dialogs) | Claude Code |
| 2025-12-19 | Documented shadow system | Claude Code |
| 2025-12-19 | Added design philosophy section | Claude Code |

---

**Last Updated:** 2025-12-19

## 🔗 Related Files

**Frontend:**
- `frontend/src/styles/variables.css` - CSS variable definitions
- `frontend/src/styles/components/action-cards.css` - Action card gradients
- `frontend/src/styles/components/stat-cards.css` - Stat card styling
- `frontend/src/components/dashboard/MonthlyCalendar.vue` - Calendar colors and emojis
- `frontend/src/views/TimeEntriesView.vue` - Entry type colors and emojis

**Documentation:**
- `CHANGELOG.md` - User-facing change history
- `README.md` - Project overview and setup
