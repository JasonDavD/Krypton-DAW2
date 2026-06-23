import React from "react";

/**
 * Krypton Button — the primary interactive control.
 * Variants: primary (blue), cta (orange, energy), secondary (outline), ghost, danger.
 */
export function Button({
  children,
  variant = "primary",
  size = "md",
  block = false,
  disabled = false,
  iconLeft = null,
  iconRight = null,
  type = "button",
  onClick,
  style = {},
  ...rest
}) {
  const sizes = {
    sm: { padding: "0 14px", height: 36, fontSize: 14, radius: 8, gap: 7 },
    md: { padding: "0 20px", height: 44, fontSize: 15, radius: 10, gap: 8 },
    lg: { padding: "0 28px", height: 54, fontSize: 17, radius: 12, gap: 10 },
  };
  const s = sizes[size] || sizes.md;

  const variants = {
    primary: {
      background: "var(--action-primary)",
      color: "var(--text-on-brand)",
      border: "1px solid transparent",
      boxShadow: "var(--shadow-sm)",
    },
    cta: {
      background: "var(--action-cta)",
      color: "var(--text-on-brand)",
      border: "1px solid transparent",
      boxShadow: "var(--shadow-cta)",
    },
    secondary: {
      background: "var(--surface-card)",
      color: "var(--color-brand)",
      border: "1.5px solid var(--border-default)",
      boxShadow: "none",
    },
    ghost: {
      background: "transparent",
      color: "var(--color-brand)",
      border: "1px solid transparent",
      boxShadow: "none",
    },
    danger: {
      background: "var(--kr-danger)",
      color: "#fff",
      border: "1px solid transparent",
      boxShadow: "var(--shadow-sm)",
    },
  };
  const v = variants[variant] || variants.primary;

  const [hover, setHover] = React.useState(false);
  const [press, setPress] = React.useState(false);

  const hoverBg = {
    primary: "var(--action-primary-hover)",
    cta: "var(--action-cta-hover)",
    secondary: "var(--surface-sunken)",
    ghost: "var(--kr-blue-50)",
    danger: "#c4322a",
  }[variant];

  return (
    <button
      type={type}
      disabled={disabled}
      onClick={onClick}
      onMouseEnter={() => setHover(true)}
      onMouseLeave={() => { setHover(false); setPress(false); }}
      onMouseDown={() => setPress(true)}
      onMouseUp={() => setPress(false)}
      style={{
        display: block ? "flex" : "inline-flex",
        width: block ? "100%" : "auto",
        alignItems: "center",
        justifyContent: "center",
        gap: s.gap,
        height: s.height,
        padding: s.padding,
        fontFamily: "var(--font-sans)",
        fontWeight: 600,
        fontSize: s.fontSize,
        lineHeight: 1,
        borderRadius: s.radius,
        cursor: disabled ? "not-allowed" : "pointer",
        opacity: disabled ? 0.5 : 1,
        transition: "background var(--dur-fast) var(--ease-out), transform var(--dur-fast) var(--ease-out), box-shadow var(--dur-base) var(--ease-out)",
        transform: press && !disabled ? "scale(0.97)" : "scale(1)",
        ...v,
        background: hover && !disabled ? hoverBg : v.background,
        ...style,
      }}
      {...rest}
    >
      {iconLeft}
      {children}
      {iconRight}
    </button>
  );
}
