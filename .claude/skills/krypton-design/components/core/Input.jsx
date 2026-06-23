import React from "react";

/** Krypton text input with label, helper/error text, and optional leading icon. */
export function Input({
  label,
  id,
  type = "text",
  placeholder = "",
  value,
  defaultValue,
  onChange,
  helper,
  error,
  iconLeft = null,
  disabled = false,
  style = {},
  ...rest
}) {
  const [focus, setFocus] = React.useState(false);
  const reactId = React.useId();
  const inputId = id || reactId;
  const invalid = Boolean(error);

  const borderColor = invalid
    ? "var(--kr-danger)"
    : focus
    ? "var(--border-focus)"
    : "var(--border-default)";

  return (
    <div style={{ display: "flex", flexDirection: "column", gap: 6, fontFamily: "var(--font-sans)", ...style }}>
      {label && (
        <label htmlFor={inputId} style={{ fontSize: 13, fontWeight: 600, color: "var(--text-strong)" }}>
          {label}
        </label>
      )}
      <div
        style={{
          display: "flex",
          alignItems: "center",
          gap: 8,
          background: disabled ? "var(--surface-sunken)" : "var(--surface-card)",
          border: `1.5px solid ${borderColor}`,
          borderRadius: 10,
          padding: "0 12px",
          height: 44,
          boxShadow: focus && !invalid ? "var(--shadow-focus)" : "none",
          transition: "border-color var(--dur-fast) var(--ease-out), box-shadow var(--dur-base) var(--ease-out)",
        }}
      >
        {iconLeft && <span style={{ color: "var(--text-muted)", display: "inline-flex" }}>{iconLeft}</span>}
        <input
          id={inputId}
          type={type}
          placeholder={placeholder}
          value={value}
          defaultValue={defaultValue}
          onChange={onChange}
          disabled={disabled}
          onFocus={() => setFocus(true)}
          onBlur={() => setFocus(false)}
          style={{
            flex: 1,
            border: "none",
            outline: "none",
            background: "transparent",
            fontFamily: "var(--font-sans)",
            fontSize: 15,
            color: "var(--text-strong)",
            minWidth: 0,
          }}
          {...rest}
        />
      </div>
      {(helper || error) && (
        <span style={{ fontSize: 12, color: invalid ? "var(--kr-danger)" : "var(--text-muted)" }}>
          {error || helper}
        </span>
      )}
    </div>
  );
}
