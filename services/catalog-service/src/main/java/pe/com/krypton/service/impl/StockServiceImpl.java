package pe.com.krypton.service.impl;

import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.com.krypton.dto.request.StockItemRequest;
import pe.com.krypton.dto.request.StockMovementRequest;
import pe.com.krypton.entity.MovimientoStock;
import pe.com.krypton.entity.Producto;
import pe.com.krypton.entity.enums.TipoMovimiento;
import pe.com.krypton.exception.InsufficientStockException;
import pe.com.krypton.exception.ResourceNotFoundException;
import pe.com.krypton.repository.MovimientoStockRepository;
import pe.com.krypton.repository.ProductoRepository;
import pe.com.krypton.service.StockService;

@Service
public class StockServiceImpl implements StockService {

    private final ProductoRepository productoRepository;
    private final MovimientoStockRepository movimientoRepository;

    public StockServiceImpl(ProductoRepository productoRepository,
                            MovimientoStockRepository movimientoRepository) {
        this.productoRepository = productoRepository;
        this.movimientoRepository = movimientoRepository;
    }

    @Override
    @Transactional
    public void descontar(StockMovementRequest request) {
        for (StockItemRequest item : request.items()) {
            // Lock pesimista: nadie más toca este producto hasta que cierre la transacción.
            Producto producto = productoRepository.findByIdWithLock(item.productId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Producto no encontrado: " + item.productId()));

            if (item.quantity() > producto.getStock()) {
                // 422: corta TODA la transacción → lo ya descontado se revierte (rollback).
                throw new InsufficientStockException(
                        "Stock insuficiente para el producto " + item.productId()
                                + ": solicitado=" + item.quantity()
                                + ", disponible=" + producto.getStock());
            }

            // 1) actualizar el valor cacheado products.stock
            producto.setStock(producto.getStock() - item.quantity());

            // 2) registrar el movimiento en el kardex (stock_movement) — JUNTOS, misma transacción
            MovimientoStock movimiento = new MovimientoStock();
            movimiento.setProduct(producto);
            movimiento.setType(TipoMovimiento.SALIDA);
            movimiento.setQuantity(item.quantity());
            movimiento.setReason("Salida por " + request.reference());
            movimiento.setReference(request.reference());
            movimiento.setCreatedAt(Instant.now());
            movimiento.setCreatedBy(request.createdBy());
            movimientoRepository.save(movimiento);
        }
    }

    @Override
    @Transactional
    public void restaurar(StockMovementRequest request) {
        for (StockItemRequest item : request.items()) {
            Producto producto = productoRepository.findByIdWithLock(item.productId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Producto no encontrado: " + item.productId()));

            // Compensación: solo SUMA de vuelta (no hay tope que validar).
            producto.setStock(producto.getStock() + item.quantity());

            MovimientoStock movimiento = new MovimientoStock();
            movimiento.setProduct(producto);
            movimiento.setType(TipoMovimiento.ENTRADA);
            movimiento.setQuantity(item.quantity());
            movimiento.setReason("Reposición por " + request.reference());
            movimiento.setReference(request.reference());
            movimiento.setCreatedAt(Instant.now());
            movimiento.setCreatedBy(request.createdBy());
            movimientoRepository.save(movimiento);
        }
    }
}
