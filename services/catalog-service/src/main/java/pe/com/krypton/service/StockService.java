package pe.com.krypton.service;

import pe.com.krypton.dto.request.StockMovementRequest;

/**
 * Operaciones de stock que catalog expone a otros servicios (Feign).
 * Lado "proveedor" del checkout distribuido (F5).
 */
public interface StockService {

    /**
     * Descuenta stock (movimiento SALIDA) de cada ítem en UNA sola transacción.
     * Lockea cada producto (PESSIMISTIC_WRITE) para evitar condiciones de carrera.
     * Lanza {@link pe.com.krypton.exception.InsufficientStockException} (→ 422) si
     * algún ítem no tiene stock suficiente: todo o nada.
     */
    void descontar(StockMovementRequest request);

    /**
     * Repone stock (movimiento ENTRADA) de cada ítem en UNA sola transacción.
     * Es la COMPENSACIÓN de {@link #descontar}: si el checkout descuenta stock y después
     * algo falla, order-service llama acá para "deshacer". No valida tope: solo suma.
     */
    void restaurar(StockMovementRequest request);
}
