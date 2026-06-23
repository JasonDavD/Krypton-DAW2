---
name: krypton-design
description: Use this skill to generate well-branded interfaces and assets for Krypton (tech e-commerce — dispositivos y componentes tecnológicos), either for production or throwaway prototypes/mocks/etc. Contains essential design guidelines, colors, type, fonts, assets, and UI kit components for prototyping.
user-invocable: true
---

Read the README.md file within this skill, and explore the other available files.
If creating visual artifacts (slides, mocks, throwaway prototypes, etc), copy assets out and create static HTML files for the user to view. If working on production code, you can copy assets and read the rules here to become an expert in designing with this brand.
If the user invokes this skill without any other guidance, ask them what they want to build or design, ask some questions, and act as an expert designer who outputs HTML artifacts _or_ production code, depending on the need.

## Quick reference
- **Brand:** Krypton — fresh, modern, youthful, technological. Spanish (es-PE), prices in Soles (`S/`).
- **Logo:** `assets/logo/Krypton-{white,navy,blue,orange}.svg` (Kanit Black Italic wordmark with paper-plane "o").
- **Color:** Navy `#03275A` (ink/bands), Blue `#1A7DD7` (primary action/links), Orange `#F37402` (CTA/energy, gets a glow), Yellow `#F2B809` + Red-orange `#F34605` (highlights/sale). Light-first surfaces. No purple, no purple gradients.
- **Type:** Kanit everywhere (bundled in `assets/fonts`). Headlines ExtraBold/Black, often italic; body Regular 16px.
- **Icons:** Lucide (CDN), 2px outline, `currentColor`. No emoji.
- **Tokens:** link `styles.css` → CSS custom properties (`--color-brand`, `--action-cta`, `--surface-card`, `--radius-md`, `--shadow-cta`, …). See `readme.md` for the full VISUAL FOUNDATIONS + CONTENT FUNDAMENTALS.
- **Components:** `components/core/` (Button, IconButton, Input, Select, Badge, Card, Rating, ProductCard).
- **Reference build:** `ui_kits/storefront/` — full interactive storefront.
