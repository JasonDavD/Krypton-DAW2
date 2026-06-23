// Krypton storefront — Home screen.
function KrHome({ onNavigate, onAdd, onOpenProduct }) {
  const { Button, ProductCard, Badge } = window.KryptonDesignSystem_457463;
  const { products, categories } = window.KR_DATA;
  const featured = products.slice(0, 4);
  const deals = products.filter((p) => p.oldPrice).slice(0, 4);

  return (
    <div>
      {/* HERO — navy band */}
      <section style={{ background: "var(--surface-inverse)", color: "#fff", position: "relative", overflow: "hidden" }}>
        <div style={{ position: "absolute", right: -120, top: -80, width: 460, height: 460, borderRadius: "50%",
          background: "radial-gradient(circle, rgba(26,125,215,0.55), transparent 65%)" }} />
        <div style={{ position: "absolute", right: 120, bottom: -160, width: 380, height: 380, borderRadius: "50%",
          background: "radial-gradient(circle, rgba(243,116,2,0.35), transparent 65%)" }} />
        <div style={{ maxWidth: "var(--container-max)", margin: "0 auto", padding: "72px 28px", position: "relative",
          display: "grid", gridTemplateColumns: "1.1fr 0.9fr", gap: 40, alignItems: "center" }}>
          <div>
            <span style={{ display: "inline-flex", alignItems: "center", gap: 8, fontSize: 12, fontWeight: 700,
              letterSpacing: "0.14em", textTransform: "uppercase", color: "var(--kr-yellow-500)", marginBottom: 18 }}>
              <i data-lucide="zap" style={{ width: 15, height: 15 }} /> Tecnología de última generación
            </span>
            <h1 style={{ fontSize: 60, fontWeight: 900, fontStyle: "italic", lineHeight: 0.98, letterSpacing: "-0.02em", color: "#fff", margin: "0 0 18px" }}>
              Tecnología<br />que <span style={{ color: "var(--kr-orange-500)" }}>enciende</span>
            </h1>
            <p style={{ fontSize: 18, color: "var(--kr-gray-300)", maxWidth: 440, margin: "0 0 28px", lineHeight: 1.55 }}>
              Dispositivos y componentes para armar tu setup ideal. Envío a todo el Perú y garantía oficial.
            </p>
            <div style={{ display: "flex", gap: 12 }}>
              <Button variant="cta" size="lg" iconRight={<i data-lucide="arrow-right" style={{ width: 18, height: 18 }} />} onClick={() => onNavigate({ name: "catalog" })}>Explorar catálogo</Button>
              <Button variant="secondary" size="lg" style={{ background: "transparent", color: "#fff", borderColor: "rgba(255,255,255,0.3)" }} onClick={() => onNavigate({ name: "catalog", category: "Gaming" })}>Ver ofertas</Button>
            </div>
            <div style={{ display: "flex", gap: 28, marginTop: 36 }}>
              {[["truck", "Envío gratis", "desde S/ 199"], ["shield-check", "Garantía", "oficial 12 meses"], ["credit-card", "Cuotas", "sin interés"]].map(([ic, t, s]) => (
                <div key={t} style={{ display: "flex", alignItems: "center", gap: 10 }}>
                  <i data-lucide={ic} style={{ width: 22, height: 22, color: "var(--kr-blue-500)" }} />
                  <div style={{ lineHeight: 1.2 }}>
                    <div style={{ fontSize: 14, fontWeight: 600, color: "#fff" }}>{t}</div>
                    <div style={{ fontSize: 12, color: "var(--kr-gray-400)" }}>{s}</div>
                  </div>
                </div>
              ))}
            </div>
          </div>
          {/* Hero product tile */}
          <div style={{ display: "flex", justifyContent: "center" }}>
            <div style={{ width: 320, height: 320, borderRadius: 28, background: "linear-gradient(160deg, rgba(255,255,255,0.10), rgba(255,255,255,0.02))",
              border: "1px solid rgba(255,255,255,0.14)", display: "flex", alignItems: "center", justifyContent: "center", position: "relative", boxShadow: "var(--shadow-lg)" }}>
              <i data-lucide="laptop" style={{ width: 150, height: 150, color: "#fff", strokeWidth: 1 }} />
              <span style={{ position: "absolute", top: 18, left: 18 }}><Badge tone="cta" solid>Más vendido</Badge></span>
              <div style={{ position: "absolute", bottom: 18, left: 18, right: 18, background: "rgba(255,255,255,0.95)", borderRadius: 14, padding: "12px 14px", display: "flex", justifyContent: "space-between", alignItems: "center" }}>
                <div style={{ fontSize: 13, fontWeight: 600, color: "var(--text-strong)" }}>UltraBook 14" i7</div>
                <div style={{ fontSize: 17, fontWeight: 800, color: "var(--text-strong)" }}>S/ 4,299</div>
              </div>
            </div>
          </div>
        </div>
      </section>

      <div style={{ maxWidth: "var(--container-max)", margin: "0 auto", padding: "0 28px" }}>
        {/* Categories */}
        <section style={{ marginTop: 44 }}>
          <h2 style={{ fontSize: 24, fontWeight: 800, color: "var(--text-strong)", margin: "0 0 18px" }}>Compra por categoría</h2>
          <div style={{ display: "grid", gridTemplateColumns: "repeat(8, 1fr)", gap: 12 }}>
            {categories.map((c) => (
              <button key={c.name} onClick={() => onNavigate({ name: "catalog", category: c.name })}
                style={{ background: "var(--surface-card)", border: "1px solid var(--border-subtle)", borderRadius: 14,
                  padding: "18px 8px", display: "flex", flexDirection: "column", alignItems: "center", gap: 10, cursor: "pointer",
                  fontFamily: "var(--font-sans)", transition: "all var(--dur-base) var(--ease-out)" }}
                onMouseEnter={(e) => { e.currentTarget.style.borderColor = "var(--kr-blue-100)"; e.currentTarget.style.boxShadow = "var(--shadow-sm)"; e.currentTarget.style.transform = "translateY(-2px)"; }}
                onMouseLeave={(e) => { e.currentTarget.style.borderColor = "var(--border-subtle)"; e.currentTarget.style.boxShadow = "none"; e.currentTarget.style.transform = "none"; }}>
                <span style={{ width: 46, height: 46, borderRadius: 12, background: "var(--kr-blue-50)", display: "flex", alignItems: "center", justifyContent: "center" }}>
                  <i data-lucide={c.icon} style={{ width: 22, height: 22, color: "var(--color-brand)" }} />
                </span>
                <span style={{ fontSize: 12.5, fontWeight: 600, color: "var(--text-strong)", textAlign: "center" }}>{c.name}</span>
              </button>
            ))}
          </div>
        </section>

        {/* Featured */}
        <Section title="Destacados" action="Ver todo" onAction={() => onNavigate({ name: "catalog" })}>
          {featured.map((p) => (
            <ProductCard key={p.id} {...p} onAdd={() => onAdd(p)} onClick={() => onOpenProduct(p)} />
          ))}
        </Section>

        {/* Feature band */}
        <section style={{ marginTop: 48, background: "linear-gradient(110deg, var(--kr-orange-500), var(--kr-redorange-500))",
          borderRadius: 24, padding: "36px 40px", display: "flex", alignItems: "center", justifyContent: "space-between", gap: 24, color: "#fff", boxShadow: "var(--shadow-cta)" }}>
          <div>
            <div style={{ fontSize: 12, fontWeight: 700, letterSpacing: "0.14em", textTransform: "uppercase", opacity: 0.9 }}>Krypton Days</div>
            <h2 style={{ fontSize: 34, fontWeight: 900, fontStyle: "italic", color: "#fff", margin: "8px 0 6px" }}>Hasta 20% en gaming</h2>
            <p style={{ margin: 0, fontSize: 16, opacity: 0.92 }}>Mouse, teclados, controles y más. Por tiempo limitado.</p>
          </div>
          <Button variant="secondary" size="lg" style={{ background: "#fff", color: "var(--kr-redorange-600)", borderColor: "#fff" }} onClick={() => onNavigate({ name: "catalog", category: "Gaming" })}>Ver ofertas</Button>
        </section>

        {/* Deals */}
        <Section title="Ofertas de la semana" action="Ver todo" onAction={() => onNavigate({ name: "catalog" })}>
          {deals.map((p) => (
            <ProductCard key={p.id} {...p} onAdd={() => onAdd(p)} onClick={() => onOpenProduct(p)} />
          ))}
        </Section>
      </div>
    </div>
  );
}

function Section({ title, action, onAction, children }) {
  return (
    <section style={{ marginTop: 48 }}>
      <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", marginBottom: 18 }}>
        <h2 style={{ fontSize: 24, fontWeight: 800, color: "var(--text-strong)", margin: 0 }}>{title}</h2>
        {action && <button onClick={onAction} style={{ background: "none", border: "none", cursor: "pointer", color: "var(--color-brand)", fontFamily: "var(--font-sans)", fontWeight: 600, fontSize: 14, display: "inline-flex", alignItems: "center", gap: 4 }}>{action} <i data-lucide="chevron-right" style={{ width: 16, height: 16 }} /></button>}
      </div>
      <div style={{ display: "grid", gridTemplateColumns: "repeat(4, 1fr)", gap: 18 }}>{children}</div>
    </section>
  );
}
window.KrHome = KrHome;
