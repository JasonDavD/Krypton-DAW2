# Krypton — Design System

**Krypton** es una tienda de e-commerce de electrónica: dispositivos y componentes tecnológicos. La marca proyecta una identidad **fresca, moderna, juvenil y tecnológica**. Mercado hispanohablante (Perú — precios en Soles, `PEN`).

This design system encodes Krypton's visual identity (logo, color, type, motion), reusable UI primitives, and a high-fidelity recreation of the storefront so design agents can build on-brand interfaces and assets.

---

## Sources

- **Codebase** (read-only, mounted locally): `src/` — an **Angular 17+ standalone** e-commerce SPA. Key feature areas under `src/app/features/`: `catalog/` (grid, filter, product card, product detail), `cart/`, `auth/` (login, register), `orders/`, `admin/`, `reports/`. Layout in `src/app/layout/` (navbar, footer). Data contracts in `src/app/models/` (`product.model.ts`, `cart.model.ts`, `order.model.ts`, `auth.model.ts`, `report.model.ts`). Backend mirrors Spring Boot DTOs.
  - ⚠️ The scaffold currently ships a **dark + magenta** placeholder theme (`#12122a` / `#e040fb`). That is **NOT the brand** — it is generic boilerplate. This design system replaces it with Krypton's real corporate identity.
- **Brand assets** (`uploads/`): `Logo-Krypton.svg` (white wordmark), `colores corporativos.jpg` (corporate palette), `Krypton logo repeticion.jpg` (logo on brand colors), `Kanit-*.ttf` (full Kanit family).
- **Aesthetic references** (`uploads/Referencia 1.jpg`, `Referencia 2.jpg`): third-party tech sites (Nexora, NovaLyne) provided as *mood/direction* inspiration — light, clean, energetic, rounded cards, bold display type, accent-driven CTAs. Not Krypton screens; used only for vibe.

---

## Brand at a glance

- **Wordmark:** "Krypton" set in **Kanit Black Italic**, all lowercase except the K. The dotless **"o"** carries a paper-plane / send glyph — a nod to delivery + tech momentum. Wordmark appears in white, navy, blue, or orange on solid brand fields. See `assets/logo/`.
- **Palette:** Navy `#03275A` (ink/depth), Blue `#1A7DD7` (primary action), Yellow `#F2B809` + Orange `#F37402` + Red-orange `#F34605` (energy/CTA/highlights).
- **Type:** **Kanit** throughout — geometric, slightly condensed, energetic. Headlines lean heavy (800/900) and italic to echo the wordmark.
- **Surfaces:** Light by default (`#f5f7fa` page, white cards). Navy used for high-impact bands and footers.

---

## CONTENT FUNDAMENTALS

**Language.** Spanish (es-PE). Copy is concise, direct, and warm — youthful but not slangy.

**Voice & person.** Speaks **to the customer with "tú/usted-neutral imperative"** — action verbs lead: *"Iniciar sesión", "Registrarse", "Volver al catálogo", "Reintentar", "Buscar por nombre…"*. UI labels are short noun phrases: *"Catálogo", "Carrito", "Mis pedidos", "Categoría"*. The brand refers to itself in third person in legal/footer copy: *"© Krypton E-commerce. Todos los derechos reservados."*

**Casing.** Sentence case for body and most labels (*"Iniciar sesión"*, not "Iniciar Sesión"). Display headlines may be set in caps for impact (reference style), but default UI is sentence case. Eyebrows/overlines are UPPERCASE with wide tracking.

**Tone examples (from product):**
- Empty state: *"No se encontraron productos"*
- Loading: *"Cargando…"*
- Error: *"No se pudieron cargar los productos"* + *"Reintentar"*
- Placeholder/stub: *"Próximamente — módulo de carrito en desarrollo."*
- Stock line: *"Stock: 12"*

**Marketing voice** (for heroes/CTAs, in the energetic reference spirit): punchy two/three-word value props + a verb-led CTA. e.g. *"Tecnología que enciende"*, *"Explora el catálogo"*, *"Arma tu setup"*. Keep it benefit-first, never jargon-heavy.

**Numbers & currency.** Prices in Soles with symbol + 2 decimals: `S/ 1,299.00` (Angular `currency:'PEN':'symbol':'1.2-2'`). SKUs uppercase alphanumeric.

**Emoji.** Not used in product UI or brand copy. Communicate energy through color, type weight, and motion instead.

---

## VISUAL FOUNDATIONS

**Color usage.** Light-first. Navy `#03275A` is the ink and the "hero/footer" field color. **Blue `#1A7DD7` is the primary interactive color** (links, primary buttons, focus, active states). **Orange `#F37402` is the CTA/energy color** — reserved for the single most important action on a view and for highlights; it always has the brand "glow" shadow. Yellow `#F2B809` is a sparing accent (badges, ratings, promo flags). Red-orange `#F34605` doubles as the "hot/sale" accent. Greens/reds for success/danger are tuned to sit beside the brand without clashing. Avoid purple/magenta entirely (it was the placeholder, not the brand). Never use bluish-purple gradients.

**Typography.** One family, Kanit. Display/H1–H2 use ExtraBold/Black (800/900) with tight tracking (`-0.02em`); hero treatments may be italic. Body is Regular 400 at 16px, line-height 1.5. Eyebrows are 12px Bold uppercase, `0.12em` tracking, in brand blue. The condensed-ish proportions let big headlines stay punchy without overflowing.

**Spacing & layout.** 4px base scale (`--space-*`). Content max-width 1200px, centered. Generous vertical rhythm between sections (48–96px). Grids use CSS `repeat(auto-fill, minmax(...))` for product listings. Fixed sticky navbar (68px).

**Backgrounds.** Predominantly flat light (`#f5f7fa`) and white cards — clean, not noisy. High-impact moments (hero, feature band, footer, CTA blocks) use **solid navy** fields. Subtle brand flourish allowed: soft navy/blue radial glow behind hero product imagery; the logo "repetition" pattern (tiled wordmark) may be used as a faint texture on navy. **No** heavy photographic textures, no grain, no purple gradients. When gradient is used, keep it within one brand hue (navy→deeper-navy, or orange→red-orange on CTAs) — never rainbow/purple.

**Imagery.** Product photography on clean/transparent backgrounds (e-commerce style), cool-neutral lighting. Lifestyle/hero imagery is modern and tech-forward. Float products over soft glows rather than boxed-in.

**Corner radii.** Friendly-but-precise. Cards `12px` (`--radius-md`), inputs/buttons `8px`, large feature panels `18–28px`, pills/chips fully rounded. Not ultra-soft blobby; reads engineered.

**Cards.** White surface, `12px` radius, `1px` subtle border (`--border-subtle`) **or** soft shadow (`--shadow-sm`), never both heavy. On hover, product cards lift: shadow grows to `--shadow-md` and translateY(-2px); border tints to brand blue. No colored-left-border cards.

**Shadows.** Soft and **navy-tinted**, never harsh black. `--shadow-xs → lg` for elevation. CTAs get a warm **orange glow** (`--shadow-cta`). Focus ring is a 3px translucent blue halo (`--shadow-focus`).

**Borders.** 1px, cool gray (`--border-subtle/-default`). Focus borders switch to brand blue plus the focus halo.

**Motion.** Quick and confident. Transitions 120–360ms with an ease-out (`cubic-bezier(0.22,1,0.36,1)`). Hover/press are snappy. A subtle **bounce** ease is reserved for playful confirmations (add-to-cart). Entrances fade+rise. No long, floaty animations; respect `prefers-reduced-motion`.

**Hover states.** Links/blue actions darken to `--action-primary-hover`. CTAs darken orange + grow glow. Cards lift. Ghost/secondary buttons fill with a faint brand tint.

**Press states.** Color deepens one more step (`--action-*-press`) and element scales down slightly (`scale(0.97)`) — tactile, fast.

**Transparency & blur.** Used sparingly — sticky navbar may use a translucent white with backdrop blur on scroll; modal scrims use navy at ~55% opacity. Otherwise surfaces are solid.

---

## ICONOGRAPHY

The Angular scaffold ships **no icon library** — it uses Unicode glyphs inline (e.g. carousel arrows `‹` `›` via `&#8249;`/`&#8250;`). For this design system we standardize on **[Lucide](https://lucide.dev)** (loaded from CDN) as the icon set:

- **Why Lucide:** clean, geometric, **2px stroke**, rounded line caps — matches Krypton's modern/precise/youthful character and pairs well with Kanit's geometry.
- **Style rules:** outline (stroke) icons only, 2px stroke, `currentColor` so they inherit text color. Default sizes 16/20/24px. Use brand blue for interactive icons, navy for neutral, orange only for emphasis.
- **Usage:** `<script src="https://unpkg.com/lucide@latest"></script>` then `<i data-lucide="shopping-cart"></i>` + `lucide.createIcons()`. Common glyphs: `shopping-cart`, `search`, `user`, `heart`, `package`, `truck`, `chevron-left/right`, `star`, `filter`, `shield-check`, `zap`.
- **Emoji:** never used as iconography.
- ⚠️ **Substitution flag:** Lucide is a *chosen* standard, not extracted from the codebase (the codebase had none). If the brand has an official icon set, provide it and we'll swap.

The brand mark itself (the paper-plane in the logo "o") can serve as a favicon/app-icon motif.

---

## VISUAL SUBSTITUTIONS & CAVEATS

- **Fonts:** Kanit `.ttf` files were provided and are bundled directly (Google Fonts OFL family) — no substitution.
- **Icons:** Lucide chosen as standard (codebase shipped none) — see above.
- **Theme reinterpretation:** The product's existing dark/magenta CSS was treated as placeholder and replaced with the corporate identity. If a dark mode is officially wanted, flag it and we'll add a navy-based dark theme.

---

## Index / Manifest

**Root**
- `styles.css` — global entry (only `@import`s). Consumers link this.
- `readme.md` — this guide.
- `SKILL.md` — Agent-Skill wrapper.

**`tokens/`** — `fonts.css` (@font-face Kanit), `colors.css`, `typography.css`, `spacing.css` (spacing/radii/shadows/motion), `base.css` (element defaults + `.kr-eyebrow`, `.kr-wordmark`).

**`assets/`** — `logo/` (Krypton white/navy/blue/orange SVGs), `fonts/` (Kanit ttf).

**`components/core/`** — reusable React primitives: `Button`, `IconButton`, `Input`, `Select`, `Badge`, `Card`, `ProductCard`, `Tag`, `Rating`. Each has `.jsx`, `.d.ts`, `.prompt.md` + a directory `@dsCard`.

**`ui_kits/storefront/`** — high-fidelity Krypton storefront recreation (home, catalog, product detail, cart, login) as interactive click-through. `index.html` is the entry.

**Design System tab** — foundation specimen cards live in `guidelines/` (colors, type, spacing, brand) plus component cards.
