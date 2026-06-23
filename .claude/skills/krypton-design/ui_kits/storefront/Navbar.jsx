// Krypton storefront — top navigation bar.
function KrNavbar({ route, onNavigate, cartCount, query, onQuery, onSearch, user, onLogout }) {
  const { IconButton } = window.KryptonDesignSystem_457463;
  const link = (label, to) => {
    const active = route.name === to;
    return (
      <button
        onClick={() => onNavigate({ name: to })}
        style={{
          background: "none", border: "none", cursor: "pointer",
          fontFamily: "var(--font-sans)", fontSize: 15, fontWeight: active ? 700 : 500,
          color: active ? "var(--color-brand)" : "var(--text-body)",
          padding: "6px 2px", position: "relative",
        }}
      >
        {label}
        {active && <span style={{ position: "absolute", left: 0, right: 0, bottom: -2, height: 3, borderRadius: 3, background: "var(--color-accent)" }} />}
      </button>
    );
  };

  return (
    <header style={{
      position: "sticky", top: 0, zIndex: 50, height: "var(--navbar-h)",
      background: "rgba(255,255,255,0.88)", backdropFilter: "blur(10px)",
      borderBottom: "1px solid var(--border-subtle)",
      display: "flex", alignItems: "center", gap: 24,
      padding: "0 28px",
    }}>
      <img
        src="../../assets/logo/Krypton-navy.svg"
        alt="Krypton" style={{ height: 26, cursor: "pointer" }}
        onClick={() => onNavigate({ name: "home" })}
      />

      <nav style={{ display: "flex", gap: 22, marginLeft: 8 }}>
        {link("Inicio", "home")}
        {link("Catálogo", "catalog")}
      </nav>

      {/* Search */}
      <form
        onSubmit={(e) => { e.preventDefault(); onSearch(); }}
        style={{ flex: 1, maxWidth: 460, marginLeft: "auto", display: "flex", alignItems: "center", gap: 8,
          background: "var(--surface-sunken)", border: "1px solid var(--border-subtle)",
          borderRadius: 999, padding: "0 6px 0 14px", height: 42 }}
      >
        <i data-lucide="search" style={{ width: 18, height: 18, color: "var(--text-muted)" }} />
        <input
          value={query}
          onChange={(e) => onQuery(e.target.value)}
          placeholder="Buscar laptops, audio, componentes…"
          style={{ flex: 1, border: "none", outline: "none", background: "transparent",
            fontFamily: "var(--font-sans)", fontSize: 14, color: "var(--text-strong)" }}
        />
        <IconButton icon="arrow-right" label="Buscar" variant="solid" size="sm" round />
      </form>

      <div style={{ display: "flex", alignItems: "center", gap: 10 }}>
        {user ? (
          <div style={{ display: "flex", alignItems: "center", gap: 10 }}>
            <span style={{ fontSize: 13, color: "var(--text-muted)", maxWidth: 130, overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }}>{user.email}</span>
            <IconButton icon="log-out" label="Cerrar sesión" variant="ghost" onClick={onLogout} />
          </div>
        ) : (
          <IconButton icon="user" label="Iniciar sesión" variant="outline" onClick={() => onNavigate({ name: "login" })} />
        )}
        <div style={{ position: "relative" }}>
          <IconButton icon="shopping-cart" label="Carrito" variant="cta" onClick={() => onNavigate({ name: "cart" })} />
          {cartCount > 0 && (
            <span style={{ position: "absolute", top: -5, right: -5, minWidth: 19, height: 19, padding: "0 5px",
              borderRadius: 999, background: "var(--kr-navy-800)", color: "#fff", fontSize: 11, fontWeight: 700,
              display: "flex", alignItems: "center", justifyContent: "center", border: "2px solid #fff" }}>{cartCount}</span>
          )}
        </div>
      </div>
    </header>
  );
}
window.KrNavbar = KrNavbar;
