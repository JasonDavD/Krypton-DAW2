import * as React from "react";

export interface SelectOption {
  value: string | number;
  label: string;
}

export interface SelectProps extends Omit<React.SelectHTMLAttributes<HTMLSelectElement>, "style"> {
  label?: string;
  /** Options as strings or {value,label} objects */
  options?: Array<string | SelectOption>;
  /** Leading empty option label */
  placeholder?: string;
  style?: React.CSSProperties;
}

/** Styled native select with a Lucide chevron. */
export declare function Select(props: SelectProps): JSX.Element;
