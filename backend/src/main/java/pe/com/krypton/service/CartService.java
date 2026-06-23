package pe.com.krypton.service;

import pe.com.krypton.dto.request.CartItemRequest;
import pe.com.krypton.dto.request.UpdateQuantityRequest;
import pe.com.krypton.dto.response.CartResponse;

public interface CartService {

    CartResponse getCart(String email);

    CartResponse addItem(String email, CartItemRequest request);

    /** Public so Spring proxy can intercept. Tx1: insert path. */
    CartResponse attemptAddItem(String email, CartItemRequest request);

    /** Public so Spring proxy can intercept. Tx2: merge path after constraint violation. */
    CartResponse mergeOnConflict(String email, CartItemRequest request);

    CartResponse updateItem(String email, Long itemId, UpdateQuantityRequest request);

    void removeItem(String email, Long itemId);

    void clearCart(String email);
}
