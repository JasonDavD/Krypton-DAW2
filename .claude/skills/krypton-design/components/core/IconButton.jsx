import React from "react";

/**
 * Krypton IconButton — square/round button holding a single Lucide icon.
 * Pass a Lucide name; renders <i data-lucide=...> (call lucide.createIcons() after mount).
 */
export function IconButton({
  icon,
  label,
  variant = "soft",
  size = "md",
  round = false,
  disabled = false,
  onClick,
  style = {},
  children,
  ...rest
}) {
  const dims = { sm: 36, md: 44, lg: 52 }[size] || 44;
  const iconSize = { sm: 16, md: 20, lg: 22 }[size] || 20;
  const [hover, setHover] = React.useState(false);

  const variants = {
    solid: { background: "var(--action-primary)", color: "#fff", border: "1px solid transparent", hover: "var(--action-primary-hover)" },
    cta: { background: "var(--action-cta)", color: "#fff", border: "1px solid transparent", hover: "var(--action-cta-hover)" },
    soft: { background: "var(--kr-blue-50)", color: "var(--color-brand)", border: "1px solid transparent", hover: "var(--kr-blue-100)" },
    outline: { background: "var(--surface-card)", color: "var(--text-body)", border: "1.5px solid var(--border-default)", hover: "var(--surface-sunken)" },
    ghost: { background: "transparent", color: "var(--text-body)", border: "1px solid transparent", hover: "var(--surface-sunken)" },
  };
  const v = variants[variant] || variants.soft;

  return (
    <button
      type="button"
      aria-label={label}
      title={label}
      disabled={disabled}
      onClick={onClick}
      onMouseEnter={() => setHover(true)}
      onMouseLeave={() => setHover(false)}
      style={{
        width: dims,
        height: dims,
        display: "inline-flex",
        alignItems: "center",
        justifyContent: "center",
        borderRadius: round ? "50%" : 10,
        cursor: disabled ? "not-allowed" : "pointer",
        opacity: disabled ? 0.5 : 1,
        transition: "background var(--dur-fast) var(--ease-out), transform var(--dur-fast) var(--ease-out)",
        ...v,
        background: hover && !disabled ? v.hover : v.background,
        ...style,
      }}
      {...rest}
    >
      {children || <i data-lucide={icon} style={{ width: iconSize, height: iconSize }} />}
    </button>
  );
}
