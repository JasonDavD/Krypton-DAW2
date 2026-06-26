package pe.com.krypton.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import pe.com.krypton.dto.request.ItemCarritoRequest;
import pe.com.krypton.dto.request.UpdateQuantityRequest;
import pe.com.krypton.dto.response.CarritoResponse;
import pe.com.krypton.exception.InsufficientStockException;
import pe.com.krypton.exception.ResourceNotFoundException;
import pe.com.krypton.mapper.CarritoMapper;
import pe.com.krypton.entity.Carrito;
import pe.com.krypton.entity.ItemCarrito;
import pe.com.krypton.entity.Producto;
import pe.com.krypton.entity.Usuario;
import pe.com.krypton.repository.ItemCarritoRepository;
import pe.com.krypton.repository.CarritoRepository;
import pe.com.krypton.repository.ProductoRepository;
import pe.com.krypton.repository.UsuarioRepository;
import pe.com.krypton.service.impl.CarritoServiceImpl;

/**
 * Unit test de CarritoServiceImpl. Repos MOCKEADOS, CarritoMapper real, sin Spring, sin DB.
 * Strict TDD: RED → GREEN → REFACTOR por sub-grupo.
 */
@ExtendWith(MockitoExtension.class)
class CartServiceImplTest {

    @Mock CarritoRepository cartRepository;
    @Mock ItemCarritoRepository cartItemRepository;
    @Mock ProductoRepository productRepository;
    @Mock UsuarioRepository userRepository;
    @Mock CarritoService selfMock;

    CarritoServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new CarritoServiceImpl(cartRepository, cartItemRepository,
                productRepository, userRepository, new CarritoMapper(), selfMock);
    }

    // ─── helpers ────────────────────────────────────────────────────────────────

    private Usuario user(Long id, String email) {
        Usuario u = new Usuario();
        u.setId(id);
        u.setName("Test Usuario");
        u.setEmail(email);
        u.setPassword("pwd");
        return u;
    }

    private Carrito cart(Long id, Usuario user) {
        Carrito c = new Carrito();
        c.setId(id);
        c.setUser(user);
        Instant now = Instant.now();
        c.setCreatedAt(now);
        c.setUpdatedAt(now);
        return c;
    }

    private Producto product(Long id, String sku, int stock, boolean active) {
        Producto p = new Producto();
        p.setId(id);
        p.setSku(sku);
        p.setName("Producto " + sku);
        p.setPrice(new BigDecimal("99.90"));
        p.setStock(stock);
        p.setActive(active);
        return p;
    }

    private ItemCarrito cartItem(Long id, Carrito cart, Producto product, int qty) {
        ItemCarrito item = new ItemCarrito();
        item.setId(id);
        item.setCart(cart);
        item.setProduct(product);
        item.setQuantity(qty);
        return item;
    }

    // ─── getCart: empty case ─────────────────────────────────────────────────────

    @Test
    void getCart_returns_emptyCart_when_no_cart_exists() {
        Usuario u = user(1L, "test@krypton.pe");
        when(userRepository.findByEmail("test@krypton.pe")).thenReturn(Optional.of(u));
        when(cartRepository.findByUser(u)).thenReturn(Optional.empty());

        CarritoResponse resp = service.obtenerCarrito("test@krypton.pe");

        assertThat(resp.cartId()).isNull();
        assertThat(resp.items()).isEmpty();
        assertThat(resp.total()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(resp.updatedAt()).isNull();
        verify(cartRepository, never()).save(any());
    }

    @Test
    void getCart_returns_mapped_cart_when_present() {
        Usuario u = user(1L, "test@krypton.pe");
        Carrito c = cart(10L, u);
        Producto p = product(5L, "SKU-001", 10, true);
        ItemCarrito item = cartItem(100L, c, p, 2);

        when(userRepository.findByEmail("test@krypton.pe")).thenReturn(Optional.of(u));
        when(cartRepository.findByUser(u)).thenReturn(Optional.of(c));
        when(cartItemRepository.findByCart(c)).thenReturn(List.of(item));

        CarritoResponse resp = service.obtenerCarrito("test@krypton.pe");

        assertThat(resp.cartId()).isEqualTo(10L);
        assertThat(resp.items()).hasSize(1);
        // subtotal = 99.90 * 2 = 199.80
        assertThat(resp.total()).isEqualByComparingTo(new BigDecimal("199.80"));
        verify(cartRepository, never()).save(any());
    }

    // ─── attemptAddItem: insert path ─────────────────────────────────────────────

    @Test
    void attemptAddItem_inserts_new_item_when_product_not_in_cart() {
        Usuario u = user(1L, "test@krypton.pe");
        Carrito c = cart(10L, u);
        Producto p = product(5L, "SKU-001", 10, true);

        when(userRepository.findByEmail("test@krypton.pe")).thenReturn(Optional.of(u));
        when(cartRepository.findByUser(u)).thenReturn(Optional.of(c));
        when(productRepository.findById(5L)).thenReturn(Optional.of(p));
        when(cartItemRepository.findByCartAndProduct(c, p)).thenReturn(Optional.empty());
        when(cartItemRepository.saveAndFlush(any(ItemCarrito.class))).thenAnswer(inv -> {
            ItemCarrito ci = inv.getArgument(0);
            ci.setId(200L);
            return ci;
        });
        when(cartRepository.save(any(Carrito.class))).thenAnswer(inv -> inv.getArgument(0));
        when(cartItemRepository.findByCart(c)).thenReturn(List.of());

        CarritoResponse resp = service.intentarAgregarItem("test@krypton.pe", new ItemCarritoRequest(5L, 3));

        verify(cartItemRepository).saveAndFlush(any(ItemCarrito.class));
        ArgumentCaptor<Carrito> cartCaptor = ArgumentCaptor.forClass(Carrito.class);
        verify(cartRepository).save(cartCaptor.capture());
        assertThat(cartCaptor.getValue().getUpdatedAt()).isNotNull();
    }

    @Test
    void attemptAddItem_merges_when_product_already_in_cart() {
        Usuario u = user(1L, "test@krypton.pe");
        Carrito c = cart(10L, u);
        Producto p = product(5L, "SKU-001", 10, true);
        ItemCarrito existing = cartItem(100L, c, p, 2);

        when(userRepository.findByEmail("test@krypton.pe")).thenReturn(Optional.of(u));
        when(cartRepository.findByUser(u)).thenReturn(Optional.of(c));
        when(productRepository.findById(5L)).thenReturn(Optional.of(p));
        when(cartItemRepository.findByCartAndProduct(c, p)).thenReturn(Optional.of(existing));
        when(cartItemRepository.save(any(ItemCarrito.class))).thenAnswer(inv -> inv.getArgument(0));
        when(cartRepository.save(any(Carrito.class))).thenAnswer(inv -> inv.getArgument(0));
        when(cartItemRepository.findByCart(c)).thenReturn(List.of(existing));

        service.intentarAgregarItem("test@krypton.pe", new ItemCarritoRequest(5L, 3));

        // merge path uses save (not saveAndFlush), quantity summed = 2+3=5
        verify(cartItemRepository, never()).saveAndFlush(any());
        ArgumentCaptor<ItemCarrito> itemCaptor = ArgumentCaptor.forClass(ItemCarrito.class);
        verify(cartItemRepository).save(itemCaptor.capture());
        assertThat(itemCaptor.getValue().getQuantity()).isEqualTo(5);
    }

    // ─── attemptAddItem: stock exceeded ─────────────────────────────────────────

    @Test
    void attemptAddItem_throws_InsufficientStockException_when_qty_exceeds_stock() {
        Usuario u = user(1L, "test@krypton.pe");
        Carrito c = cart(10L, u);
        Producto p = product(5L, "SKU-001", 5, true);  // stock = 5
        ItemCarrito existing = cartItem(100L, c, p, 3);   // existing qty = 3

        when(userRepository.findByEmail("test@krypton.pe")).thenReturn(Optional.of(u));
        when(cartRepository.findByUser(u)).thenReturn(Optional.of(c));
        when(productRepository.findById(5L)).thenReturn(Optional.of(p));
        when(cartItemRepository.findByCartAndProduct(c, p)).thenReturn(Optional.of(existing));

        // Total would be 3+3=6 > stock=5
        assertThatThrownBy(() -> service.intentarAgregarItem("test@krypton.pe", new ItemCarritoRequest(5L, 3)))
                .isInstanceOf(InsufficientStockException.class);

        verify(cartItemRepository, never()).save(any());
        verify(cartItemRepository, never()).saveAndFlush(any());
    }

    // ─── attemptAddItem: inactive/missing product ────────────────────────────────

    @Test
    void attemptAddItem_throws_ResourceNotFoundException_for_inactive_product() {
        Usuario u = user(1L, "test@krypton.pe");
        Carrito c = cart(10L, u);
        Producto p = product(5L, "SKU-001", 10, false);  // inactive

        when(userRepository.findByEmail("test@krypton.pe")).thenReturn(Optional.of(u));
        when(cartRepository.findByUser(u)).thenReturn(Optional.of(c));
        when(productRepository.findById(5L)).thenReturn(Optional.of(p));

        assertThatThrownBy(() -> service.intentarAgregarItem("test@krypton.pe", new ItemCarritoRequest(5L, 1)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void attemptAddItem_throws_ResourceNotFoundException_for_missing_product() {
        Usuario u = user(1L, "test@krypton.pe");
        Carrito c = cart(10L, u);

        when(userRepository.findByEmail("test@krypton.pe")).thenReturn(Optional.of(u));
        when(cartRepository.findByUser(u)).thenReturn(Optional.of(c));
        when(productRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.intentarAgregarItem("test@krypton.pe", new ItemCarritoRequest(99L, 1)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ─── addItem concurrency wiring ──────────────────────────────────────────────

    @Test
    void addItem_calls_mergeOnConflict_when_attemptAddItem_throws_DataIntegrityViolation() {
        ItemCarritoRequest req = new ItemCarritoRequest(5L, 2);
        CarritoResponse mergeResult = new CarritoResponse(10L, List.of(), BigDecimal.ZERO, Instant.now());

        when(selfMock.intentarAgregarItem("test@krypton.pe", req))
                .thenThrow(new DataIntegrityViolationException("unique constraint"));
        when(selfMock.fusionarEnConflicto("test@krypton.pe", req)).thenReturn(mergeResult);

        CarritoResponse result = service.agregarItem("test@krypton.pe", req);

        verify(selfMock).fusionarEnConflicto("test@krypton.pe", req);
        assertThat(result).isSameAs(mergeResult);
    }

    @Test
    void addItem_returns_attemptAddItem_result_when_no_conflict() {
        ItemCarritoRequest req = new ItemCarritoRequest(5L, 2);
        CarritoResponse insertResult = new CarritoResponse(10L, List.of(), BigDecimal.TEN, Instant.now());

        when(selfMock.intentarAgregarItem("test@krypton.pe", req)).thenReturn(insertResult);

        CarritoResponse result = service.agregarItem("test@krypton.pe", req);

        verify(selfMock, never()).fusionarEnConflicto(any(), any());
        assertThat(result).isSameAs(insertResult);
    }

    // ─── updateItem ──────────────────────────────────────────────────────────────

    @Test
    void updateItem_replaces_quantity_and_returns_cart() {
        Usuario u = user(1L, "test@krypton.pe");
        Carrito c = cart(10L, u);
        Producto p = product(5L, "SKU-001", 10, true);
        ItemCarrito item = cartItem(100L, c, p, 2);

        when(userRepository.findByEmail("test@krypton.pe")).thenReturn(Optional.of(u));
        when(cartItemRepository.findById(100L)).thenReturn(Optional.of(item));
        when(productRepository.findById(5L)).thenReturn(Optional.of(p));
        when(cartItemRepository.save(any(ItemCarrito.class))).thenAnswer(inv -> inv.getArgument(0));
        when(cartRepository.save(any(Carrito.class))).thenAnswer(inv -> inv.getArgument(0));
        when(cartItemRepository.findByCart(c)).thenReturn(List.of(item));

        CarritoResponse resp = service.actualizarItem("test@krypton.pe", 100L, new UpdateQuantityRequest(7));

        ArgumentCaptor<ItemCarrito> itemCaptor = ArgumentCaptor.forClass(ItemCarrito.class);
        verify(cartItemRepository).save(itemCaptor.capture());
        assertThat(itemCaptor.getValue().getQuantity()).isEqualTo(7);
    }

    @Test
    void updateItem_throws_ResourceNotFoundException_for_IDOR() {
        Usuario u = user(1L, "test@krypton.pe");
        Usuario otherUser = user(2L, "other@krypton.pe");
        Carrito otherCart = cart(20L, otherUser);
        Producto p = product(5L, "SKU-001", 10, true);
        ItemCarrito item = cartItem(99L, otherCart, p, 2);  // owned by otherUser

        when(userRepository.findByEmail("test@krypton.pe")).thenReturn(Optional.of(u));
        when(cartItemRepository.findById(99L)).thenReturn(Optional.of(item));

        assertThatThrownBy(() -> service.actualizarItem("test@krypton.pe", 99L, new UpdateQuantityRequest(5)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void updateItem_throws_404_when_item_not_found() {
        Usuario u = user(1L, "test@krypton.pe");
        when(userRepository.findByEmail("test@krypton.pe")).thenReturn(Optional.of(u));
        when(cartItemRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.actualizarItem("test@krypton.pe", 999L, new UpdateQuantityRequest(5)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void updateItem_throws_404_for_inactive_product() {
        Usuario u = user(1L, "test@krypton.pe");
        Carrito c = cart(10L, u);
        Producto p = product(5L, "SKU-001", 10, false);  // inactive
        ItemCarrito item = cartItem(100L, c, p, 2);

        when(userRepository.findByEmail("test@krypton.pe")).thenReturn(Optional.of(u));
        when(cartItemRepository.findById(100L)).thenReturn(Optional.of(item));
        when(productRepository.findById(5L)).thenReturn(Optional.of(p));

        assertThatThrownBy(() -> service.actualizarItem("test@krypton.pe", 100L, new UpdateQuantityRequest(3)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void updateItem_throws_422_when_qty_exceeds_stock() {
        Usuario u = user(1L, "test@krypton.pe");
        Carrito c = cart(10L, u);
        Producto p = product(5L, "SKU-001", 4, true);  // stock = 4
        ItemCarrito item = cartItem(100L, c, p, 2);

        when(userRepository.findByEmail("test@krypton.pe")).thenReturn(Optional.of(u));
        when(cartItemRepository.findById(100L)).thenReturn(Optional.of(item));
        when(productRepository.findById(5L)).thenReturn(Optional.of(p));

        assertThatThrownBy(() -> service.actualizarItem("test@krypton.pe", 100L, new UpdateQuantityRequest(5)))
                .isInstanceOf(InsufficientStockException.class);
    }

    // ─── removeItem ─────────────────────────────────────────────────────────────

    @Test
    void removeItem_deletes_item_and_touches_cart() {
        Usuario u = user(1L, "test@krypton.pe");
        Carrito c = cart(10L, u);
        Producto p = product(5L, "SKU-001", 10, true);
        ItemCarrito item = cartItem(100L, c, p, 2);

        when(userRepository.findByEmail("test@krypton.pe")).thenReturn(Optional.of(u));
        when(cartItemRepository.findById(100L)).thenReturn(Optional.of(item));
        when(cartRepository.save(any(Carrito.class))).thenAnswer(inv -> inv.getArgument(0));

        service.quitarItem("test@krypton.pe", 100L);

        verify(cartItemRepository).delete(item);
        ArgumentCaptor<Carrito> cartCaptor = ArgumentCaptor.forClass(Carrito.class);
        verify(cartRepository).save(cartCaptor.capture());
        assertThat(cartCaptor.getValue().getUpdatedAt()).isNotNull();
    }

    @Test
    void removeItem_throws_ResourceNotFoundException_for_IDOR() {
        Usuario u = user(1L, "test@krypton.pe");
        Usuario otherUser = user(2L, "other@krypton.pe");
        Carrito otherCart = cart(20L, otherUser);
        Producto p = product(5L, "SKU-001", 10, true);
        ItemCarrito item = cartItem(99L, otherCart, p, 2);

        when(userRepository.findByEmail("test@krypton.pe")).thenReturn(Optional.of(u));
        when(cartItemRepository.findById(99L)).thenReturn(Optional.of(item));

        assertThatThrownBy(() -> service.quitarItem("test@krypton.pe", 99L))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(cartItemRepository, never()).delete(any());
    }

    @Test
    void removeItem_throws_404_when_item_not_found() {
        Usuario u = user(1L, "test@krypton.pe");
        when(userRepository.findByEmail("test@krypton.pe")).thenReturn(Optional.of(u));
        when(cartItemRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.quitarItem("test@krypton.pe", 999L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ─── clearCart ──────────────────────────────────────────────────────────────

    @Test
    void clearCart_deletes_all_items_and_touches_cart_when_cart_exists() {
        Usuario u = user(1L, "test@krypton.pe");
        Carrito c = cart(10L, u);

        when(userRepository.findByEmail("test@krypton.pe")).thenReturn(Optional.of(u));
        when(cartRepository.findByUser(u)).thenReturn(Optional.of(c));
        when(cartRepository.save(any(Carrito.class))).thenAnswer(inv -> inv.getArgument(0));

        service.vaciarCarrito("test@krypton.pe");

        verify(cartItemRepository).deleteByCart(c);
        ArgumentCaptor<Carrito> cartCaptor = ArgumentCaptor.forClass(Carrito.class);
        verify(cartRepository).save(cartCaptor.capture());
        assertThat(cartCaptor.getValue().getUpdatedAt()).isNotNull();
    }

    @Test
    void clearCart_is_noop_when_no_cart_exists() {
        Usuario u = user(1L, "test@krypton.pe");

        when(userRepository.findByEmail("test@krypton.pe")).thenReturn(Optional.of(u));
        when(cartRepository.findByUser(u)).thenReturn(Optional.empty());

        service.vaciarCarrito("test@krypton.pe");

        verify(cartItemRepository, never()).deleteByCart(any());
        verify(cartRepository, never()).save(any());
    }

    // ─── getOrCreateCart — lazy creation ────────────────────────────────────────

    @Test
    void attemptAddItem_creates_cart_when_no_cart_exists() {
        Usuario u = user(1L, "test@krypton.pe");
        Producto p = product(5L, "SKU-001", 10, true);

        when(userRepository.findByEmail("test@krypton.pe")).thenReturn(Optional.of(u));
        when(cartRepository.findByUser(u)).thenReturn(Optional.empty());
        when(cartRepository.saveAndFlush(any(Carrito.class))).thenAnswer(inv -> {
            Carrito c = inv.getArgument(0);
            c.setId(10L);
            return c;
        });
        when(productRepository.findById(5L)).thenReturn(Optional.of(p));
        when(cartItemRepository.findByCartAndProduct(any(Carrito.class), any(Producto.class)))
                .thenReturn(Optional.empty());
        when(cartItemRepository.saveAndFlush(any(ItemCarrito.class))).thenAnswer(inv -> {
            ItemCarrito ci = inv.getArgument(0);
            ci.setId(200L);
            return ci;
        });
        when(cartRepository.save(any(Carrito.class))).thenAnswer(inv -> inv.getArgument(0));
        when(cartItemRepository.findByCart(any(Carrito.class))).thenReturn(List.of());

        service.intentarAgregarItem("test@krypton.pe", new ItemCarritoRequest(5L, 2));

        verify(cartRepository).saveAndFlush(any(Carrito.class));
    }
}
