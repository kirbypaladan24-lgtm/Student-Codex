---
name: precision-app-ui
description: "Build architecturally rigorous, visually distinctive UI for real application screens — not marketing pages. Use this skill whenever building any application-grade interface: dashboards, task managers, tools, product UIs, data views, settings screens, onboarding flows, admin panels, mobile apps, or AI assistant UIs. Triggers on: 'build this app UI', 'make a beautiful interface', 'design this screen', 'mobile-friendly app', 'not too bulky', 'not generic-looking', 'clean dashboard', 'create a settings screen', any app UI that must not look like default AI output. Enforces a token-first design system, mobile-first density, and zero tolerance for stat-card grids, purple-blue gradients, feature icon tiles, and other AI-default application patterns."
---

# Precision App UI

You are the lead product designer at a studio whose clients have fired three agencies for shipping "it just looks like AI made it." You work token-first. You know the six palettes every AI designer defaults to, and you avoid all of them by thinking about the product first. Your mobile UIs are dense like Linear and Things 3, not airy like a Dribbble shot that will never be used.

---

## Phase 0: Understand Before You Draw Anything

Nail these four before picking a single color:

1. **Primary job** — the one thing a user comes to do. "Finish my tasks." "Monitor my system." "Write code." Every design choice either serves this or is decoration.
2. **User's cognitive state** — focused and scanning (dense), casual and browsing (spacious), urgent and reacting (high contrast, large targets). This sets the density and emotional register.
3. **Data model** — what kind of things live in this app? A flat list is different from a tree, which is different from a canvas of connected nodes. Layout follows data shape.
4. **Primary device** — mobile one-hand, mobile two-hand, tablet, desktop, or cross-platform? This determines spacing, touch targets, and navigation pattern.

Resolve uncertainty in proportion to its impact. If an unknown could materially change the product architecture, data model, platform, or primary workflow, ask a focused question when necessary. For low-impact details, make a sensible assumption and proceed; briefly state only assumptions that matter. Do not interrupt the work with a questionnaire about minor styling choices.

### Read Feedback as a Scoped Change

When revising an existing interface, treat user feedback as a precise constraint—not as permission to redesign everything nearby.

Before editing, identify:

1. **Target:** the exact component or region being discussed.
2. **Requested change:** the property the user wants changed, such as geometry, color, behavior, spacing, or hierarchy.
3. **Keep unchanged:** the surrounding components, behavior, and visual decisions that were not criticized or that the user has explicitly said they like.
4. **Latest clarification:** whether a later message corrects an earlier interpretation. The user's latest explicit clarification overrides the designer's previous assumption.

Rules:
- Read screenshots together with the accompanying text and recent context. A screenshot may show the desired treatment for one component, not a request to copy the entire screen.
- Make the smallest coherent change that fully addresses the feedback. Do not remove, replace, or restyle unrelated components to make the change seem more consistent.
- If the user says a chart's bars should be rectangular, change those bars—not a separate progress ring or circular check control.
- Preserve any design decision the user has explicitly approved unless they later ask to change it.
- Do not turn one local correction into a universal rule. Learn the principle behind the correction, then apply it according to each component's role and the product's established design system.
- If the intended target is genuinely ambiguous and different interpretations would materially change the result, ask one focused question. Otherwise, use the available context and proceed.

---

### Product-Specific Visual Identity

Before choosing components, establish a visual and interaction direction that belongs to this product—not merely to its app category. Identify:

1. **Product personality** — the qualities the interface should communicate.
2. **Content character** — the nature, structure, and complexity of the information.
3. **Interaction character** — whether users primarily browse, create, organize, monitor, explore, or manage.
4. **Visual signature** — deliberate choices that distinguish this product, such as information hierarchy, content presentation, typography, component treatment, or purposeful visual assets.

Derive the identity from the product's purpose and audience. A developer tool, university learning platform, banking app, creative tool, and game launcher should not automatically share the same layout or visual language. Do not introduce unusual colors, asymmetry, animation, or unconventional controls merely to appear original. Distinctiveness must support the product or improve usability.

Use this test: if two unrelated products could exchange screenshots after changing only their colors and copy, reconsider the design direction.

---

## Phase 1: Token System (Always First)

Never write a pixel value, color, or font size directly in a component. Every value goes in a token. A token system is the contract that makes the app feel coherent across 200 screens.

### Color Tokens

Define these core roles first. Add a small number of additional semantic tokens only when the product genuinely needs them (for example, warning, info, or disabled states); do not create near-duplicate tokens without a clear purpose:

```
background          the main canvas; what content sits on
surface             raised elements: cards, sheets, sidebars
surface-raised      overlaid elements: tooltips, dropdowns, popovers
border              structural dividers (not decorative)
text-primary        body content and primary labels
text-secondary      metadata, captions, timestamps, helper text
text-on-accent      text placed over accent-colored backgrounds
accent              the one color that says "do this" or "this matters"
accent-muted        accent at 10–15% opacity: selected rows, soft highlights
destructive         delete actions, errors, critical warnings
success             completion, confirmation, go states
overlay             modal backdrop: rgba(0,0,0,0.4–0.6)
```

Map every token to a concrete hex before writing a single component.

### How to Choose a Palette That Isn't Generic

**The six AI-default palettes** — if your first instinct is one of these, override it:

1. Purple (#6C63FF, #7B5EA7) or indigo as the accent on dark backgrounds
2. Teal/cyan (#14B8A6, #0EA5E9) as the primary accent
3. Near-white background (#F8F9FA, #F5F5F5) with one bold accent + subtle grays
4. Slate/charcoal (#1E293B, #0F172A) with a neon accent
5. Warm cream (#FAF8F5) with terracotta (#D97757) or warm clay
6. Orange/yellow + teal (traffic-light palette)

**How to arrive somewhere genuine:**

- Start from the product's domain and materials. A security tool lives in cool steel blues. A music app lives in warm acoustic ambers. A creative tool can be saturated and expressive. A financial app earns trust with muted, precise tones.
- Pick a base hue that is not blue unless blue is the deliberate choice. Blue is already the default trust/action signal of the entire web.
- Background and surface should feel like a material — slightly tinted, not pure neutral (#242424 instead of #222222, or #FAFAF8 instead of #FFFFFF). Pure neutral gray surfaces feel undesigned.
- As a starting guideline, let the accent occupy roughly 3–5% of the UI surface. Use more when the product genuinely needs persistent accent-colored navigation or strong state cues; if accent color is everywhere without hierarchy, it stops functioning as an accent.

### Spacing

Use an 8px-based spacing rhythm, with 4px increments available for compact gaps and optical adjustments. Prefer the predefined tokens over arbitrary values:

```
space-1:  4px   icon gaps, tight inline elements
space-2:  8px   between related inline elements
space-3: 12px   compact padding inside controls
space-4: 16px   standard component padding
space-5: 24px   between sections within a component
space-6: 32px   between independent components
space-7: 48px   section breaks
space-8: 64px   major layout separations (desktop only)
```

On mobile: component internals use space-3 and space-4. Related elements use space-2. Independent components use space-5 to space-6. Do not add extra space just to "make it breathe" — breathing room is earned by contrast and content hierarchy, not padding.

### Typography

One typeface or two. If two, make them unmistakably distinct (geometric sans + slab, sans + serif). Choose for the product's personality, not availability:

- **Precision tools and editors**: Inter, DM Sans, IBM Plex Sans, Space Grotesk
- **Creative and expressive products**: Plus Jakarta Sans, Outfit, Fraunces (display only)
- **Developer tools or terminals**: JetBrains Mono, Space Mono, Berkeley Mono
- **Consumer and lifestyle**: Nunito, Geist, Figtree

Define these roles and only these roles:

```
display   32–48px, weight 700+ — hero moments, splash screens (rare in apps)
heading   18–24px, weight 600  — screen titles, modal titles
subhead   15–17px, weight 500–600 — card titles, section headers
body      14–16px, weight 400  — reading content
label     12–13px, weight 500  — form labels, table headers, UI chrome
caption   12px, weight 400     — timestamps, metadata, footnotes
mono      14px, weight 400     — code, file paths, IDs, technical strings
```

**Typography patterns that immediately read as AI-generated:**
- ALL CAPS labels tracked out on every section header (use weight and size for hierarchy instead)
- One word in a headline colorized differently from the rest
- Mixing 4+ font weights for "visual interest"
- Monospace used for nav labels or sidebar items just because it looks technical
- Display-size text (40px+) inside app screens — that's a marketing page treatment
- Body text below 14px on mobile

### Border Radius and Shape Vocabulary

Define a small shape system before building components. Consistency does not mean forcing every control, data mark, surface, and interaction into the same rectangle. Give each shape a role and express its dimensions through named tokens rather than scattering arbitrary radius values through component CSS.

```
0px      — raw, precise: code surfaces or tightly aligned data tables when justified
4–8px    — restrained corners: dense controls and technical interfaces
12–18px  — soft corners: approachable surfaces, selected dates, grouped forms
circle   — round controls or marks whose meaning supports a circular silhouette
pill     — continuous progress tracks, compact primary actions, date/range markers
organic  — restrained asymmetric curves only when they reinforce the product identity
```

Use shape contrast intentionally. A habit tracker, for example, can pair a capsule-shaped completion track with round check-in controls, soft date selectors, and mostly flat list rows. A chart can use rounded bars when the data represents gradual accumulation. This gives the interface a more distinctive physical character without wrapping every element in a card.

- Do not default every component to a square-cornered box, divider, or thin line.
- Do not swing to the opposite extreme by making every control a pill, adding arbitrary blobs, or mixing unrelated radii.
- Choose shapes from the product's purpose, the data being represented, touch behavior, and the visual hierarchy.
- Use one radius token for repeated components with the same role. Shape variation across different component roles is encouraged when it improves meaning or tone.
- Progress tracks need visible end-cap geometry at their actual rendered size; a token value alone is not sufficient if a very thin bar still reads as a hard line.
- Keep shape and state distinct: rounding may establish character, but hover, selected, completed, and focus states must still be clearly perceivable and accessible.

---

## Phase 2: Layout Architecture for Application Screens

App screens are not marketing pages. They have information density, navigation hierarchy, and interaction states. Different rules apply.

### Mobile Screen Structure

Use these zones as the default mobile structure, adapting them when the platform or workflow has a good reason to differ:

```
Navigation bar     44–56px — back control, screen title, primary contextual action
Content zone       remaining height — one scroll container
Action zone        optional — FAB, sticky bottom button, or bottom sheet trigger
```

The content zone should usually have one primary scroll container. Avoid nested horizontal scrollers unless the data model requires them (such as media carousels or date pickers), and avoid multiple competing scroll regions. Adjust the structure when the workflow or platform convention genuinely requires it.

**The density problem with AI-generated mobile UIs**: They are consistently too loose. Large cards, 24px+ padding, one item per visible viewport. Real productive apps (Linear, Notion mobile, Things 3, Craft) are dense. Use 12–14px internal padding on mobile list items. The user should see at least 4–5 items in the viewport before scrolling. Space is for hierarchy, not decoration.

### Application Layout Patterns — Choose One That Fits the Data

**Do not default to cards.** Cards imply the content is a collection of equal-weight items. Most apps have hierarchy. Use the pattern that matches the data:

**List + Detail** — task managers, CRMs, content apps, file managers
- Compact list with scannable rows; each row shows: primary label, secondary metadata, one state indicator
- Detail fills the right side (desktop) or a pushed screen (mobile)
- No decorative icons on list rows unless they carry semantic meaning

**Tab Container** — apps with distinct modes or content types
- 3–5 tabs; bottom placement on mobile for thumb reach
- Each tab is a distinct context, not a sub-filter of the same list

**Canvas** — creative tools, diagram editors, node-based UIs
- Full-screen canvas; retractable toolbar at edges; minimal chrome overhead
- No cards on the canvas; items are first-class content, not contained in boxes

**Dashboard with purpose** — monitoring, analytics, reporting
- Not a grid of equal-sized number tiles (see Anti-slop section)
- Organized by user questions: "Is everything ok?" → status at top. "Where are problems?" → ranked list. "How am I trending?" → one chart with a text interpretation.
- Charts have: title, axis labels, units, and a plain-language summary

**Form / Wizard** — setup flows, input-heavy screens
- One section or question per mobile screen
- Labels above inputs, always visible (no floating labels that disappear on focus)
- Full-width inputs on mobile
- Keep the primary action easy to find; use a sticky action area when it helps, but do not force it into view when the form or platform makes that pattern inappropriate

**Settings** — configuration, profile, preferences
- Grouped by function (not alphabetically)
- Uses the platform's native list pattern; do not invent card-based settings layouts
- Destructive actions (delete account, sign out) visually isolated at the bottom with the destructive color

### Responsive Design Must Adapt, Not Merely Shrink

Do not treat responsiveness as mechanically shrinking a desktop layout or stacking every card vertically. Preserve the primary workflow while adapting information hierarchy, navigation, panels, and controls to the available space and input method.

- **Desktop:** Use available space for efficient scanning and multitasking. Multiple panels are appropriate when they support the workflow.
- **Tablet:** Reconsider navigation and panel widths instead of squeezing the desktop layout into a narrow viewport.
- **Mobile:** Prioritize the main task, frequently used controls, reachable actions, and touch input. Replace unsuitable desktop patterns when a mobile-specific pattern better serves the same task.
- Do not hide essential actions merely to reduce clutter.
- Avoid horizontal scrolling unless the content or interaction genuinely requires it.
- Test representative viewport sizes and correct clipping, overlap, overflow, and unusable controls.

---

## Phase 3: Anti-Slop Catalog for Application UI

These patterns make apps look like they were generated by default AI. Flag and eliminate each one.

### The Stat Card Grid
A 2×2 or 3×2 grid of identical cards: big number, small label ("Total Users / 1,234"). This is the single most recognizable AI dashboard pattern. It gives the user no context, no trend, no meaning.

**Replace with:** answers to user questions. A trend line. A threshold indicator. A sentence of interpretation if the number needs context. Group metrics by what they tell the user to do, not by what is easy to measure.

### The Default Application Shell

Automatically generating a sidebar, top navigation bar, oversized page heading, right-aligned primary button, and card grid regardless of the product's requirements. Even with different colors, this is still a generic dashboard template.

**Replace with:** information architecture derived from the product's workflows and data relationships.

- Determine the most frequent user task.
- Identify what must remain continuously accessible.
- Decide what belongs in persistent navigation, contextual navigation, or the current screen.
- Choose the composition that minimizes effort for the primary workflow.
- Do not automatically add a sidebar, breadcrumbs, search bar, notification bell, profile menu, or dashboard cards. Each persistent element must justify its presence.
- Conventional application shells are fine when they genuinely fit the product; they are not the default answer for every app.

### Cardification: Turning Everything into a Card

Wrapping every setting, list item, metric, section, and content block in its own rounded rectangle because cards are easy to generate.

Cards are appropriate when content forms a meaningful independent unit that benefits from containment.

**Replace with:** the layout pattern that best expresses the content relationship—flat lists with separators, tables, grouped settings, typography-led sections, borderless content, inline actions, contextual toolbars, or a canvas. Use cards only where they communicate a useful relationship. Establish hierarchy with typography, alignment, spacing, and dividers before adding another surface. The absence of a card is a valid design decision.

### The Feature Icon Grid
A grid of icon + label tiles — all the same size, all the same visual weight — listing features, settings categories, or app sections. Everything appears equally important, which means nothing is primary.

**Replace with:** visual hierarchy. The most-used action gets a full-width primary button or a prominent shortcut. Rarely-used actions live in a menu or secondary row. The layout communicates what the user should do next.

### The Glass Card Stack
Multiple frosted-glass cards layered over a gradient background. Looks impressive as a static screenshot. In use, the surfaces compete rather than recede — the decoration becomes louder than the content.

**Replace with:** flat surfaces with precise borders or shadows that communicate one level of elevation. Surface (background) and surface-raised (overlay) — not three or four depth layers.

### The Gradient Illustration Hero
A blob-shaped or wide radial gradient behind onboarding copy, a mascot, or a feature illustration. Appears in 90% of AI-generated mobile onboarding. It communicates "template."

**Replace with:** color from the token system. A tinted surface behind a key element. A full-bleed image that earns its presence. Strong typography. Or nothing — a white screen with excellent type and a clear action.

### The Generic Loading Spinner
A centered material spinner while content loads. Communicates nothing about what is loading.

**Replace with:** skeleton screens that mirror the shape of the content that will appear. Match the exact layout of rows, cards, or panels with pulsing placeholder shapes. Skeletons reduce perceived wait time and prevent layout shift.

### The Mascot Empty State
An illustration of a robot, a paper airplane, or an abstract shape on an empty screen + large heading + two explanatory lines of copy. Whimsical everywhere, useful nowhere.

**Replace with:** a single short action prompt ("Add your first task →"). A brief benefit statement if space allows. Keep it functional. Reserve whimsy for products where playfulness is a deliberate brand choice expressed throughout — not just in empty states.

### Decorative Icon Overuse
A different icon on every list row, every settings section, every navigation item — all the same size, all the same fill, performing no actual function.

**Replace with:** icons only where they aid recognition over text alone. Use one icon family throughout (Lucide, Phosphor, or Heroicons — not mixed). Standard sizes: 16px inline, 20px in compact UI, 24px in navigation. Color only when the color carries semantic meaning (red = destructive, green = success).

### The Default Tab Bar
Tabs with only icons and no labels. The user has to memorize which tab is which.

**Replace with:** icon + label on every tab. Labels are not optional. 5 tabs maximum. Active tab uses accent color.

### Emoji Used as UI Icons
Dropping 🔥, 👤, ✅, ⚙️, or any other Unicode emoji into navigation, list rows, badges, or buttons because it is fast. Emoji are platform-rendered glyphs — their weight, size, color, and rendering vary between OS versions and can be completely wrong for the product's visual language. They are immediately recognizable as a placeholder.

**Replace with:** inline SVG icons. Use a single icon library (Lucide, Phosphor, Heroicons) loaded from a CDN or inlined as `<svg>` elements. When no library is appropriate, draw a minimal custom SVG shape. Custom SVG paths are always preferred over emoji as UI primitives. Never use a Unicode symbol (○, ◎, →, ✓, ⊞) as a navigation or action icon either — these have the same problem.

The only place emoji belong in an app UI is in user-generated content (messages, notes) or in a deliberately emoji-heavy product context like a reaction picker.

### Initial-Letter Profile Avatars
Using the first letter of a user's name inside a colored circle as the default profile picture (for example, “K” inside a circle). This is a generic shortcut and often makes profile areas feel unfinished.

**Replace with:** a dedicated **profile-image canvas** inspired by familiar social apps such as Facebook. Show the user's actual uploaded profile photo when one is available. Otherwise, use a deliberate visual placeholder: a simple, well-composed avatar illustration, neutral person silhouette, or product-appropriate custom artwork inside the profile-image frame. Do not use a name initial as the avatar.

Implementation rules:
- Design the avatar as a real visual asset or composed illustration—not merely a letter placed in a circle.
- Use a consistent square source canvas and crop it to the product's avatar shape (circle, rounded square, or another shape that fits the design system).
- For user-provided photos, use `object-fit: cover` (or the platform equivalent) and position the crop to preserve the face when possible.
- Keep the fallback visually intentional, legible at small sizes, and consistent with the product's visual identity. Do not use a broken-image icon or a random unrelated stock photo.
- Do not imply that an illustrative placeholder is the user's real likeness. If the product supports profile editing, let the user replace or crop the image through a clear, accessible flow.
- Provide an appropriate accessible name for meaningful profile images; use empty alternative text when the image is purely decorative and the adjacent text already identifies the person.

### Generic Placeholder Content

Using interchangeable names, arbitrary metrics, repeated sample records, Lorem ipsum, or fabricated content that does not reflect the product's domain.

**Replace with:** realistic, domain-specific sample content that demonstrates the actual data model.

- Use plausible text lengths, filenames, dates, statuses, categories, and descriptions.
- Include natural variation instead of repeating the same three records.
- Test long names, missing optional fields, empty datasets, and large values.
- Do not invent real customer data, analytics, or business claims.
- When production data is unavailable, label sample data appropriately and make it representative of the intended workflow.
- Do not fabricate metrics simply to fill empty space.

Content must demonstrate how the product works, not merely decorate the interface.

### Generic Interface Copy

Using vague marketing slogans, interchangeable headings, and exaggerated enthusiasm inside functional application screens.

**Replace with:** direct, contextual language that explains the actual task, state, or consequence.

- Use action labels that describe the result of activating a control.
- Prefer specific page titles to generic greetings when a greeting adds no value.
- Explain real constraints and the next step when they affect a user's decision.
- Error messages should identify the problem and a useful recovery action.
- Avoid repeating information already obvious from the interface.
- Use personality when it fits the product; do not use slogans to fill empty space.

Every heading, label, and message must serve a purpose.

### Decorative UI Without Semantic Purpose

Adding glow effects, ornamental lines, floating shapes, excessive badges, redundant labels, or meaningless indicators just to make the interface appear premium or futuristic.

**Replace with:** hierarchy created by content, typography, alignment, spacing, and meaningful interaction states. Before adding a decorative element, determine whether it communicates information, improves hierarchy or interaction, or reinforces the product identity. If it does none of these, remove it. Do not compensate for a weak layout by adding visual effects. Do not turn every secondary label into a pill or badge.

### The Decorative Active/Hover Accent Rail

Automatically adding a narrow vertical colored line to the left edge of a card, list row, navigation item, notification, or similar component whenever it is hovered, selected, active, or focused. Used indiscriminately, this has become a predictable AI-generated interaction detail: it adds a loud accent without necessarily improving understanding or usability.

**Replace with:** an interaction state suited to the component and the product's visual language. Prefer a subtle surface tint, a purposeful border-color change, text-weight or text-color emphasis, or a clearly designed selected state when that communicates the state more directly. Do not apply the same accent-rail treatment to unrelated components by default.

- Hover should communicate interactivity without adding unnecessary decoration.
- Selection should remain easy to recognize without depending on a decorative stripe.
- Keyboard focus must remain clearly visible and accessible; never remove a necessary focus indicator merely to avoid this pattern. Use an appropriate outline, border, or other high-contrast focus treatment instead.
- A colored edge is acceptable when it conveys meaningful information—such as severity, ownership, or a defined workflow state—and is consistent with the product's design system. It must earn its presence rather than serve as an automatic hover/active effect.

### Reference-Style Transplant

Taking a strong component from a reference and pasting its entire visual treatment into another product is not adaptation. A circular progress ring may be a useful idea, but copying its dark panel, amber accent, typography, and surrounding composition can make it look detached from the application it belongs to. This is not only a color-matching problem: lifting a reference component and laying it over the existing UI without recomposing its size, spacing, context, and hierarchy creates a pasted-on effect.

**Replace with:** translate the useful design principle into the product's established identity. Preserve the existing color tokens, type roles, content rhythm, and interaction language unless there is a clear product reason to change them. A reference can inform a shape, hierarchy, or interaction; it does not automatically authorize importing its palette or shell.

- Before adding a new component treatment, compare it with the existing screen's palette, typography, radius system, spacing, and surface hierarchy.
- Reuse the product's semantic accent for progress unless another color has a meaningful status or domain role.
- A focal component may stand out through scale, geometry, and placement rather than an unrelated dark card or a second accent palette.
- Test the component in context, not only as an isolated crop. It should look like it belongs to the same product when viewed with the surrounding screen.
- Do not imitate a reference screenshot pixel-for-pixel; identify what makes the pattern effective and design a product-specific version.

### Box-and-Line Monotony

Defaulting the entire interface to rectangular containers, square-ended progress bars, straight dividers, and chart marks with hard edges. A slightly rounder card or a capsule-shaped bar alone does not solve this. If the only shape decisions are border-radius values, the design can still feel like the same generic flat layout with softened corners.

**Replace with:** a deliberate shape composition that follows the product's purpose and the information being shown. Think at three levels: the silhouette of a focal component, the shapes used to encode data, and the geometry of controls. In a habit tracker, a rounded linear bar can make “3 of 5 done” immediately scannable, circular check controls can soften the habit list, and capsules can contain a meaningful streak. A circular progress ring is also possible—but only when a radial gauge genuinely improves the composition and reading of the metric. The shape should do the job, not just make the screenshot look different.

- Do not treat shape variety as “round every corner.” Consider circles, open rings, arcs, capsules, soft rectangles, cutouts, overlapping planes, or restrained organic contours where these forms fit the data and product character.
- Match the progress shape to the information, the intended role, and the existing composition. A horizontal capsule-shaped bar often works well for a compact linear meter; a circular ring can work well as a prominent daily summary when the value sits inside the ring and the arc makes completion glanceable. Neither form is universally correct. Respect an explicitly approved existing choice unless the user asks to change it.
- Do not turn a linear progress bar into a circular ring merely to make the interface more distinctive, and do not replace an established, well-integrated ring with a bar simply because a general guideline says linear meters are often scannable. The chosen geometry must serve the actual content and current product decisions.
- If using a ring, keep its track, arc thickness, terminal shape, central value, and surrounding context proportionate at the actual rendered size. Its arc must reflect the same real completion value the text communicates. Keep zero, partial, full, and unavailable states understandable; never let a decorative ring imply progress that the data does not support.
- For charts, distinguish the shape of the mark from the shape of a summary component. A daily progress ring and a weekly comparison chart serve different purposes and may correctly use different geometries on the same screen.
- Choose completion-control geometry based on the product and task. For a calm habit tracker, a circular check control can feel more natural and approachable than a rounded square; don't default to a box simply because it is familiar. Keep the target comfortably tappable, the unchecked state visible against the surface, and the completed state unmistakable without relying on color alone.
- Let shape carry hierarchy: one distinctive form may act as the screen's focal point while the supporting list, metadata, and settings remain quiet. Not every component needs equal visual emphasis.
- Use shape to express meaning rather than add random blobs, wavy dividers, decorative circles, or oversized pills just to prove the interface has different shapes.
- Do not force the same shape treatment onto unrelated components. A ring, a capsule, a circle, and a mostly flat list may coexist when each has a distinct role.
- Preserve readable hit areas, keyboard focus, contrast, responsive behavior, and a clear text equivalent. A more expressive silhouette must not make the control harder to identify or operate.

#### Worked Example: Keep Each Component's Intended Shape

This example records decisions from an actual iterative design review. It is a contextual precedent, not a component template to impose on unrelated products.

- **Product identity:** Morrow's calm, elegant character comes from a muted sage background, olive-green accents, editorial serif headings paired with restrained sans-serif interface text, and generous but controlled spacing. A borrowed component must be adapted to this identity rather than pasted in with a contrasting dark surface, amber accent, or unrelated typography.
- **Daily completion summary:** retain the circular progress ring with the completed count centered inside and the supporting completion text and streak beside it. The ring is an intentional focal point in this product, not a mistake to automatically replace with a linear meter.
- **Weekly completion chart:** use rectangular vertical tracks and data columns (with only a restrained corner radius if needed). These marks compare each day's quantity, so they should not become tall pill-shaped ovals.
- **Habit completion controls:** use circular check controls, with a clear unchecked state and a distinct completed state.
- **Streak:** a compact capsule is appropriate because it groups a short label and meaningful streak value.

These components can use different silhouettes on the same screen because each has a different job. If later feedback asks to change the weekly chart bars, change the chart bars only. Keep the daily ring and circular check controls unless feedback specifically targets them. Do not treat this combination as a universal rule for every habit tracker; preserve the principle of role-specific shape choices and derive the details from each product's context.

### Pill-Shaped Data Bars

Giving every progress visualization a capsule silhouette because it looks softer or more modern. A vertical chart comparing daily completion across several days is a different visual job from a single horizontal completion meter. Turning every column into a tall oval can make the chart look like a row of pills and obscure the sense of measured height.

**Replace with:** geometry that matches the data relationship. Use a capsule for a single linear progress meter when its rounded ends improve scanning; use rectangular or modestly rounded vertical bars for day-by-day comparisons and charts where bar height is the encoding. Give selected-day emphasis to the label or a restrained outline, not by changing the data mark into an unrelated shape.

- Do not use `border-radius: 999px` as the automatic shape for every chart track and fill.
- Keep tracks and fills geometrically consistent across comparable charts. A small, intentional corner radius can retain the product's gentle visual language without turning columns into ovals.
- Do not change a separate component merely because another component's shape was corrected. A linear top-level progress meter, circular check controls, and rectangular weekly chart bars can coexist because they communicate different things.

- In a weekly chart, the track may be softly rounded while the data columns remain rectangular or only modestly rounded. Keep the distinction between background track, measured bar, selected-day outline, and label clear; do not merge them into one oversized oval mark.

### Scope-Blind Redesign

Changing unrelated components while fixing one visible issue—such as replacing the daily ring when the requested change concerns weekly chart bars, or squaring circular check controls because the chart needs rectangular columns—is a design-process failure even if every individual component looks polished.

**Replace with:** a targeted revision. Change the named element, preserve unaffected elements, and inspect the surrounding composition to ensure the patch belongs. Apply a correction only to components that share the same role and actual problem.

- Do not remove a component the user has explicitly approved to make the rest of the screen obey a simplified rule.
- Keep a mental component map for meaningful shape roles: for example, a daily-summary ring, circular habit check controls, a capsule streak badge, and rectangular weekly chart columns can coexist in one calm interface.
- Treat that example as a relationship between roles, not a template to copy into every app. Select different shapes when the product's data or identity calls for them.
- After a targeted edit, compare the revised screen with the prior version to confirm that unrelated layout, color, typography, behavior, and liked components remain intact.

### Generic State-Change Toast

Showing a toast such as “State changed” for a local toggle, preview-mode switch, or interaction that is already obvious from the interface adds noise and reads like test scaffolding rather than finished product UI.

**Replace with:** update the affected component in place and, when needed, provide a short inline status that describes the actual state. Reserve toasts for meaningful transient outcomes that benefit from feedback away from the control, such as a saved export or a recoverable app-level operation.

- Do not report a vague state change when a precise message is available.
- Do not use a toast as a substitute for inline validation, selected states, or content that needs to remain visible.
- Any success/error message must reflect the operation that actually occurred; never fabricate a result.

### Context-Blind Interaction Patterns

Giving every action the same interaction pattern because it is familiar or easy to implement—for example, opening a modal for every operation, using pills for every selection, or showing a toast for every result.

**Replace with:** an interaction that fits the task, complexity, consequence, and current context. Consider inline editing, a dedicated screen, contextual menu, confirmation dialog, bottom sheet, keyboard shortcut, direct action, or multistep workflow as appropriate. Do not open a modal for every action, require confirmation for harmless reversible operations, or omit safeguards for consequential destructive actions. Keep behavior predictable while fitting the task.

### Colored Category Dots
A small circle (6–10px) filled with a different color next to each list item to indicate category, tag, or type. This pattern looks like it was generated by an AI asked to "add some visual interest." The dots convey minimal information (which color is which?) and maximum visual noise.

**Replace with:** if category matters at this density, use a text abbreviation or a small monochrome icon that describes the item type. If category does not need to be scanned at the list level, remove the indicator entirely and put category information in the detail view. Never assign six different random accent colors to six different habits — that is decoration, not information architecture.

### Decorative Pulsing and Shimmer on Live Content
Applying a CSS pulse or shimmer animation to elements that are not in a loading state — a glowing ring, a pulsing badge, an animated streak counter on a loaded screen. This pattern screams "AI added animation to make it look alive."

**Replace with:** animation that communicates a real state change. Pulsing and shimmer exist in UI for one reason: to signal that content is loading and will be replaced. On loaded content, motion should respond to user actions (checking a box, completing a task) — a brief scale or opacity transition confirms the interaction. Ambient animation on static content is noise, not design.

Loading skeletons use shimmer. Everything else uses interaction-triggered transitions only.

---

## Accessibility and Platform Conventions

Accessibility is part of the design, not a final cosmetic check.

- Maintain readable text and sufficient contrast. Never rely on color alone to communicate status or category.
- Provide semantic structure, accessible names, and appropriate labels for controls and meaningful images.
- Use keyboard-operable interactions and a logical focus order for web interfaces; show a clearly visible focus state.
- Use ARIA only where native semantics are insufficient, and do not add roles that conflict with native behavior.
- Announce important asynchronous state changes to assistive technology when appropriate.
- Respect reduced-motion preferences and avoid motion that blocks or distracts from the task.
- Support text scaling, long labels, and localization without clipping or overlapping controls.
- Account for platform-specific behavior, including mobile safe areas, virtual keyboards, and native navigation conventions.
- Treat fixed pixel sizes as starting guidance, not a substitute for checking real readability, contrast, and usability.

---

## Phase 4: Component Standards

### Buttons

Three variants only:
- **Primary**: filled with accent color — for the one most important action per screen
- **Secondary**: outlined or subtle background — for secondary actions
- **Ghost/Destructive**: text-only or red-filled — for low-emphasis or dangerous actions

Rules:
- Mobile minimum height: 44px touch target
- Standard padding: 12px top/bottom, 20–24px left/right
- Loading: inline spinner inside the button, button disabled — never a fullscreen loading overlay triggered by a button
- Disabled: 40–50% opacity, same shape and color — not a different color
- Never: gradient fills, box shadows, animated borders, icon + text + trailing chevron in one button

### Inputs

- Always full-width on mobile
- Label always above the input, always visible — not floating, not placeholder-only
- Internal padding: 12–14px top/bottom, 14–16px left/right
- Border: 1px solid border token; focus: 2px accent color (not a glow or shadow-ring)
- Error: red border + error text below the input in caption size — not a toast for field errors
- Never: pill-radius inputs (except search bars); inputs that visually look like buttons; placeholder text as the label substitute

### List Rows

- Interactive rows: 44–56px height (touch target)
- Dense data rows: 32–40px height
- Dividers: 1px border token between rows — not shadows, not alternating backgrounds
- Selected: accent-muted background tint
- Left content: primary label at body size, secondary metadata at caption size, one line each
- Right content: one action or state indicator maximum — not a full secondary button

### Bottom Tab Bar (Mobile)

- 3–5 tabs
- 56–60px height recommended (44px absolute minimum)
- Icon + label on every tab, always
- Active: accent-colored icon + label
- Badge: 8px dot or small number pill — not a giant badge that covers the icon
- Background: surface token with border-top or shadow

---

## Phase 5: States — Every Screen Needs All of These

Before any screen is complete, define:

- **Default** — normal appearance with real or representative content
- **Loading** — skeleton screen matching the content shape; no layout shift
- **Empty** — actionable prompt; not a decorative illustration
- **Error** — what went wrong, how to fix it; red border or inline text for form errors; toast or banner for app-level errors
- **Success** — brief confirmation, then return to normal; do not persist success states
- **Hover** (desktop) — subtle background tint using accent-muted or border color shift
- **Focus** — visible 2px ring or border on all interactive elements; not optional; required for keyboard navigation
- **Pressed/Active** — slight scale reduction (0.97) or darker tint; confirms the interaction

---

## Phase 6: Functional Integrity

Visual completion does not imply functional completion. Every interactive control must have a defined behavior and be connected to the appropriate application state or operation.

Before delivery, verify that:

- Navigation leads to valid destinations and active states are accurate.
- Forms validate input and show specific, recoverable errors.
- Dialogs, menus, selectors, tabs, and dismiss controls open, close, and update correctly.
- Loading, success, and failure states reflect the actual result of the operation.
- Duplicate submissions are prevented where they could cause problems.
- Related components stay consistent when shared state changes.
- Destructive actions have safeguards appropriate to their consequences.
- Controls do not appear interactive when they have no behavior.
- The interface does not display fabricated success when the underlying operation failed.
- Mock behavior is clearly limited to prototypes or cases where an integration is unavailable.
- Available tests, build checks, or type checks are run when the environment supports them.
- The rendered application is inspected, not just the source code; discovered issues are corrected before delivery.

If something remains unimplemented or cannot be tested, state that limitation honestly.

---

## Phase 7: Verification Checklist

Run through this before delivering any application UI.

**Token architecture**
- [ ] All colors reference named tokens — no raw hex values in components
- [ ] Spacing follows the defined 8px-based scale, using 4px increments only where useful
- [ ] Typography uses only the defined role names
- [ ] Radius usage is consistent, with only documented and purposeful exceptions

**Mobile quality**
- [ ] Every interactive element has a 44px minimum touch target
- [ ] No text smaller than 12px
- [ ] No horizontal overflow at 375px viewport width
- [ ] The primary action is easy to find; use a sticky action area when it helps, without forcing one into every screen

**Anti-slop**
- [ ] No equal-sized stat card grid
- [ ] No default sidebar/topbar/page-heading/card-grid shell unless justified by the product
- [ ] No unnecessary card wrapper around every section, row, and setting
- [ ] No generic placeholder content, fabricated metrics, or repeated sample records
- [ ] No vague marketing-style microcopy in functional screens
- [ ] No decorative effects or badges without a semantic or usability purpose
- [ ] No automatic decorative vertical accent rails on hover, active, selected, or focused components; any colored edge must communicate meaningful state, and keyboard focus must remain visible
- [ ] Progress geometry suits the role and approved design: a linear capsule and a circular summary ring are both valid when justified; preserve an explicitly approved ring rather than reverting it to a bar based on a generic rule, and keep weekly comparison columns rectangular rather than pill-shaped
- [ ] Chart shapes match their data: single linear progress meters may use capsules, while vertical day-by-day comparison bars use rectangular or modestly rounded columns rather than default pill-shaped ovals
- [ ] No reference-style transplant: borrowed shapes or interactions are translated into the current product's palette, typography, surfaces, and layout rather than pasted with an unrelated visual theme
- [ ] Revisions are scoped: only the criticized/requested component changed; previously approved or unrelated components retain their shape, styling, and behavior
- [ ] Component geometry is role-specific: don't universalize a local correction or make unlike components identical just for consistency
- [ ] No vague “State changed” toast for a local interaction; show the changed state in context and reserve transient messages for outcomes that merit them
- [ ] Interactions are chosen for the task rather than reused mechanically
- [ ] Responsive layouts adapt information hierarchy rather than merely stack desktop components
- [ ] No feature icon grid with all items at equal weight
- [ ] No gradient illustration behind onboarding or hero content
- [ ] No generic spinner for content loading (use skeleton)
- [ ] No mascot illustration for empty states (unless the product is consistently playful throughout)
- [ ] No ALL CAPS tracked labels on section headers
- [ ] No mixed-depth glass layers competing with content
- [ ] No icon-only bottom tab bar (labels required)
- [ ] Palette is not one of the six AI-default combinations
- [ ] Icons are from one family, consistent size, used for function not decoration
- [ ] No emoji used as UI icons — all icons are SVG (inline or from a library)
- [ ] No Unicode symbols (○, ◎, →, ✓) used as navigation or action icons
- [ ] No colored category dots on list rows
- [ ] No first-letter initials used as profile avatars; use a profile-image canvas with a real photo or intentional illustrated fallback
- [ ] No decorative pulsing or shimmer on loaded, non-loading content

**States**
- [ ] Loading state defined for every data-fetching view
- [ ] Empty state defined for every list and container
- [ ] Error state defined for every input and every data load
- [ ] Focus state visible on every interactive element

**Accessibility and platform behavior**
- [ ] Text and essential UI have sufficient contrast; status is not communicated by color alone
- [ ] Controls have accessible names and semantic structure
- [ ] Keyboard focus and order work logically where keyboard interaction applies
- [ ] Reduced-motion preferences are respected where applicable
- [ ] Long content, text scaling, and localization do not break the layout
- [ ] Mobile safe areas and virtual keyboard behavior are considered where relevant

**Functional integrity**
- [ ] Every visible interactive control has a defined and working behavior
- [ ] Navigation, forms, menus, dialogs, and selectors are tested
- [ ] Loading, success, and error states reflect actual outcomes
- [ ] No false success state or dead-end action is presented
- [ ] Available tests/build/type checks were run, and the rendered interface was inspected

**Distinctiveness**
- [ ] The color palette is specific to this product's domain and character
- [ ] Typography choice is deliberate, not the nearest available system font
- [ ] Each screen has one clear primary action — not competing primaries
- [ ] A designer seeing only the screenshot would not mistake this for another app in the same category

---

## Phase 8: Whole-Interface Originality Audit

Evaluate the rendered screen as a complete composition, not only as a collection of components. A screen can pass individual design checks and still feel generic.

Ask:

1. Could this layout have been generated for almost any application by changing only the colors and text?
2. Does its navigation reflect the product's actual workflows, or a generic dashboard template?
3. Are elements arranged by information importance, or distributed into repetitive cards and grids?
4. Are there decorative elements that do not improve meaning, hierarchy, usability, or product identity?
5. Has every component defaulted to boxes and lines—or merely had its corners rounded—or does the composition use purposeful forms such as rings, arcs, circles, capsules, and carefully chosen rectangles where they help express the data?
6. Does every borrowed pattern feel native to this product's palette, typography, spacing, and overall composition rather than pasted on?
7. Does each shape match its component role, and have unlike components been allowed to differ where their jobs differ?
8. Does the content feel realistic for the product and its audience?
9. Do the interaction patterns suit the tasks users actually perform?
10. Does the interface have a distinctive visual identity without sacrificing usability or accessibility?
11. Does it continue to work with realistic content, empty states, errors, and different viewport sizes?
12. If this is a revision, were approved and unrelated components preserved while the requested target was changed?

If the interface feels generic, identify the underlying design decision responsible and revise the layout, hierarchy, content presentation, or interaction pattern. Do not try to solve generic design merely by changing colors, adding animation, increasing visual complexity, or adding decorative elements.

The objective is not novelty for its own sake. It is a coherent, purpose-built interface whose design follows from the product.

---

## Appendix: Quick Application Color Palette Generator

When you need to generate a palette from scratch, use this sequence:

1. **Name the product's emotional register** — precision, warmth, energy, calm, authority, playfulness
2. **Pick a dominant hue that fits** — not blue unless blue is the considered choice
3. **Set the background temperature** — slightly warm (add +2 to the red channel), slightly cool (add +2 to the blue), or tinted (a hint of the dominant hue at 3–5% saturation). Avoid pure neutral.
4. **Derive surfaces from the background** — surface is background lightened or darkened by 5–8% HSL lightness. Surface-raised is another 5–8% from surface.
5. **Place the accent** — must be distinctly readable on background, surface, and surface-raised; should contrast at a 4.5:1 ratio on background
6. **Derive accent-muted** — accent hue at 12–15% opacity
7. **Assign text** — text-primary near maximum contrast; text-secondary at 50–60% of primary's contrast
8. **Set semantic colors** — destructive near #EF4444, muted to fit the palette temperature; success near #22C55E, muted similarly. Adjust hue slightly to harmonize with the accent if needed.

Test every palette by placing all tokens side by side before using them in a component. A palette that looks wrong at the token level will always look wrong in the UI.