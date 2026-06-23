import { useState, type CSSProperties, type FormEvent } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import axios from 'axios';
import { ArrowRight, Check, Eye, EyeOff, Lock, Mail, Package, ShieldCheck, Truck, User, Zap } from 'lucide-react';
import { useAuth } from '../../auth/AuthContext';
import { useComingSoon } from '../../components/coming-soon/ComingSoon';
import './auth-form.css';

interface FieldErrors {
  name?: string;
  email?: string;
  password?: string;
  confirm?: string;
  agree?: string;
}

const label: CSSProperties = { fontSize: 12.5, fontWeight: 600, color: 'var(--text-body)' };
const field: CSSProperties = { display: 'flex', flexDirection: 'column', gap: 7 };
const inputRow: CSSProperties = { position: 'relative', display: 'flex', alignItems: 'center' };
const errorText: CSSProperties = { fontSize: 12, color: 'var(--kr-danger)' };
const baseInput: CSSProperties = {
  width: '100%', height: 50, padding: '0 14px 0 42px', border: '1.5px solid var(--border-subtle)',
  borderRadius: 12, fontFamily: 'var(--font-sans)', fontSize: 15, color: 'var(--text-strong)',
  background: 'var(--kr-gray-50)', outline: 'none', boxSizing: 'border-box',
};

function tabStyle(active: boolean): CSSProperties {
  return {
    flex: 1, height: 44, display: 'flex', alignItems: 'center', justifyContent: 'center',
    borderRadius: 999, textDecoration: 'none', fontWeight: 700, fontSize: 14,
    background: active ? '#fff' : 'transparent',
    color: active ? 'var(--kr-navy-800)' : 'var(--text-muted)',
    boxShadow: active ? '0 2px 8px rgba(3,39,90,0.12)' : 'none',
  };
}

export function AuthForm({ mode }: { mode: 'login' | 'register' }) {
  const isLogin = mode === 'login';
  const { login, register } = useAuth();
  const comingSoon = useComingSoon();
  const navigate = useNavigate();

  const [name, setName] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [confirm, setConfirm] = useState('');
  const [agree, setAgree] = useState(false);
  const [showPw, setShowPw] = useState(false);
  const [loading, setLoading] = useState(false);
  const [success, setSuccess] = useState(false);
  const [serverError, setServerError] = useState<string | null>(null);
  const [errors, setErrors] = useState<FieldErrors>({});

  const onSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setServerError(null);

    const errs: FieldErrors = {};
    if (!isLogin && name.trim().length < 2) errs.name = 'Ingresa tu nombre completo.';
    if (!/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(email)) errs.email = 'Ingresa un correo válido.';
    if (password.length < 6) errs.password = 'Mínimo 6 caracteres.';
    if (!isLogin) {
      if (confirm !== password) errs.confirm = 'Las contraseñas no coinciden.';
      if (!agree) errs.agree = 'Debes aceptar los Términos y la Política para crear tu cuenta.';
    }
    setErrors(errs);
    if (Object.keys(errs).length > 0) return;

    setLoading(true);
    try {
      if (isLogin) await login({ email, password });
      else await register({ name, email, password });
      setSuccess(true);
      setTimeout(() => navigate('/catalogo'), 1100);
    } catch (err) {
      setLoading(false);
      let msg = 'No se pudo completar la operación. Inténtalo de nuevo.';
      if (axios.isAxiosError(err)) {
        const status = err.response?.status;
        if (status === 401) msg = 'Correo o contraseña incorrectos.';
        else if (status === 409) msg = 'Ese correo ya está registrado.';
      }
      setServerError(msg);
    }
  };

  return (
    <div style={{ position: 'relative', minHeight: '100vh', width: '100%', fontFamily: 'var(--font-sans)', overflow: 'hidden', background: 'radial-gradient(135% 110% at 80% -10%, #0a3a7a 0%, #03275a 44%, #021a3d 100%)', display: 'flex', flexDirection: 'column' }}>
      <img src="/bg-login.webp" alt="" style={{ position: 'fixed', inset: 0, zIndex: 0, width: '100%', height: '100%', objectFit: 'cover', mixBlendMode: 'multiply', opacity: 0.55, pointerEvents: 'none' }} />
      <div style={{ position: 'fixed', inset: 0, zIndex: 0, background: 'rgba(2,18,55,0.5)', pointerEvents: 'none' }} />

      <div className="kr-orb" style={{ position: 'absolute', top: -150, right: -120, width: 520, height: 520, borderRadius: '50%', background: 'radial-gradient(circle, rgba(243,116,2,0.32), rgba(3,39,90,0) 64%)', filter: 'blur(8px)', pointerEvents: 'none', animation: 'krFloat 11s ease-in-out infinite' }} />
      <div className="kr-orb" style={{ position: 'absolute', bottom: -200, left: -140, width: 560, height: 560, borderRadius: '50%', background: 'radial-gradient(circle, rgba(26,125,215,0.40), rgba(3,39,90,0) 66%)', filter: 'blur(8px)', pointerEvents: 'none', animation: 'krFloat2 13s ease-in-out infinite' }} />

      <header style={{ position: 'relative', zIndex: 2, display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '26px 34px' }}>
        <Link to="/" aria-label="Krypton inicio">
          <img src="/brand/Krypton-white.svg" alt="Krypton" style={{ height: 26, display: 'block' }} />
        </Link>
      </header>

      <main style={{ position: 'relative', zIndex: 2, flex: 1, display: 'flex', alignItems: 'flex-start', justifyContent: 'center', padding: '40px 24px 48px' }}>
        <div style={{ display: 'grid', gridTemplateColumns: 'minmax(0,1fr) 460px', gap: 64, alignItems: 'start', width: '100%', maxWidth: 1080 }}>

          {/* LEFT · pitch */}
          <div style={{ color: '#fff', minWidth: 0, minHeight: 645, display: 'flex', flexDirection: 'column', justifyContent: 'center' }}>
            <span style={{ display: 'inline-flex', alignItems: 'center', gap: 8, fontSize: 11.5, fontWeight: 700, letterSpacing: '0.14em', textTransform: 'uppercase', color: 'var(--kr-blue-500)', marginBottom: 24 }}>
              <Zap size={16} color="var(--kr-yellow-500)" /> Tienda de tecnología
            </span>
            <h1 style={{ fontFamily: 'var(--font-display)', fontWeight: 900, fontStyle: 'italic', fontSize: 56, lineHeight: 0.98, letterSpacing: '-0.025em', margin: '0 0 20px', textShadow: '0 2px 22px rgba(2,18,55,0.45)' }}>
              <span style={{ color: '#fff' }}>Tecnología<br />que&nbsp;</span>
              <span style={{ color: 'var(--kr-orange-500)' }}>enciende.</span>
            </h1>
            <p style={{ fontSize: 16.5, lineHeight: 1.6, color: 'rgba(255,255,255,0.66)', margin: '0 0 34px', maxWidth: 380 }}>
              Crea tu cuenta y arma tu setup en minutos. Lo último en laptops, componentes y audio — a un solo clic.
            </p>
            <div style={{ display: 'flex', gap: 10, flexWrap: 'wrap' }}>
              <span className="kr-chip"><Truck size={16} color="var(--kr-yellow-500)" /> Envío en 24h</span>
              <span className="kr-chip"><ShieldCheck size={16} color="var(--kr-yellow-500)" /> Pago seguro</span>
              <span className="kr-chip"><Package size={16} color="var(--kr-yellow-500)" /> Garantía oficial</span>
            </div>
          </div>

          {/* RIGHT · card */}
          <div style={{ position: 'relative' }}>
            <div style={{ position: 'absolute', inset: 'auto 8% -22px 8%', height: 46, borderRadius: '50%', background: 'radial-gradient(ellipse, rgba(0,0,0,0.42), rgba(0,0,0,0) 70%)', filter: 'blur(8px)' }} />
            <div style={{ position: 'relative', background: '#fff', borderRadius: 26, padding: '34px 34px 30px', boxShadow: '0 44px 100px -34px rgba(2,18,55,0.7), 0 0 0 1px rgba(255,255,255,0.6)' }}>

              {success ? (
                <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', textAlign: 'center', padding: '14px 6px 8px' }}>
                  <span style={{ display: 'inline-flex', alignItems: 'center', justifyContent: 'center', width: 78, height: 78, borderRadius: '50%', background: 'var(--kr-success-bg)', color: 'var(--kr-success)', marginBottom: 22 }}>
                    <Check size={40} />
                  </span>
                  <h2 style={{ fontFamily: 'var(--font-display)', fontWeight: 800, fontSize: 27, letterSpacing: '-0.02em', color: 'var(--text-strong)', margin: '0 0 10px' }}>¡Todo listo!</h2>
                  <p style={{ fontSize: 15, color: 'var(--text-muted)', lineHeight: 1.6, margin: '0 0 26px', maxWidth: 300 }}>Tu sesión está activa. Te llevamos al catálogo.</p>
                </div>
              ) : (
                <form className="kr-card-in" onSubmit={onSubmit}>

                  {/* segmented switch */}
                  <div style={{ display: 'flex', gap: 4, padding: 5, borderRadius: 999, background: 'var(--kr-gray-100)', marginBottom: 22 }}>
                    <Link to="/cuenta/ingresar" style={tabStyle(isLogin)}>Iniciar sesión</Link>
                    <Link to="/cuenta/registro" style={tabStyle(!isLogin)}>Crear cuenta</Link>
                  </div>

                  <div>
                    <h2 style={{ fontFamily: 'var(--font-display)', fontWeight: 800, fontSize: 26, letterSpacing: '-0.02em', color: 'var(--text-strong)', margin: '0 0 5px' }}>
                      {isLogin ? 'Bienvenido de nuevo' : 'Crea tu cuenta'}
                    </h2>
                    <p style={{ fontSize: 14.5, color: 'var(--text-muted)', margin: 0 }}>
                      {isLogin ? 'Inicia sesión para continuar con tu compra.' : 'Únete a Krypton y arma tu setup en minutos.'}
                    </p>
                  </div>

                  <div style={{ display: 'flex', flexDirection: 'column', gap: 15, marginTop: 22 }}>

                    {!isLogin && (
                      <div style={field}>
                        <label htmlFor="kr-name" style={label}>Nombre completo</label>
                        <div style={inputRow}>
                          <span className="kr-field-icon"><User size={18} /></span>
                          <input id="kr-name" className="kr-in" type="text" placeholder="Ej. Ana Quispe" value={name} onChange={(e) => setName(e.target.value)} style={baseInput} />
                        </div>
                        {errors.name && <span style={errorText}>{errors.name}</span>}
                      </div>
                    )}

                    <div style={field}>
                      <label htmlFor="kr-email" style={label}>Correo electrónico</label>
                      <div style={inputRow}>
                        <span className="kr-field-icon"><Mail size={18} /></span>
                        <input id="kr-email" className="kr-in" type="email" placeholder="tu@correo.com" value={email} onChange={(e) => setEmail(e.target.value)} style={baseInput} />
                      </div>
                      {errors.email && <span style={errorText}>{errors.email}</span>}
                    </div>

                    <div style={field}>
                      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
                        <label htmlFor="kr-password" style={label}>Contraseña</label>
                        {isLogin && (
                          <a href="#" onClick={(e) => { e.preventDefault(); comingSoon.show('Recuperar contraseña'); }} style={{ fontSize: 12.5, fontWeight: 600, color: 'var(--text-link)', textDecoration: 'none' }}>
                            ¿Olvidaste tu contraseña?
                          </a>
                        )}
                      </div>
                      <div style={inputRow}>
                        <span className="kr-field-icon"><Lock size={18} /></span>
                        <input id="kr-password" className="kr-in" type={showPw ? 'text' : 'password'} placeholder="••••••••" value={password} onChange={(e) => setPassword(e.target.value)} style={{ ...baseInput, padding: '0 44px 0 42px' }} />
                        <button type="button" onClick={() => setShowPw((v) => !v)} aria-label={showPw ? 'Ocultar contraseña' : 'Mostrar contraseña'} style={{ position: 'absolute', right: 9, width: 34, height: 34, display: 'flex', alignItems: 'center', justifyContent: 'center', border: 'none', background: 'transparent', color: 'var(--kr-gray-400)', cursor: 'pointer', borderRadius: 8 }}>
                          {showPw ? <EyeOff size={20} /> : <Eye size={20} />}
                        </button>
                      </div>
                      {errors.password && <span style={errorText}>{errors.password}</span>}
                    </div>

                    {!isLogin && (
                      <div style={field}>
                        <label htmlFor="kr-confirm" style={label}>Confirmar contraseña</label>
                        <div style={inputRow}>
                          <span className="kr-field-icon"><Lock size={18} /></span>
                          <input id="kr-confirm" className="kr-in" type={showPw ? 'text' : 'password'} placeholder="••••••••" value={confirm} onChange={(e) => setConfirm(e.target.value)} style={baseInput} />
                        </div>
                        {errors.confirm && <span style={errorText}>{errors.confirm}</span>}
                      </div>
                    )}

                    {!isLogin && (
                      <div style={field}>
                        <label style={{ display: 'flex', alignItems: 'flex-start', gap: 9, cursor: 'pointer', userSelect: 'none' }}>
                          <input type="checkbox" checked={agree} onChange={(e) => { setAgree(e.target.checked); setErrors((p) => ({ ...p, agree: undefined })); }} style={{ width: 17, height: 17, marginTop: 2, accentColor: 'var(--kr-blue-600)', cursor: 'pointer', flex: 'none' }} />
                          <span style={{ fontSize: 13.5, color: 'var(--text-body)', lineHeight: 1.45 }}>
                            Acepto los <a href="/terminos" target="_blank" rel="noopener noreferrer" style={{ color: 'var(--text-link)', textDecoration: 'none', fontWeight: 600 }}>Términos</a> y la <a href="/privacidad" target="_blank" rel="noopener noreferrer" style={{ color: 'var(--text-link)', textDecoration: 'none', fontWeight: 600 }}>Política de privacidad</a>.
                          </span>
                        </label>
                        {errors.agree && <span style={errorText}>{errors.agree}</span>}
                      </div>
                    )}

                    {isLogin && (
                      <label style={{ display: 'flex', alignItems: 'center', gap: 9, cursor: 'pointer', userSelect: 'none' }}>
                        <input type="checkbox" defaultChecked style={{ width: 17, height: 17, accentColor: 'var(--kr-blue-600)', cursor: 'pointer' }} />
                        <span style={{ fontSize: 13.5, color: 'var(--text-body)' }}>Mantener sesión iniciada</span>
                      </label>
                    )}

                    {serverError && (
                      <div style={{ fontSize: 13, color: 'var(--kr-danger)', background: '#fdecec', borderRadius: 10, padding: '10px 12px' }}>{serverError}</div>
                    )}

                    <button className="kr-cta" type="submit" disabled={loading} style={{ position: 'relative', display: 'flex', alignItems: 'center', justifyContent: 'center', width: '100%', height: 56, border: 'none', borderRadius: 999, background: 'linear-gradient(120deg, var(--kr-orange-500), var(--kr-redorange-500))', color: '#fff', fontFamily: 'var(--font-sans)', fontWeight: 700, fontSize: 16, cursor: 'pointer', boxShadow: '0 14px 30px -8px rgba(243,116,2,0.62)', marginTop: 6 }}>
                      <span>{loading ? 'Procesando…' : isLogin ? 'Iniciar sesión' : 'Crear mi cuenta'}</span>
                      <span style={{ position: 'absolute', right: 8, top: '50%', transform: 'translateY(-50%)', width: 40, height: 40, borderRadius: '50%', background: 'rgba(0,0,0,0.16)', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                        <ArrowRight size={20} />
                      </span>
                    </button>
                  </div>

                  <p style={{ textAlign: 'center', fontSize: 14, color: 'var(--text-muted)', margin: '22px 0 0' }}>
                    {isLogin ? (
                      <>¿Aún no tienes cuenta? <Link to="/cuenta/registro" style={{ color: 'var(--text-link)', fontWeight: 700, textDecoration: 'none' }}>Crear cuenta</Link></>
                    ) : (
                      <>¿Ya tienes cuenta? <Link to="/cuenta/ingresar" style={{ color: 'var(--text-link)', fontWeight: 700, textDecoration: 'none' }}>Iniciar sesión</Link></>
                    )}
                  </p>

                  {/* social */}
                  <div style={{ display: 'flex', alignItems: 'center', gap: 12, margin: '18px 0', color: 'var(--text-faint)', fontSize: 13 }}>
                    <span style={{ flex: 1, height: 1, background: 'var(--border-subtle)' }} />o continúa con<span style={{ flex: 1, height: 1, background: 'var(--border-subtle)' }} />
                  </div>
                  <div style={{ display: 'flex', gap: 12 }}>
                    <button type="button" className="kr-soc" onClick={() => comingSoon.show('Acceso con Google')}>
                      <svg width="18" height="18" viewBox="0 0 48 48" aria-hidden="true"><path fill="#FFC107" d="M43.6 20.5H42V20H24v8h11.3c-1.6 4.7-6.1 8-11.3 8-6.6 0-12-5.4-12-12s5.4-12 12-12c3.1 0 5.9 1.2 8 3.1l5.7-5.7C34.3 6.1 29.4 4 24 4 12.9 4 4 12.9 4 24s8.9 20 20 20 20-8.9 20-20c0-1.3-.1-2.3-.4-3.5z" /><path fill="#FF3D00" d="M6.3 14.7l6.6 4.8C14.7 15.1 19 12 24 12c3.1 0 5.9 1.2 8 3.1l5.7-5.7C34.3 6.1 29.4 4 24 4 16.3 4 9.7 8.3 6.3 14.7z" /><path fill="#4CAF50" d="M24 44c5.3 0 10.1-2 13.7-5.3l-6.3-5.3C29.3 35 26.8 36 24 36c-5.2 0-9.6-3.3-11.3-7.9l-6.5 5C9.6 39.6 16.2 44 24 44z" /><path fill="#1976D2" d="M43.6 20.5H42V20H24v8h11.3c-.8 2.3-2.3 4.3-4.2 5.7l6.3 5.3C40.9 35.7 44 30.3 44 24c0-1.3-.1-2.3-.4-3.5z" /></svg>
                      Google
                    </button>
                    <Link to="/" className="kr-soc"><User size={18} /> Invitado</Link>
                  </div>
                </form>
              )}
            </div>
          </div>
        </div>
      </main>
    </div>
  );
}
