// Krypton storefront — app shell: routing + cart/auth state.
function KrApp() {
  const [route, setRoute] = React.useState({ name: "home" });
  const [cart, setCart] = React.useState([]);
  const [user, setUser] = React.useState(null);
  const [query, setQuery] = React.useState("");
  const [activeQuery, setActiveQuery] = React.useState("");
  const [toast, setToast] = React.useState(null);

  const navigate = (r) => { setRoute(r); window.scrollTo({ top: 0 }); };

  const addToCart = (product, qty = 1) => {
    setCart((c) => {
      const ex = c.find((it) => it.id === product.id);
      if (ex) return c.map((it) => (it.id === product.id ? { ...it, qty: it.qty + qty } : it));
      return [...c, { ...product, qty }];
    });
    setToast(product.name + " agregado al carrito");
    clearTimeout(window.__krToast);
    window.__krToast = setTimeout(() => setToast(null), 2200);
  };
  const setQty = (id, qty) => setCart((c) => c.map((it) => (it.id === id ? { ...it, qty } : it)));
  const removeItem = (id) => setCart((c) => c.filter((it) => it.id !== id));

  const openProduct = (p) => navigate({ name: "product", product: p });
  const runSearch = () => { setActiveQuery(query); navigate({ name: "catalog" }); };

  React.useEffect(() => { if (window.lucide) lucide.createIcons(); });

  const cartCount = cart.reduce((s, it) => s + it.qty, 0);

  let screen;
  if (route.name === "home") screen = <KrHome onNavigate={navigate} onAdd={addToCart} onOpenProduct={openProduct} />;
  else if (route.name === "catalog") screen = <KrCatalog initialCategory={route.category} query={activeQuery} onAdd={addToCart} onOpenProduct={openProduct} />;
  else if (route.name === "product") screen = <KrProductDetail product={route.product} onAdd={addToCart} onNavigate={navigate} onOpenProduct={openProduct} />;
  else if (route.name === "cart") screen = <KrCart items={cart} onQty={setQty} onRemove={removeItem} onNavigate={navigate} />;
  else if (route.name === "login") screen = <KrLogin onLogin={(email) => { setUser({ email }); navigate({ name: "home" }); }} onNavigate={navigate} />;

  return (
    <div style={{ minHeight: "100vh", display: "flex", flexDirection: "column", background: "var(--surface-page)" }}>
      <KrNavbar
        route={route} onNavigate={navigate} cartCount={cartCount}
        query={query} onQuery={setQuery} onSearch={runSearch}
        user={user} onLogout={() => setUser(null)}
      />
      <main style={{ flex: 1 }}>{screen}</main>
      {route.name !== "login" && <KrFooter />}

      {/* Toast */}
      {toast && (
        <div style={{ position: "fixed", bottom: 24, left: "50%", transform: "translateX(-50%)", zIndex: 100,
          background: "var(--kr-navy-800)", color: "#fff", padding: "13px 20px", borderRadius: 12,
          boxShadow: "var(--shadow-lg)", display: "flex", alignItems: "center", gap: 10, fontSize: 14, fontWeight: 500,
          animation: "krToastIn 240ms cubic-bezier(0.34,1.56,0.64,1)" }}>
          <i data-lucide="check-circle-2" style={{ width: 18, height: 18, color: "var(--kr-yellow-500)" }} />
          {toast}
        </div>
      )}
    </div>
  );
}
window.KrApp = KrApp;
