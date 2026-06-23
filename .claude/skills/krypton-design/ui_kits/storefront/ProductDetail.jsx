// Krypton storefront — Product detail screen.
function KrProductDetail({ product, onAdd, onNavigate, onOpenProduct }) {
  const { Button, Badge, Rating, IconButton, ProductCard } = window.KryptonDesignSystem_457463;
  const { products, fmt } = window.KR_DATA;
  const [qty, setQty] = React.useState(1);
  const related = products.filter((p) => p.category === product.category && p.id !== product.id).slice(0, 4);

  return (
    <div style={{ maxWidth: "var(--container-max)", margin: "0 auto", padding: "28px" }}>
      <div style={{ display: "flex", alignItems: "center", gap: 8, fontSize: 13, color: "var(--text-muted)", marginBottom: 18 }}>
        <button onClick={() => onNavigate({ name: "catalog" })} style={{ background: "none", border: "none", cursor: "pointer", color: "var(--text-muted)", fontFamily: "var(--font-sans)", fontSize: 13 }}>Catálogo</button>
        <i data-lucide="chevron-right" style={{ width: 14, height: 14 }} />
        <span>{product.category}</span>
        <i data-lucide="chevron-right" style={{ width: 14, height: 14 }} />
        <span style={{ color: "var(--text-strong)", fontWeight: 600 }}>{product.name}</span>
      </div>

      <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 40, alignItems: "start" }}>
        {/* Gallery */}
        <div>
          <div style={{ aspectRatio: "1 / 1", borderRadius: 20, background: "linear-gradient(160deg, var(--kr-gray-50), var(--kr-blue-50))",
            border: "1px solid var(--border-subtle)", display: "flex", alignItems: "center", justifyContent: "center", position: "relative" }}>
            {product.badge && <span style={{ position: "absolute", top: 18, left: 18 }}><Badge tone={product.badge.tone} solid>{product.badge.label}</Badge></span>}
            <i data-lucide={product.icon} style={{ width: 200, height: 200, color: "var(--kr-navy-700)", strokeWidth: 1 }} />
          </div>
          <div style={{ display: "flex", gap: 10, marginTop: 14 }}>
            {[0, 1, 2, 3].map((i) => (
              <div key={i} style={{ width: 72, height: 72, borderRadius: 12, background: "var(--surface-card)",
                border: i === 0 ? "2px solid var(--color-brand)" : "1px solid var(--border-subtle)",
                display: "flex", alignItems: "center", justifyContent: "center", cursor: "pointer" }}>
                <i data-lucide={product.icon} style={{ width: 30, height: 30, color: "var(--kr-gray-400)", strokeWidth: 1.4 }} />
              </div>
            ))}
          </div>
        </div>

        {/* Info */}
        <div>
          <span style={{ fontSize: 12, fontWeight: 700, letterSpacing: "0.1em", textTransform: "uppercase", color: "var(--color-brand)" }}>{product.category} · {product.brand}</span>
          <h1 style={{ fontSize: 30, fontWeight: 800, color: "var(--text-strong)", margin: "8px 0 12px", lineHeight: 1.15 }}>{product.name}</h1>
          <div style={{ display: "flex", alignItems: "center", gap: 12, marginBottom: 18 }}>
            <Rating value={product.rating} count={product.ratingCount} showValue />
            <span style={{ fontSize: 13, color: "var(--text-faint)" }}>·</span>
            <span style={{ fontSize: 13, color: "var(--kr-success)", fontWeight: 600, display: "inline-flex", alignItems: "center", gap: 5 }}>
              <i data-lucide="check-circle-2" style={{ width: 15, height: 15 }} /> {product.stock} en stock
            </span>
            <span style={{ fontSize: 13, color: "var(--text-faint)" }}>SKU {product.sku}</span>
          </div>

          <div style={{ display: "flex", alignItems: "baseline", gap: 12, marginBottom: 20 }}>
            <span style={{ fontSize: 38, fontWeight: 900, color: "var(--text-strong)", letterSpacing: "-0.02em" }}>{fmt(product.price)}</span>
            {product.oldPrice && <span style={{ fontSize: 18, color: "var(--text-faint)", textDecoration: "line-through" }}>{fmt(product.oldPrice)}</span>}
            {product.oldPrice && <Badge tone="sale" solid>Ahorra {fmt(product.oldPrice - product.price)}</Badge>}
          </div>

          <p style={{ fontSize: 15.5, color: "var(--text-body)", lineHeight: 1.65, marginBottom: 24 }}>{product.desc}</p>

          {/* Qty + actions */}
          <div style={{ display: "flex", alignItems: "center", gap: 14, marginBottom: 16 }}>
            <div style={{ display: "flex", alignItems: "center", border: "1.5px solid var(--border-default)", borderRadius: 10, height: 48 }}>
              <button onClick={() => setQty((q) => Math.max(1, q - 1))} style={{ width: 44, height: "100%", background: "none", border: "none", cursor: "pointer", color: "var(--text-body)", fontSize: 20 }}>−</button>
              <span style={{ width: 40, textAlign: "center", fontWeight: 700, color: "var(--text-strong)" }}>{qty}</span>
              <button onClick={() => setQty((q) => q + 1)} style={{ width: 44, height: "100%", background: "none", border: "none", cursor: "pointer", color: "var(--text-body)", fontSize: 20 }}>+</button>
            </div>
            <Button variant="cta" size="lg" iconLeft={<i data-lucide="shopping-cart" style={{ width: 19, height: 19 }} />} onClick={() => onAdd(product, qty)} style={{ flex: 1 }}>Agregar al carrito</Button>
            <IconButton icon="heart" label="Favorito" variant="outline" size="lg" />
          </div>

          {/* Reassurance */}
          <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 12, marginTop: 20 }}>
            {[["truck", "Envío gratis", "Entrega en 24-48h"], ["shield-check", "Garantía oficial", "12 meses"], ["refresh-cw", "Devolución", "30 días"], ["credit-card", "Cuotas", "Hasta 12 sin interés"]].map(([ic, t, s]) => (
              <div key={t} style={{ display: "flex", gap: 12, alignItems: "center", background: "var(--surface-card)", border: "1px solid var(--border-subtle)", borderRadius: 12, padding: "12px 14px" }}>
                <i data-lucide={ic} style={{ width: 22, height: 22, color: "var(--color-brand)" }} />
                <div style={{ lineHeight: 1.25 }}>
                  <div style={{ fontSize: 13.5, fontWeight: 600, color: "var(--text-strong)" }}>{t}</div>
                  <div style={{ fontSize: 12, color: "var(--text-muted)" }}>{s}</div>
                </div>
              </div>
            ))}
          </div>
        </div>
      </div>

      {/* Related */}
      {related.length > 0 && (
        <section style={{ marginTop: 56 }}>
          <h2 style={{ fontSize: 24, fontWeight: 800, color: "var(--text-strong)", margin: "0 0 18px" }}>También te puede interesar</h2>
          <div style={{ display: "grid", gridTemplateColumns: "repeat(4, 1fr)", gap: 18 }}>
            {related.map((p) => (
              <ProductCard key={p.id} {...p} onAdd={() => onAdd(p)} onClick={() => onOpenProduct(p)} />
            ))}
          </div>
        </section>
      )}
    </div>
  );
}
window.KrProductDetail = KrProductDetail;
