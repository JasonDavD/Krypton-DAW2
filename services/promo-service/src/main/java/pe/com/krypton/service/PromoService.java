package pe.com.krypton.service;

import java.util.List;
import pe.com.krypton.dto.request.AplicarPromoRequest;
import pe.com.krypton.dto.request.CreatePromoRequest;
import pe.com.krypton.dto.response.DescuentoResponse;
import pe.com.krypton.dto.response.PromoResponse;

/** Operaciones sobre codigos de descuento (cupones). */
public interface PromoService {

    /** Crea un cupon. Falla si el code ya existe. */
    PromoResponse crear(CreatePromoRequest req);

    /** Lista todos los cupones. */
    List<PromoResponse> listar();

    /** Aplica un cupon sobre un monto y devuelve el descuento + monto final. */
    DescuentoResponse aplicar(AplicarPromoRequest req);
}
