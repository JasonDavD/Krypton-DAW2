import React from "react";

/** Small status/label pill. Tones map to brand + semantic colors. */
export function Badge({ children, tone = "neutral", solid = false, style = {}, ...rest }) {
  const tones = {
    neutral: { bg: "var(--surface-sunken)", fg: "var(--text-body)", solidBg: "var(--kr-gray-600)" },
    brand: { bg: "var(--kr-blue-50)", fg: "var(--kr-blue-700)", solidBg: "var(--action-primary)" },
    cta: { bg: "var(--kr-orange-100)", fg: "var(--kr-orange-600)", solidBg: "var(--action-cta)" },
    sale: { bg: "var(--kr-redorange-100)", fg: "var(--kr-redorange-600)", solidBg: "var(--kr-redorange-500)" },
    warn: { bg: "var(--kr-warning-bg)", fg: "var(--kr-yellow-600)", solidBg: "var(--kr-yellow-500)" },
    success: { bg: "var(--kr-success-bg)", fg: "var(--kr-success)", solidBg: "var(--kr-success)" },
    danger: { bg: "var(--kr-danger-bg)", fg: "var(--kr-danger)", solidBg: "var(--kr-danger)" },
  };
  const t = tones[tone] || tones.neutral;
  return (
    <span
      style={{
        display: "inline-flex",
        alignItems: "center",
        gap: 5,
        height: 22,
        padding: "0 9px",
        borderRadius: 999,
        fontFamily: "var(--font-sans)",
        fontSize: 12,
        fontWeight: 600,
        lineHeight: 1,
        letterSpacing: "0.01em",
        background: solid ? t.solidBg : t.bg,
        color: solid ? "#fff" : t.fg,
        ...style,
      }}
      {...rest}
    >
      {children}
    </span>
  );
}
