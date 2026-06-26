package pe.com.krypton.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.com.krypton.dto.request.AplicarPromoRequest;
import pe.com.krypton.dto.request.CreatePromoRequest;
import pe.com.krypton.dto.response.DescuentoResponse;
import pe.com.krypton.dto.response.PromoResponse;
import pe.com.krypton.entity.Promo;
import pe.com.krypton.entity.enums.DescuentoTipo;
import pe.com.krypton.exception.DuplicatePromoCodeException;
import pe.com.krypton.exception.PromoInvalidaException;
import pe.com.krypton.mapper.PromoMapper;
import pe.com.krypton.repository.PromoRepository;
import pe.com.krypton.service.PromoService;

/**
 * Logica de cupones: alta, listado y aplicacion del descuento.
 *
 * <p>Aplicacion: PORCENTAJE descuenta value% del monto (redondeo HALF_UP a 2 decimales);
 * MONTO descuenta un importe fijo pero NUNCA mas que el total (min con el amount).
 */
@Service
public class PromoServiceImpl implements PromoService {

    private static final BigDecimal CIEN = BigDecimal.valueOf(100);

    private final PromoRepository promoRepository;
    private final PromoMapper promoMapper;

    public PromoServiceImpl(PromoRepository promoRepository, PromoMapper promoMapper) {
        this.promoRepository = promoRepository;
        this.promoMapper = promoMapper;
    }

    @Override
    @Transactional
    public PromoResponse crear(CreatePromoRequest req) {
        if (promoRepository.existsByCode(req.code())) {
            throw new DuplicatePromoCodeException("El código ya existe");
        }
        Promo promo = new Promo();
        promo.setCode(req.code());
        promo.setType(req.type());
        promo.setValue(req.value());
        promo.setActive(true);
        promo.setCreatedAt(Instant.now());
        promoRepository.save(promo);
        return promoMapper.toResponse(promo);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PromoResponse> listar() {
        return promoRepository.findAll().stream()
                .map(promoMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public DescuentoResponse aplicar(AplicarPromoRequest req) {
        Promo p = promoRepository.findByCode(req.code())
                .orElseThrow(() -> new PromoInvalidaException("Código inválido"));
        if (!p.isActive()) {
            throw new PromoInvalidaException("Código inactivo");
        }
        BigDecimal discount;
        if (p.getType() == DescuentoTipo.PORCENTAJE) {
            discount = req.amount().multiply(p.getValue())
                    .divide(CIEN, 2, RoundingMode.HALF_UP);
        } else {
            // MONTO: no descuenta mas que el total
            discount = p.getValue().min(req.amount());
        }
        BigDecimal finalAmount = req.amount().subtract(discount);
        return new DescuentoResponse(p.getCode(), discount, finalAmount);
    }
}
