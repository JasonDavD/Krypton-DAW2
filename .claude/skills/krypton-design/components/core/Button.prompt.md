Krypton's primary button — use for any clickable action; reach for `variant="cta"` (orange) only for the single most important action on a screen.

```jsx
<Button variant="cta" size="lg" iconRight={<i data-lucide="arrow-right" />}>
  Comprar ahora
</Button>
<Button variant="primary">Agregar al carrito</Button>
<Button variant="secondary">Ver detalle</Button>
<Button variant="ghost" size="sm">Cancelar</Button>
```

Variants: `primary` (blue, default actions) · `cta` (orange + glow, hero action) · `secondary` (outline) · `ghost` (text) · `danger`. Sizes `sm|md|lg`. Props: `block`, `disabled`, `iconLeft`, `iconRight`.
