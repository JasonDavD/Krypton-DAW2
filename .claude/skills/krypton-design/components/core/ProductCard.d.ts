import * as React from "react";

export interface ProductCardBadge {
  label: string;
  tone?: "sale" | "brand" | "cta" | "warn" | "success";
}

export interface ProductCardProps {
  name: string;
  /** Numeric price; formatted as S/ 0,000.00 (es-PE) */
  price: number;
  /** Optional struck-through original price */
  oldPrice?: number;
  /** Product image URL (contained over a soft brand gradient) */
  image?: string;
  /** Lucide icon name shown when no image (device stand-in, e.g. "laptop") */
  icon?: string;
  /** Eyebrow category label */
  category?: string;
  /** Corner flag, e.g. {label:"-20%", tone:"sale"} */
  badge?: ProductCardBadge;
  rating?: number;
  ratingCount?: number;
  /** Currency symbol prefix (default "S/") */
  currency?: string;
  /** Shows the orange add-to-cart button when provided */
  onAdd?: () => void;
  onClick?: () => void;
  style?: React.CSSProperties;
}

/**
 * The storefront's signature product tile.
 * @startingPoint section="Commerce" subtitle="Product card with price & add-to-cart" viewport="280x380"
 */
export declare function ProductCard(props: ProductCardProps): JSX.Element;
