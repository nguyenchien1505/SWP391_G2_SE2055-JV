---
name: Sao Mai Hospitality Ops
colors:
  surface: '#f9f9fd'
  surface-dim: '#d9dade'
  surface-bright: '#f9f9fd'
  surface-container-lowest: '#ffffff'
  surface-container-low: '#f3f3f7'
  surface-container: '#ededf1'
  surface-container-high: '#e8e8ec'
  surface-container-highest: '#e2e2e6'
  on-surface: '#1a1c1f'
  on-surface-variant: '#42474e'
  inverse-surface: '#2f3034'
  inverse-on-surface: '#f0f0f4'
  outline: '#72777f'
  outline-variant: '#c2c7cf'
  surface-tint: '#35618c'
  primary: '#00375e'
  on-primary: '#ffffff'
  primary-container: '#1f4e78'
  on-primary-container: '#95bff0'
  inverse-primary: '#a0cafb'
  secondary: '#0e61a1'
  on-secondary: '#ffffff'
  secondary-container: '#79b7fd'
  on-secondary-container: '#00477b'
  tertiary: '#4b2f00'
  on-tertiary: '#ffffff'
  tertiary-container: '#694400'
  on-tertiary-container: '#e7b369'
  error: '#ba1a1a'
  on-error: '#ffffff'
  error-container: '#ffdad6'
  on-error-container: '#93000a'
  primary-fixed: '#d0e4ff'
  primary-fixed-dim: '#a0cafb'
  on-primary-fixed: '#001d35'
  on-primary-fixed-variant: '#194973'
  secondary-fixed: '#d1e4ff'
  secondary-fixed-dim: '#9ecaff'
  on-secondary-fixed: '#001d36'
  on-secondary-fixed-variant: '#00497d'
  tertiary-fixed: '#ffddb3'
  tertiary-fixed-dim: '#f3bd72'
  on-tertiary-fixed: '#291800'
  on-tertiary-fixed-variant: '#624000'
  background: '#f9f9fd'
  on-background: '#1a1c1f'
  surface-variant: '#e2e2e6'
  status-available: '#2E7D32'
  status-available-bg: '#E8F5E9'
  status-available-border: '#C8E6C9'
  status-occupied: '#1565C0'
  status-occupied-bg: '#E3F2FD'
  status-occupied-border: '#BBDEFB'
  status-dirty: '#EF6C00'
  status-dirty-bg: '#FFF3E0'
  status-dirty-border: '#FFE0B2'
  status-cleaning: '#F9A825'
  status-cleaning-bg: '#FFFDE7'
  status-cleaning-border: '#FFF59D'
  status-inspecting: '#6A1B9A'
  status-inspecting-bg: '#F3E5F5'
  status-inspecting-border: '#E1BEE7'
  status-oos: '#616161'
  status-oos-bg: '#EEEEEE'
  status-oos-border: '#E0E0E0'
  surface-bg: '#F7F8FA'
  surface-card: '#FFFFFF'
  border-subtle: '#DFE3E8'
  text-primary: '#1C2330'
  text-secondary: '#5B6472'
typography:
  display-lg:
    fontFamily: Be Vietnam Pro
    fontSize: 30px
    fontWeight: '700'
    lineHeight: 38px
    letterSpacing: -0.02em
  headline-lg:
    fontFamily: Be Vietnam Pro
    fontSize: 22px
    fontWeight: '700'
    lineHeight: 28px
    letterSpacing: -0.015em
  headline-md:
    fontFamily: Be Vietnam Pro
    fontSize: 18px
    fontWeight: '600'
    lineHeight: 24px
    letterSpacing: -0.01em
  title-sm:
    fontFamily: Be Vietnam Pro
    fontSize: 16px
    fontWeight: '600'
    lineHeight: 22px
  body-lg:
    fontFamily: Be Vietnam Pro
    fontSize: 16px
    fontWeight: '400'
    lineHeight: 24px
  body-md:
    fontFamily: Be Vietnam Pro
    fontSize: 14px
    fontWeight: '400'
    lineHeight: 20px
  body-sm:
    fontFamily: Be Vietnam Pro
    fontSize: 12px
    fontWeight: '400'
    lineHeight: 16px
  label-md:
    fontFamily: Be Vietnam Pro
    fontSize: 13px
    fontWeight: '600'
    lineHeight: 18px
  label-sm:
    fontFamily: Be Vietnam Pro
    fontSize: 11px
    fontWeight: '600'
    lineHeight: 14px
    letterSpacing: 0.02em
  metric-val:
    fontFamily: Be Vietnam Pro
    fontSize: 28px
    fontWeight: '700'
    lineHeight: 32px
    letterSpacing: -0.02em
rounded:
  sm: 0.25rem
  DEFAULT: 0.5rem
  md: 0.75rem
  lg: 1rem
  xl: 1.5rem
  full: 9999px
spacing:
  gutter: 1rem
  margin: 1.5rem
  space-xs: 0.25rem
  space-sm: 0.5rem
  space-md: 1rem
  space-lg: 1.5rem
  space-xl: 2rem
---

## Brand & Style

This design system powers a multi-property hospitality operations platform (PMS / Ops SaaS) designed for mid-scale hotel chains (2–3 stars). It serves frontline operators: front desk receptionists managing rapid check-ins, housekeeping teams moving across floors on mobile devices, and operations managers monitoring cross-property logistics.

The brand personality is **reliable, razor-sharp, pragmatic, and calm under operational pressure**. It rejects decorative fluff in favor of immediate visual legibility, status differentiation, and high-speed execution—drawing structural discipline from modern tools like Linear and Notion, adapted specifically for high-frequency tactile shift work.

Key principles:
- **Zero-Ambiguity Status Recognition**: Operational states (clean, occupied, dirty, pending inspection, maintenance) must be perceived instantly across lighting conditions and screen scales.
- **Fail-Safe Business Rules**: Hard stops, shift overlaps, and maintenance alerts communicate with uncompromising clarity, preventing costly logistical errors before they occur.
- **Desktop Density, Mobile Ergonomics**: Dense, information-rich command centers for desktop managers alongside generous touch targets (minimum 48×48px) and scan-friendly cards for mobile housekeeping crews.

## Colors

The core palette anchors on authoritative deep navy (`#1F4E78`) paired with an active operational blue (`#2E74B5`). Backdrops remain cool and glare-free (`#F7F8FA`), providing high contrast against pure white cards (`#FFFFFF`) and hairline neutral dividers (`#DFE3E8`).

The six operational room and housekeeping states are functional brand anchors, not merely decorative accents:
- **Sẵn sàng / Trống (Available)**: `#2E7D32` with `#E8F5E9` background and `#C8E6C9` stroke. Denotes ready-for-sale, verified cleanliness.
- **Đang sử dụng / Đang ở (Occupied)**: `#1565C0` with `#E3F2FD` background and `#BBDEFB` stroke. High-contrast reassurance for in-house guests.
- **Chờ dọn (Pending Clean)**: `#EF6C00` with `#FFF3E0` background and `#FFE0B2` stroke. High urgency alert color for immediate turnover.
- **Đang dọn (In Progress)**: `#F9A825` with `#FFFDE7` background and `#FFF59D` stroke. Real-time active task indicator.
- **Chờ duyệt / Nghiệm thu (Pending Inspection)**: `#6A1B9A` with `#F3E5F5` background and `#E1BEE7` stroke. Manager sign-off queue.
- **Khóa phòng / Bảo trì (Out of Service)**: `#616161` with `#EEEEEE` background and `#E0E0E0` stroke. Deactivated inventory.

Color alone never conveys state; every badge, room card, or row must pair color-coded tokens with precise textual descriptors and dedicated semantic iconography.

## Typography

The design system standardizes on **Be Vietnam Pro** across all touchpoints to ensure native, unclipped rendering of complex Vietnamese tone marks and diacritics (`ẩ`, `ệ`, `ở`, `ữ`, `ặ`). 

Rules for typographic execution:
- **Diacritic Integrity**: Line heights (`line-height`) maintain generous bounds (minimum 1.35× to 1.5× of font size) to prevent diacritical collision or clipping in cramped tabular headers and status tags.
- **Numbers & Currencies**: All room codes, financial amounts (VND with thousands separators), and occupancy counters use tabular number alignment (`font-variant-numeric: tabular-nums`) to prevent jitter when data updates in real-time.
- **Mobile Ergonomics**: On field mobile devices (Housekeeping views), the standard body baseline elevates to 16px to prevent accidental zoom and facilitate effortless readability while in physical transit.

## Layout & Spacing

The layout is structured around an 8-point base spatial rhythm (`0.5rem` = 8px), providing consistent alignment from compact operational tables to expansive room grids.

- **Desktop (>= 1280px)**: Multi-column structure featuring a persistent 260px collapsible primary navigation sidebar, a contextual property/shift utility topbar, and a fluid main dashboard panel divided into a standard 12-column grid.
- **Tablet (768px – 1279px)**: Responsive collapse where the sidebar docks into an icon-rail (64px) or drawer, and multi-column room floorplans flow naturally from 4-card down to 3-card horizontal rows.
- **Mobile (< 768px)**: Strict single-column hierarchy optimized for thumb-driven usage. Bottom operational sticky bars for batch housekeeping actions, card lists replace complex tables, and horizontal tab filters enable rapid floor-by-floor navigation.

## Elevation & Depth

This system avoids expressive drop shadows, adopting an ultra-clean, functional depth hierarchy powered by crisp 1px borders and targeted micro-elevations:

- **Level 0 (Flat Canvas)**: `#F7F8FA` neutral surface background.
- **Level 1 (Card & Section Containers)**: `#FFFFFF` surface bounded by a crisp 1px border (`#DFE3E8`). Elevated solely through shadow: `0 1px 2px 0 rgba(16, 24, 40, 0.05)`.
- **Level 2 (Interactive Floating / Hover)**: Active room cards, draggable shift chips, and interactive cards elevate slightly on hover or grab: `0 4px 6px -1px rgba(16, 24, 40, 0.08), 0 2px 4px -2px rgba(16, 24, 40, 0.04)`.
- **Level 3 (Overlays & Dialogs)**: Dropdown menus, branch pickers, shift creation modals, and date range sheets: `0 10px 15px -3px rgba(16, 24, 40, 0.1), 0 4px 6px -4px rgba(16, 24, 40, 0.05)`.

Modal backdrops utilize `#1C2330` at 40% opacity with subtle backdrop blur (`backdrop-filter: blur(2px)`) to keep critical background floor context partially visible during quick edits.

## Shapes

The interface balances sharp industrial utility with ergonomic modern software curves:
- **Cards, Panels & Containers**: `8px` (`rounded-lg` / token `2`), establishing a grounded, structured frame.
- **Interactive Controls (Buttons, Inputs, Selects)**: `6px` (`rounded-md`), delivering a tactile, click-confident target.
- **Status Badges & Tenant Tags**: Full capsule `9999px` (`rounded-full`), visually detaching status metadata from structural card frames.
- **Floor & Room Identifiers**: Squared with subtle softening (4px) to accentuate alphanumeric room identification codes.

## Components

### Room Cards (Floor & Inventory Matrix)
- **Structure**: Encapsulated card (`#FFFFFF`, border `#DFE3E8`, 8px radius) featuring a distinct 3px colored top border corresponding to its current state.
- **Header**: High-contrast room number (e.g. "301", 18px bold) paired with a status pill badge in the upper right.
- **Body**: Secondary line for room tier (e.g. "Phòng Đôi · Tầng 3"), assigned housekeeper or guest name, and expected checkout/turnaround time.
- **Interaction**: Single-tap triggers quick-action popover (Guest Check-in, Mark Clean, Report Damage); click card body opens full room drawer.

### Metrics & KPI Cards
- Compact data widget with key indicator (`metric-val`, 28px bold, tabular numerals), contextual semantic icon, and micro-trend or sub-label.
- Clickable states include a subtle active ring (`#2E74B5`) allowing instant dashboard-level filtering of all sub-tables by clicking the corresponding card.

### Status Badges (Pills)
- Rendered with background tint (10% opacity), matching solid text color, and a 1px matching border.
- Mandatory pairing: 12px SVG icon + exact Vietnamese status text (e.g., `✓ Sẵn sàng`, `● Đang ở`, `! Chờ dọn`, `↻ Đang dọn`).

### Shift & Roster Calendar Grid
- Weekly 7-column layout with employee rows on the Y-axis.
- Unassigned shifts rendered with dashed 1px border (`#DFE3E8`) and striped translucent fill.
- Assigned shifts display as compact pills featuring shift hours, role tag, and instant conflict indicators (e.g. red dot for rest rule violations).

### Buttons & Inputs
- **Primary Action**: Background `#1F4E78`, text `#FFFFFF`, 6px radius, 38px height (48px on mobile views). Hover transitions to `#163957`.
- **Secondary / Ghost**: Outline `#DFE3E8`, text `#1C2330`, background `#FFFFFF`. Hover transitions to `#F7F8FA`.
- **Form Inputs**: 1px border `#DFE3E8`, background `#FFFFFF`, active focus ring 2px `#2E74B5` with 0px offset.

### Blocking Error Banners
- Dedicated notification containers for business rule violations (e.g. BR-SHIFT over-hours). `#FFF2F0` fill, `#FF4D4F` border, `#CF1322` text, explicitly enumerating the violated regulation before preventing state commitment.