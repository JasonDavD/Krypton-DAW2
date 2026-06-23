// Krypton storefront — footer (navy band).
function KrFooter() {
  const cols = [
    { title: "Productos", links: ["Laptops", "Smartphones", "Audio", "Componentes", "Gaming"] },
    { title: "Ayuda", links: ["Centro de ayuda", "Estado de pedido", "Envíos", "Devoluciones"] },
    { title: "Krypton", links: ["Nosotros", "Tiendas", "Trabaja con nosotros", "Contacto"] },
  ];
  return (
    <footer style={{ background: "var(--surface-inverse)", color: "var(--text-on-inverse)", marginTop: 64 }}>
      <div style={{ maxWidth: "var(--container-max)", margin: "0 auto", padding: "48px 28px 28px",
        display: "grid", gridTemplateColumns: "1.5fr repeat(3, 1fr)", gap: 32 }}>
        <div>
          <img src="../../assets/logo/Krypton-white.svg" alt="Krypton" style={{ height: 28, marginBottom: 14 }} />
          <p style={{ color: "var(--kr-gray-400)", fontSize: 14, lineHeight: 1.6, maxWidth: 280, margin: 0 }}>
            Tu tienda de tecnología. Dispositivos y componentes de última generación con envío a todo el Perú.
          </p>
          <div style={{ display: "flex", gap: 10, marginTop: 18 }}>
            {["instagram", "facebook", "youtube", "twitter"].map((s) => (
              <span key={s} style={{ width: 38, height: 38, borderRadius: 10, background: "rgba(255,255,255,0.08)",
                display: "inline-flex", alignItems: "center", justifyContent: "center" }}>
                <i data-lucide={s} style={{ width: 18, height: 18, color: "#fff" }} />
              </span>
            ))}
          </div>
        </div>
        {cols.map((c) => (
          <div key={c.title}>
            <div style={{ fontSize: 13, fontWeight: 700, letterSpacing: "0.08em", textTransform: "uppercase", color: "#fff", marginBottom: 14 }}>{c.title}</div>
            <ul style={{ listStyle: "none", padding: 0, margin: 0, display: "flex", flexDirection: "column", gap: 9 }}>
              {c.links.map((l) => (
                <li key={l}><a href="#" onClick={(e) => e.preventDefault()} style={{ color: "var(--kr-gray-400)", fontSize: 14, textDecoration: "none" }}>{l}</a></li>
              ))}
            </ul>
          </div>
        ))}
      </div>
      <div style={{ borderTop: "1px solid rgba(255,255,255,0.1)", padding: "18px 28px", display: "flex", justifyContent: "space-between", flexWrap: "wrap", gap: 10,
        maxWidth: "var(--container-max)", margin: "0 auto", color: "var(--kr-gray-500)", fontSize: 13 }}>
        <span>© {new Date().getFullYear()} Krypton E-commerce. Todos los derechos reservados.</span>
        <span style={{ display: "flex", gap: 18 }}>
          <a href="#" onClick={(e)=>e.preventDefault()} style={{ color: "var(--kr-gray-500)" }}>Privacidad</a>
          <a href="#" onClick={(e)=>e.preventDefault()} style={{ color: "var(--kr-gray-500)" }}>Términos</a>
        </span>
      </div>
    </footer>
  );
}
window.KrFooter = KrFooter;
