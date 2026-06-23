import * as React from "react";

export interface InputProps extends Omit<React.InputHTMLAttributes<HTMLInputElement>, "style"> {
  label?: string;
  helper?: string;
  /** Error message — turns the field red and overrides helper */
  error?: string;
  iconLeft?: React.ReactNode;
  style?: React.CSSProperties;
}

/** Text input with label, helper/error and optional leading icon. */
export declare function Input(props: InputProps): JSX.Element;
