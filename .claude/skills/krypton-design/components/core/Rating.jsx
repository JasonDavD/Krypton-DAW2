import React from "react";

/** Star rating using Lucide stars. Read-only display by default. */
export function Rating({ value = 0, count, size = 15, showValue = false, style = {} }) {
  const full = Math.round(value);
  return (
    <span style={{ display: "inline-flex", alignItems: "center", gap: 6, fontFamily: "var(--font-sans)", ...style }}>
      <span style={{ display: "inline-flex", gap: 1 }}>
        {[0, 1, 2, 3, 4].map((i) => (
          <i
            key={i}
            data-lucide="star"
            style={{
              width: size,
              height: size,
              color: i < full ? "var(--kr-yellow-500)" : "var(--kr-gray-300)",
              fill: i < full ? "var(--kr-yellow-500)" : "transparent",
            }}
          />
        ))}
      </span>
      {showValue && (
        <span style={{ fontSize: 13, fontWeight: 600, color: "var(--text-strong)" }}>{value.toFixed(1)}</span>
      )}
      {count != null && <span style={{ fontSize: 12, color: "var(--text-muted)" }}>({count})</span>}
    </span>
  );
}
