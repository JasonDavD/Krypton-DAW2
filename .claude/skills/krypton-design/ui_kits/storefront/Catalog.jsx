// Krypton storefront — Catalog screen (filter sidebar + product grid).
function KrCatalog({ initialCategory, query, onAdd, onOpenProduct }) {
  const { ProductCard, Select, Badge, Button } = window.KryptonDesignSystem_457463;
  const { products, categories } = window.KR_DATA;

  const [category, setCategory] = React.useState(initialCategory || null);
  const [sort, setSort] = React.useState("relevance");
  const [maxPrice, setMaxPrice] = React.useState(5000);

  let list = products.filter((p) => {
    if (category && p.category !== category) return false;
    if (query && !(p.name + " " + p.category + " " + p.brand).toLowerCase().includes(query.toLowerCase())) return false;
    if (p.price > maxPrice) return false;
    return true;
  });
  if (sort === "price-asc") list = [...list].sort((a, b) => a.price - b.price);
  if (sort === "price-desc") list = [...list].sort((a, b) => b.price - a.price);
  if (sort === "rating") list = [...list].sort((a, b) => b.rating - a.rating);

  const catRow = (label, value) => {
    const active = category === value;
    return (
      <button key={label} onClick={() => setCategory(value)}
        style={{ display: "flex", alignItems: "center", justifyContent: "space-between", width: "100%",
          background: active ? "var(--kr-blue-50)" : "transparent", border: "none", borderRadius: 8,
          padding: "9px 12px", cursor: "pointer", fontFamily: "var(--font-sans)", fontSize: 14,
          fontWeight: active ? 700 : 500, color: active ? "var(--color-brand)" : "var(--text-body)" }}>
        <span>{label}</span>
        <span style={{ fontSize: 12, color: "var(--text-faint)" }}>{value ? products.filter((p) => p.category === value).length : products.length}</span>
      </button>
    );
  };

  return (
    <div style={{ maxWidth: "var(--container-max)", margin: "0 auto", padding: "28px" }}>
      <div style={{ display: "flex", alignItems: "center", gap: 8, fontSize: 13, color: "var(--text-muted)", marginBottom: 8 }}>
        <span>Inicio</span><i data-lucide="chevron-right" style={{ width: 14, height: 14 }} /><span style={{ color: "var(--text-strong)", fontWeight: 600 }}>{category || "Catálogo"}</span>
      </div>
      <h1 style={{ fontSize: 32, fontWeight: 800, color: "var(--text-strong)", margin: "0 0 20px" }}>{category || "Todos los productos"}</h1>

      <div style={{ display: "grid", gridTemplateColumns: "248px 1fr", gap: 28, alignItems: "start" }}>
        {/* Sidebar */}
        <aside style={{ position: "sticky", top: 88, background: "var(--surface-card)", border: "1px solid var(--border-subtle)", borderRadius: 16, padding: 18 }}>
          <div style={{ fontSize: 12, fontWeight: 700, letterSpacing: "0.1em", textTransform: "uppercase", color: "var(--text-muted)", marginBottom: 10 }}>Categorías</div>
          <div style={{ display: "flex", flexDirection: "column", gap: 2, marginBottom: 18 }}>
            {catRow("Todas", null)}
            {categories.map((c) => catRow(c.name, c.name))}
          </div>
          <div style={{ borderTop: "1px solid var(--border-subtle)", paddingTop: 16 }}>
            <div style={{ fontSize: 12, fontWeight: 700, letterSpacing: "0.1em", textTransform: "uppercase", color: "var(--text-muted)", marginBottom: 12 }}>Precio máximo</div>
            <input type="range" min="100" max="5000" step="100" value={maxPrice} onChange={(e) => setMaxPrice(Number(e.target.value))}
              style={{ width: "100%", accentColor: "var(--color-brand)" }} />
            <div style={{ display: "flex", justifyContent: "space-between", fontSize: 13, color: "var(--text-body)", marginTop: 6 }}>
              <span>S/ 100</span><span style={{ fontWeight: 700, color: "var(--text-strong)" }}>S/ {maxPrice.toLocaleString("es-PE")}</span>
            </div>
          </div>
        </aside>

        {/* Grid */}
        <div>
          <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", marginBottom: 16 }}>
            <span style={{ fontSize: 14, color: "var(--text-muted)" }}>{list.length} producto{list.length !== 1 ? "s" : ""}</span>
            <div style={{ width: 220 }}>
              <Select value={sort} onChange={(e) => setSort(e.target.value)}
                options={[{ value: "relevance", label: "Más relevantes" }, { value: "price-asc", label: "Precio: menor a mayor" }, { value: "price-desc", label: "Precio: mayor a menor" }, { value: "rating", label: "Mejor valorados" }]} />
            </div>
          </div>

          {list.length === 0 ? (
            <div style={{ textAlign: "center", padding: "64px 20px", color: "var(--text-muted)" }}>
              <i data-lucide="package-search" style={{ width: 48, height: 48, color: "var(--kr-gray-300)" }} />
              <p style={{ marginTop: 12, fontSize: 16 }}>No se encontraron productos</p>
              <Button variant="secondary" onClick={() => { setCategory(null); setMaxPrice(5000); }}>Limpiar filtros</Button>
            </div>
          ) : (
            <div style={{ display: "grid", gridTemplateColumns: "repeat(3, 1fr)", gap: 18 }}>
              {list.map((p) => (
                <ProductCard key={p.id} {...p} onAdd={() => onAdd(p)} onClick={() => onOpenProduct(p)} />
              ))}
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
window.KrCatalog = KrCatalog;
