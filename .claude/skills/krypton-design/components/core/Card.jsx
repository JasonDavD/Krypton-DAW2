import React from "react";

/** Generic surface container. Optional hover lift for interactive cards. */
export function Card({
  children,
  padding = 20,
  elevation = "sm",
  interactive = false,
  as = "div",
  style = {},
  ...rest
}) {
  const [hover, setHover] = React.useState(false);
  const shadows = {
    none: "none",
    sm: "var(--shadow-sm)",
    md: "var(--shadow-md)",
    lg: "var(--shadow-lg)",
  };
  const El = as;
  return (
    <El
      onMouseEnter={() => setHover(true)}
      onMouseLeave={() => setHover(false)}
      style={{
        background: "var(--surface-card)",
        border: "1px solid var(--border-subtle)",
        borderRadius: "var(--radius-md)",
        padding,
        boxShadow: interactive && hover ? "var(--shadow-md)" : shadows[elevation],
        borderColor: interactive && hover ? "var(--kr-blue-100)" : "var(--border-subtle)",
        transform: interactive && hover ? "translateY(-2px)" : "translateY(0)",
        transition: "box-shadow var(--dur-base) var(--ease-out), transform var(--dur-base) var(--ease-out), border-color var(--dur-base) var(--ease-out)",
        ...style,
      }}
      {...rest}
    >
      {children}
    </El>
  );
}
