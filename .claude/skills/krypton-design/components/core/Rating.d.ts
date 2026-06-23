import * as React from "react";

export interface RatingProps {
  /** 0–5 */
  value?: number;
  /** Number of reviews, shown as (n) */
  count?: number;
  size?: number;
  showValue?: boolean;
  style?: React.CSSProperties;
}

/** Read-only star rating (Lucide stars, brand yellow). */
export declare function Rating(props: RatingProps): JSX.Element;
