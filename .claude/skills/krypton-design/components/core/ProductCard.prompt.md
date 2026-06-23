The storefront's signature product tile — image over a soft brand gradient, category eyebrow, clamped name, price in Soles, optional rating/sale badge and orange add-to-cart. Needs `lucide.createIcons()` after mount.

```jsx
<ProductCard
  name="Audífonos Krypton Pulse X"
  category="Audio"
  price={349.9} oldPrice={429.9}
  badge={{ label: "-20%", tone: "sale" }}
  rating={4.5} ratingCount={128}
  image="/img/audifonos.png"
  onAdd={() => addToCart(id)}
  onClick={() => goToProduct(id)}
/>
```

Composes `Badge` + `Rating`. Lay out in a `repeat(auto-fill, minmax(220px, 1fr))` grid.
