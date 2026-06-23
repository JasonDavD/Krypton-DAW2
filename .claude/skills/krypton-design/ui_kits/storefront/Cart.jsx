// Krypton storefront — Cart screen.
function KrCart({ items, onQty, onRemove, onNavigate }) {
  const { Button, IconButton } = window.KryptonDesignSystem_457463;
  const { fmt } = window.KR_DATA;
  const subtotal = items.reduce((s, it) => s + it.price * it.qty, 0);
  const shipping = subtotal > 199 || subtotal === 0 ? 0 : 19.9;
  const total = subtotal + shipping;

  if (items.length === 0) {
    return (
      <div style={{ maxWidth: 560, margin: "0 auto", padding: "80px 28px", textAlign: "center" }}>
        <span style={{ width: 84, height: 84, borderRadius: "50%", background: "var(--kr-blue-50)", display: "inline-flex", alignItems: "center", justifyContent: "center" }}>
          <i data-lucide="shopping-cart" style={{ width: 38, height: 38, color: "var(--color-brand)" }} />
        </span>
        <h1 style={{ fontSize: 26, fontWeight: 800, color: "var(--text-strong)", margin: "20px 0 8px" }}>Tu carrito está vacío</h1>
        <p style={{ color: "var(--text-muted)", marginBottom: 22 }}>Descubre nuestra tecnología y arma tu setup.</p>
        <Button variant="cta" size="lg" onClick={() => onNavigate({ name: "catalog" })}>Explorar catálogo</Button>
      </div>
    );
  }

  return (
    <div style={{ maxWidth: "var(--container-max)", margin: "0 auto", padding: "28px" }}>
      <h1 style={{ fontSize: 32, fontWeight: 800, color: "var(--text-strong)", margin: "0 0 22px" }}>Carrito ({items.reduce((s, it) => s + it.qty, 0)})</h1>
      <div style={{ display: "grid", gridTemplateColumns: "1fr 360px", gap: 28, alignItems: "start" }}>
        {/* Lines */}
        <div style={{ display: "flex", flexDirection: "column", gap: 12 }}>
          {items.map((it) => (
            <div key={it.id} style={{ display: "flex", gap: 16, alignItems: "center", background: "var(--surface-card)", border: "1px solid var(--border-subtle)", borderRadius: 14, padding: 14 }}>
              <div style={{ width: 84, height: 84, flex: "none", borderRadius: 12, background: "linear-gradient(160deg, var(--kr-gray-50), var(--kr-blue-50))", display: "flex", alignItems: "center", justifyContent: "center" }}>
                <i data-lucide={it.icon} style={{ width: 40, height: 40, color: "var(--kr-navy-700)", strokeWidth: 1.4 }} />
              </div>
              <div style={{ flex: 1, minWidth: 0 }}>
                <div style={{ fontSize: 11, fontWeight: 700, letterSpacing: "0.08em", textTransform: "uppercase", color: "var(--color-brand)" }}>{it.category}</div>
                <div style={{ fontSize: 15, fontWeight: 600, color: "var(--text-strong)", margin: "3px 0" }}>{it.name}</div>
                <div style={{ fontSize: 12.5, color: "var(--text-muted)" }}>SKU {it.sku}</div>
              </div>
              <div style={{ display: "flex", alignItems: "center", border: "1.5px solid var(--border-default)", borderRadius: 9, height: 40 }}>
                <button onClick={() => onQty(it.id, Math.max(1, it.qty - 1))} style={{ width: 36, height: "100%", background: "none", border: "none", cursor: "pointer", fontSize: 18, color: "var(--text-body)" }}>−</button>
                <span style={{ width: 32, textAlign: "center", fontWeight: 700, color: "var(--text-strong)" }}>{it.qty}</span>
                <button onClick={() => onQty(it.id, it.qty + 1)} style={{ width: 36, height: "100%", background: "none", border: "none", cursor: "pointer", fontSize: 18, color: "var(--text-body)" }}>+</button>
              </div>
              <div style={{ width: 110, textAlign: "right", fontSize: 16, fontWeight: 800, color: "var(--text-strong)" }}>{fmt(it.price * it.qty)}</div>
              <IconButton icon="trash-2" label="Quitar" variant="ghost" onClick={() => onRemove(it.id)} />
            </div>
          ))}
        </div>

        {/* Summary */}
        <aside style={{ position: "sticky", top: 88, background: "var(--surface-card)", border: "1px solid var(--border-subtle)", borderRadius: 16, padding: 22 }}>
          <h3 style={{ fontSize: 18, fontWeight: 700, color: "var(--text-strong)", margin: "0 0 16px" }}>Resumen</h3>
          <Row label="Subtotal" value={fmt(subtotal)} />
          <Row label="Envío" value={shipping === 0 ? "Gratis" : fmt(shipping)} accent={shipping === 0} />
          {subtotal <= 199 && <p style={{ fontSize: 12.5, color: "var(--text-muted)", margin: "4px 0 0" }}>Te faltan {fmt(200 - subtotal)} para envío gratis.</p>}
          <div style={{ borderTop: "1px solid var(--border-subtle)", margin: "16px 0", paddingTop: 16, display: "flex", justifyContent: "space-between", alignItems: "baseline" }}>
            <span style={{ fontSize: 16, fontWeight: 700, color: "var(--text-strong)" }}>Total</span>
            <span style={{ fontSize: 26, fontWeight: 900, color: "var(--text-strong)", letterSpacing: "-0.02em" }}>{fmt(total)}</span>
          </div>
          <Button variant="cta" size="lg" block iconRight={<i data-lucide="arrow-right" style={{ width: 18, height: 18 }} />}>Finalizar compra</Button>
          <Button variant="ghost" block style={{ marginTop: 8 }} onClick={() => onNavigate({ name: "catalog" })}>Seguir comprando</Button>
          <div style={{ display: "flex", alignItems: "center", justifyContent: "center", gap: 8, marginTop: 14, color: "var(--text-muted)", fontSize: 12.5 }}>
            <i data-lucide="lock" style={{ width: 14, height: 14 }} /> Pago 100% seguro
          </div>
        </aside>
      </div>
    </div>
  );
}

function Row({ label, value, accent }) {
  return (
    <div style={{ display: "flex", justifyContent: "space-between", fontSize: 14, padding: "5px 0", color: "var(--text-body)" }}>
      <span>{label}</span>
      <span style={{ fontWeight: 600, color: accent ? "var(--kr-success)" : "var(--text-strong)" }}>{value}</span>
    </div>
  );
}
window.KrCart = KrCart;
