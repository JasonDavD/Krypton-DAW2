# Krypton Storefront — UI Kit

High-fidelity, interactive recreation of the **Krypton** e-commerce storefront, built on the design system's `styles.css` tokens and `components/core` primitives (loaded from `_ds_bundle.js`).

Open **`index.html`**. It is a fake-but-interactive click-through — no backend.

## Flows
- **Inicio (Home):** navy hero, category grid, featured products, Krypton Days promo band, weekly deals.
- **Catálogo:** sticky category + price-range sidebar, sort dropdown, responsive product grid, empty state.
- **Detalle de producto:** gallery, price/old-price, rating, stock, quantity stepper, add-to-cart, reassurance grid, related products.
- **Carrito:** line items with qty steppers, order summary (free-shipping threshold), empty state.
- **Cuenta:** login / register tabs with brand split-panel.
- **Global:** sticky translucent navbar with live search + cart badge; add-to-cart toast; navy footer.

## Architecture
| File | Role |
|---|---|
| `index.html` | Entry — loads React, Babel, Lucide, the DS bundle + the JSX files |
| `data.js` | Demo catalog (`window.KR_DATA` — products, categories, `fmt`) |
| `App.jsx` | Shell: route + cart + auth state, toast (`window.KrApp`) |
| `Navbar.jsx` / `Footer.jsx` | Chrome |
| `Home.jsx` / `Catalog.jsx` / `ProductDetail.jsx` / `Cart.jsx` / `Login.jsx` | Screens |

Components consumed from `window.KryptonDesignSystem_457463`: `Button`, `IconButton`, `Input`, `Select`, `Badge`, `Rating`, `ProductCard`, `Card`. Icons via Lucide (`lucide.createIcons()` re-run on each render).

## Notes / caveats
- **Product imagery** is represented with Lucide device icons over a soft brand gradient — the project shipped no real product photos. Swap `icon` for an `image` URL on `ProductCard` / detail gallery when real assets are available.
- Currency formatted as Soles (`S/`, es-PE) to match the Angular source (`currency:'PEN'`).
- This recreates the storefront's *customer* surface. The Angular app also has Admin / Orders / Reports areas (stubs in the codebase) — not built here; flag if needed.
