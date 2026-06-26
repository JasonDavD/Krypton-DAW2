package pe.com.krypton.service;

import pe.com.krypton.dto.request.ItemCarritoRequest;
import pe.com.krypton.dto.request.UpdateQuantityRequest;
import pe.com.krypton.dto.response.CarritoResponse;

/** Operaciones del carrito del usuario (identidad = email del JWT). */
public interface CarritoService {

    /** Carrito actual con precios vivos del catálogo; vacío si no existe. */
    CarritoResponse obtenerCarrito(String email);

    /** Agrega un producto (o suma cantidad si ya estaba). Valida stock contra catalog. */
    CarritoResponse agregarItem(String email, ItemCarritoRequest request);

    /** Cambia la cantidad de una línea propia. Valida stock. */
    CarritoResponse actualizarItem(String email, Long itemId, UpdateQuantityRequest request);

    /** Quita una línea propia del carrito. */
    void quitarItem(String email, Long itemId);

    /** Vacía el carrito del usuario. */
    void vaciarCarrito(String email);
}
