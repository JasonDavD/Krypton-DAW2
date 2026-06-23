// Krypton storefront — Login / Register screen.
function KrLogin({ onLogin, onNavigate }) {
  const { Button, Input } = window.KryptonDesignSystem_457463;
  const [mode, setMode] = React.useState("login");
  const [email, setEmail] = React.useState("");

  const tab = (label, value) => (
    <button onClick={() => setMode(value)}
      style={{ flex: 1, padding: "12px 0", background: "none", border: "none", cursor: "pointer",
        fontFamily: "var(--font-sans)", fontSize: 15, fontWeight: mode === value ? 700 : 500,
        color: mode === value ? "var(--color-brand)" : "var(--text-muted)",
        borderBottom: mode === value ? "2px solid var(--color-brand)" : "2px solid transparent" }}>
      {label}
    </button>
  );

  return (
    <div style={{ minHeight: "calc(100vh - var(--navbar-h))", display: "grid", gridTemplateColumns: "1fr 1fr" }}>
      {/* Brand panel */}
      <div style={{ background: "var(--surface-inverse)", color: "#fff", padding: "56px 56px", display: "flex", flexDirection: "column", justifyContent: "center", position: "relative", overflow: "hidden" }}>
        <div style={{ position: "absolute", left: -100, bottom: -120, width: 420, height: 420, borderRadius: "50%", background: "radial-gradient(circle, rgba(26,125,215,0.5), transparent 65%)" }} />
        <div style={{ position: "relative" }}>
          <img src="../../assets/logo/Krypton-white.svg" alt="Krypton" style={{ height: 34, marginBottom: 30 }} />
          <h2 style={{ fontSize: 40, fontWeight: 900, fontStyle: "italic", color: "#fff", lineHeight: 1.05, margin: "0 0 16px" }}>Tecnología<br />que enciende</h2>
          <p style={{ fontSize: 16, color: "var(--kr-gray-300)", maxWidth: 360, lineHeight: 1.6 }}>Ingresa para seguir tus pedidos, guardar favoritos y comprar más rápido.</p>
          <div style={{ display: "flex", flexDirection: "column", gap: 14, marginTop: 30 }}>
            {[["truck", "Envío gratis desde S/ 199"], ["shield-check", "Garantía oficial 12 meses"], ["credit-card", "Cuotas sin interés"]].map(([ic, t]) => (
              <div key={t} style={{ display: "flex", alignItems: "center", gap: 12 }}>
                <i data-lucide={ic} style={{ width: 20, height: 20, color: "var(--kr-yellow-500)" }} />
                <span style={{ fontSize: 15, color: "#fff" }}>{t}</span>
              </div>
            ))}
          </div>
        </div>
      </div>

      {/* Form panel */}
      <div style={{ display: "flex", alignItems: "center", justifyContent: "center", padding: "40px" }}>
        <div style={{ width: "100%", maxWidth: 380 }}>
          <div style={{ display: "flex", marginBottom: 26 }}>
            {tab("Iniciar sesión", "login")}
            {tab("Registrarse", "register")}
          </div>

          <form onSubmit={(e) => { e.preventDefault(); onLogin(email || "cliente@krypton.pe"); }} style={{ display: "flex", flexDirection: "column", gap: 16 }}>
            {mode === "register" && (
              <Input label="Nombre completo" placeholder="Tu nombre" iconLeft={<i data-lucide="user" style={{ width: 18, height: 18 }} />} />
            )}
            <Input label="Correo electrónico" type="email" placeholder="tu@correo.com" value={email} onChange={(e) => setEmail(e.target.value)} iconLeft={<i data-lucide="mail" style={{ width: 18, height: 18 }} />} />
            <Input label="Contraseña" type="password" placeholder="••••••••" helper={mode === "register" ? "Mínimo 8 caracteres" : undefined} iconLeft={<i data-lucide="lock" style={{ width: 18, height: 18 }} />} />
            {mode === "register" && (
              <Input label="Confirmar contraseña" type="password" placeholder="••••••••" iconLeft={<i data-lucide="lock" style={{ width: 18, height: 18 }} />} />
            )}
            {mode === "login" && (
              <div style={{ textAlign: "right", marginTop: -6 }}>
                <a href="#" onClick={(e) => e.preventDefault()} style={{ fontSize: 13, color: "var(--text-link)" }}>¿Olvidaste tu contraseña?</a>
              </div>
            )}
            {mode === "register" && (
              <label style={{ display: "flex", alignItems: "flex-start", gap: 10, fontSize: 13, color: "var(--text-muted)", lineHeight: 1.5, cursor: "pointer", marginTop: -2 }}>
                <input type="checkbox" defaultChecked style={{ width: 16, height: 16, marginTop: 1, accentColor: "var(--color-brand)" }} />
                <span>Acepto los <a href="#" onClick={(e) => e.preventDefault()} style={{ color: "var(--text-link)" }}>Términos</a> y la <a href="#" onClick={(e) => e.preventDefault()} style={{ color: "var(--text-link)" }}>Política de privacidad</a>.</span>
              </label>
            )}
            <Button variant="cta" size="lg" block type="submit">{mode === "login" ? "Iniciar sesión" : "Crear cuenta"}</Button>
          </form>

          <p style={{ textAlign: "center", marginTop: 18, fontSize: 14, color: "var(--text-muted)" }}>
            {mode === "login" ? "¿Aún no tienes cuenta? " : "¿Ya tienes cuenta? "}
            <a href="#" onClick={(e) => { e.preventDefault(); setMode(mode === "login" ? "register" : "login"); }} style={{ color: "var(--text-link)", fontWeight: 600 }}>
              {mode === "login" ? "Regístrate" : "Inicia sesión"}
            </a>
          </p>

          <div style={{ display: "flex", alignItems: "center", gap: 12, margin: "22px 0", color: "var(--text-faint)", fontSize: 13 }}>
            <span style={{ flex: 1, height: 1, background: "var(--border-subtle)" }} />o continúa con<span style={{ flex: 1, height: 1, background: "var(--border-subtle)" }} />
          </div>
          <div style={{ display: "flex", gap: 12 }}>
            <Button variant="secondary" block iconLeft={<i data-lucide="globe" style={{ width: 18, height: 18 }} />}>Google</Button>
            <Button variant="secondary" block iconLeft={<i data-lucide="smartphone" style={{ width: 18, height: 18 }} />}>Apple</Button>
          </div>
        </div>
      </div>
    </div>
  );
}
window.KrLogin = KrLogin;
