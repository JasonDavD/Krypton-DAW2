import * as React from "react";

export interface CardProps extends React.HTMLAttributes<HTMLElement> {
  padding?: number | string;
  elevation?: "none" | "sm" | "md" | "lg";
  /** Adds hover lift + brand border tint */
  interactive?: boolean;
  as?: keyof JSX.IntrinsicElements;
  children?: React.ReactNode;
}

/** White surface container with token radius/shadow and optional hover lift. */
export declare function Card(props: CardProps): JSX.Element;
