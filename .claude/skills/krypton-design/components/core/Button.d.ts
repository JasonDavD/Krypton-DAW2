import * as React from "react";

export interface ButtonProps extends React.ButtonHTMLAttributes<HTMLButtonElement> {
  /** Visual style. `cta` is the orange energy button reserved for the top action. */
  variant?: "primary" | "cta" | "secondary" | "ghost" | "danger";
  size?: "sm" | "md" | "lg";
  /** Full-width */
  block?: boolean;
  disabled?: boolean;
  /** Icon node rendered before the label */
  iconLeft?: React.ReactNode;
  /** Icon node rendered after the label */
  iconRight?: React.ReactNode;
  children?: React.ReactNode;
}

/**
 * Krypton's primary button.
 * @startingPoint section="Core" subtitle="Button variants & sizes" viewport="700x220"
 */
export declare function Button(props: ButtonProps): JSX.Element;
