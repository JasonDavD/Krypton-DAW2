import React from "react";
import { Badge } from "./Badge.jsx";
import { Rating } from "./Rating.jsx";

/**
 * Krypton product card — the storefront's signature unit.
 * Image over soft surface, name, price (S/), optional rating/badge, add-to-cart.
 */
export function ProductCard({
  name,
  price,
  oldPrice,
  image,
  icon,
  category,
  badge,
  rating,
  ratingCount,
  currency = "S/",
  onAdd,
  onClick,
  style = {},
}) {
  const [hover, setHover] = React.useState(false);
  const fmt = (n) =>
    `${currency} ${Number(n).toLocaleString("es-PE", { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;

  return (
    <div
      onClick={onClick}
      onMouseEnter={() => setHover(true)}
      onMouseLeave={() => setHover(false)}
      style={{
        display: "flex",
        flexDirection: "column",
        background: "var(--surface-card)",
        border: "1px solid var(--border-subtle)",
        borderColor: hover ? "var(--kr-blue-100)" : "var(--border-subtle)",
        borderRadius: "var(--radius-md)",
        overflow: "hidden",
        cursor: onClick ? "pointer" : "default",
        boxShadow: hover ? "var(--shadow-md)" : "var(--shadow-xs)",
        transform: hover ? "translateY(-3px)" : "translateY(0)",
        transition: "box-shadow var(--dur-base) var(--ease-out), transform var(--dur-base) var(--ease-out), border-color var(--dur-base) var(--ease-out)",
        fontFamily: "var(--font-sans)",
        ...style,
      }}
    >
      {/* Image */}
      <div
        style={{
          position: "relative",
          aspectRatio: "1 / 1",
          background: "linear-gradient(160deg, var(--kr-gray-50), var(--kr-blue-50))",
          display: "flex",
          alignItems: "center",
          justifyContent: "center",
          padding: 18,
        }}
      >
        {badge && (
          <span style={{ position: "absolute", top: 12, left: 12 }}>
            <Badge tone={badge.tone || "sale"} solid>
              {badge.label}
            </Badge>
          </span>
        )}
        {image ? (
          <img
            src={image}
            alt={name}
            style={{
              maxWidth: "100%",
              maxHeight: "100%",
              objectFit: "contain",
              transform: hover ? "scale(1.05)" : "scale(1)",
              transition: "transform var(--dur-slow) var(--ease-out)",
            }}
          />
        ) : (
          <i
            data-lucide={icon || "image"}
            style={{
              width: icon ? 76 : 40,
              height: icon ? 76 : 40,
              color: icon ? "var(--kr-navy-700)" : "var(--kr-gray-300)",
              strokeWidth: 1.4,
              transform: hover ? "scale(1.06)" : "scale(1)",
              transition: "transform var(--dur-slow) var(--ease-out)",
            }}
          />
        )}
      </div>

      {/* Body */}
      <div style={{ padding: 16, display: "flex", flexDirection: "column", gap: 8, flex: 1 }}>
        {category && (
          <span style={{ fontSize: 11, fontWeight: 700, letterSpacing: "0.1em", textTransform: "uppercase", color: "var(--color-brand)" }}>
            {category}
          </span>
        )}
        <h3
          style={{
            margin: 0,
            fontSize: 15,
            fontWeight: 600,
            color: "var(--text-strong)",
            lineHeight: 1.3,
            display: "-webkit-box",
            WebkitLineClamp: 2,
            WebkitBoxOrient: "vertical",
            overflow: "hidden",
            minHeight: 39,
          }}
        >
          {name}
        </h3>

        {rating != null && <Rating value={rating} count={ratingCount} size={14} />}

        <div style={{ marginTop: "auto", display: "flex", alignItems: "flex-end", justifyContent: "space-between", gap: 8, paddingTop: 6 }}>
          <div style={{ display: "flex", flexDirection: "column" }}>
            {oldPrice && (
              <span style={{ fontSize: 12, color: "var(--text-faint)", textDecoration: "line-through" }}>
                {fmt(oldPrice)}
              </span>
            )}
            <span style={{ fontSize: 19, fontWeight: 800, color: "var(--text-strong)", letterSpacing: "-0.01em" }}>
              {fmt(price)}
            </span>
          </div>
          {onAdd && (
            <button
              type="button"
              aria-label="Agregar al carrito"
              onClick={(e) => { e.stopPropagation(); onAdd(); }}
              style={{
                width: 40,
                height: 40,
                flex: "none",
                display: "inline-flex",
                alignItems: "center",
                justifyContent: "center",
                borderRadius: 10,
                border: "none",
                cursor: "pointer",
                background: "var(--action-cta)",
                color: "#fff",
                boxShadow: "var(--shadow-cta)",
                transition: "transform var(--dur-fast) var(--ease-bounce), background var(--dur-fast) var(--ease-out)",
                transform: hover ? "scale(1.06)" : "scale(1)",
              }}
            >
              <i data-lucide="shopping-cart" style={{ width: 18, height: 18 }} />
            </button>
          )}
        </div>
      </div>
    </div>
  );
}
