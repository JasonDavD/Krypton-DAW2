import React from "react";

/** Krypton native select with brand styling and a custom chevron. */
export function Select({
  label,
  id,
  value,
  defaultValue,
  onChange,
  options = [],
  placeholder,
  disabled = false,
  style = {},
  ...rest
}) {
  const [focus, setFocus] = React.useState(false);
  const reactId = React.useId();
  const selId = id || reactId;

  return (
    <div style={{ display: "flex", flexDirection: "column", gap: 6, fontFamily: "var(--font-sans)", ...style }}>
      {label && (
        <label htmlFor={selId} style={{ fontSize: 13, fontWeight: 600, color: "var(--text-strong)" }}>
          {label}
        </label>
      )}
      <div style={{ position: "relative" }}>
        <select
          id={selId}
          value={value}
          defaultValue={defaultValue}
          onChange={onChange}
          disabled={disabled}
          onFocus={() => setFocus(true)}
          onBlur={() => setFocus(false)}
          style={{
            appearance: "none",
            WebkitAppearance: "none",
            width: "100%",
            height: 44,
            padding: "0 38px 0 12px",
            background: disabled ? "var(--surface-sunken)" : "var(--surface-card)",
            border: `1.5px solid ${focus ? "var(--border-focus)" : "var(--border-default)"}`,
            borderRadius: 10,
            boxShadow: focus ? "var(--shadow-focus)" : "none",
            fontFamily: "var(--font-sans)",
            fontSize: 15,
            color: "var(--text-strong)",
            cursor: disabled ? "not-allowed" : "pointer",
            transition: "border-color var(--dur-fast) var(--ease-out), box-shadow var(--dur-base) var(--ease-out)",
            outline: "none",
          }}
          {...rest}
        >
          {placeholder && <option value="">{placeholder}</option>}
          {options.map((o) => {
            const opt = typeof o === "string" ? { value: o, label: o } : o;
            return (
              <option key={opt.value} value={opt.value}>
                {opt.label}
              </option>
            );
          })}
        </select>
        <span
          aria-hidden
          style={{
            position: "absolute",
            right: 12,
            top: "50%",
            transform: "translateY(-50%)",
            pointerEvents: "none",
            color: "var(--text-muted)",
            display: "inline-flex",
          }}
        >
          <i data-lucide="chevron-down" style={{ width: 18, height: 18 }} />
        </span>
      </div>
    </div>
  );
}
