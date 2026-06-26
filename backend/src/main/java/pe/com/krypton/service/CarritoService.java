package pe.com.krypton.service;

import pe.com.krypton.dto.request.ItemCarritoRequest;
import pe.com.krypton.dto.request.UpdateQuantityRequest;
import pe.com.krypton.dto.response.CarritoResponse;

public interface CarritoService {

    CarritoResponse getCart(String email);

    CarritoResponse addItem(String email, ItemCarritoRequest request);

    /** Public so Spring proxy can intercept. Tx1: insert path. */
    CarritoResponse attemptAddItem(String email, ItemCarritoRequest request);

    /** Public so Spring proxy can intercept. Tx2: merge path after constraint violation. */
    CarritoResponse mergeOnConflict(String email, ItemCarritoRequest request);

    CarritoResponse updateItem(String email, Long itemId, UpdateQuantityRequest request);

    void removeItem(String email, Long itemId);

    void clearCart(String email);
}
