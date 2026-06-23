import * as React from "react";

export interface IconButtonProps extends React.ButtonHTMLAttributes<HTMLButtonElement> {
  /** Lucide icon name (e.g. "shopping-cart"). Ignored if children provided. */
  icon?: string;
  /** Accessible label (also the tooltip) */
  label: string;
  variant?: "solid" | "cta" | "soft" | "outline" | "ghost";
  size?: "sm" | "md" | "lg";
  round?: boolean;
  disabled?: boolean;
  children?: React.ReactNode;
}

/** A square or round button holding a single icon. */
export declare function IconButton(props: IconButtonProps): JSX.Element;
