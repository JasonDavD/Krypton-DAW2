package pe.com.krypton.service;

import java.util.List;
import pe.com.krypton.dto.request.CheckoutRequest;
import pe.com.krypton.dto.response.OrdenResponse;

/** Operaciones de pedidos del usuario. */
public interface OrdenService {

    /**
     * Confirma la compra (checkout). La saga distribuida (descuento de stock en catalog +
     * creación de orden + compensación) la implementa el orquestador.
     */
    OrdenResponse confirmarCompra(String email, CheckoutRequest request);

    /** Órdenes del usuario, más recientes primero. */
    List<OrdenResponse> misOrdenes(String email);

    /** Una orden propia por id (404 si no existe o no es del usuario). */
    OrdenResponse miOrden(String email, Long id);
}
