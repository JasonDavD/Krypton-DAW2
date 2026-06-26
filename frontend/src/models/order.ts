/**
 * Tipos del contrato de pedidos. Reflejan los DTOs del backend (Jackson camelCase).
 * El total y el desglose (envío, IGV) los calcula y persiste el BACKEND — el front
 * sólo los muestra. Identificadores en inglés (contrato JSON).
 */

/** Comprobante: boleta (consumidor final, DNI) o factura (RUC, con desglose). */
export type DocumentType = 'BOLETA' | 'FACTURA';

/** Estado del pedido (refleja el enum OrderStatus del backend). */
export type OrderStatus = 'PENDIENTE' | 'CONFIRMADA' | 'ENVIADO' | 'ENTREGADO' | 'CANCELADA';

/** Método de pago (refleja el enum PaymentMethod del backend). */
export type PaymentMethod = 'CREDIT_CARD' | 'DEBIT_CARD' | 'YAPE';

/** Línea del pedido. unitPrice es el snapshot congelado al hacer checkout. */
export interface OrderItemResponse {
  id: number;
  productId: number;
  productName: string;
  quantity: number;
  unitPrice: number;
  subtotal: number;
}

export interface OrderResponse {
  id: number;
  userId: number;
  /** Instant ISO-8601. */
  orderDate: string;
  status: OrderStatus;
  documentType: DocumentType;
  /** Nombre (boleta) o razón social (factura) del receptor. */
  customerName: string;
  /** DNI (8 díg) o RUC (11 díg). */
  customerDoc: string;
  /** Subtotal de productos (IGV incluido). */
  subtotal: number;
  /** Descuento aplicado por cupón (0 si no hubo). */
  discount: number;
  shippingCost: number;
  /** IGV desglosado hacia adentro del total. */
  igv: number;
  total: number;
  items: OrderItemResponse[];
}

/** Body del checkout (POST /api/orders/checkout). El total no viene del cliente. */
export interface CheckoutRequest {
  documentType: DocumentType;
  customerName: string;
  customerDoc: string;
  /** Cupón de descuento opcional; el backend aplica el descuento real al total. */
  couponCode?: string;
}

/** Body del pago (POST /api/orders/{id}/pay). */
export interface PaymentRequest {
  method: PaymentMethod;
}
