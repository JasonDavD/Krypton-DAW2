import * as React from "react";

export interface BadgeProps extends React.HTMLAttributes<HTMLSpanElement> {
  tone?: "neutral" | "brand" | "cta" | "sale" | "warn" | "success" | "danger";
  /** Filled style instead of soft tint */
  solid?: boolean;
  children?: React.ReactNode;
}

/** Small status/label pill. */
export declare function Badge(props: BadgeProps): JSX.Element;
