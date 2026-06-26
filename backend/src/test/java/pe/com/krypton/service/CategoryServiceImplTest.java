package pe.com.krypton.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pe.com.krypton.dto.request.CategoryRequest;
import pe.com.krypton.dto.response.CategoryResponse;
import pe.com.krypton.exception.CategoryInUseException;
import pe.com.krypton.exception.DuplicateCategoryNameException;
import pe.com.krypton.exception.ResourceNotFoundException;
import pe.com.krypton.mapper.CategoriaMapper;
import pe.com.krypton.entity.Categoria;
import pe.com.krypton.repository.CategoriaRepository;
import pe.com.krypton.repository.ProductoRepository;
import pe.com.krypton.service.impl.CategoriaServiceImpl;

/**
 * Unit test de CategoriaServiceImpl. Repos MOCKEADOS, sin Spring context, sin DB.
 * TDD: RED escrito antes de que exista CategoriaServiceImpl.
 */
@ExtendWith(MockitoExtension.class)
class CategoryServiceImplTest {

    @Mock CategoriaRepository categoryRepository;
    @Mock ProductoRepository productRepository;

    CategoriaServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new CategoriaServiceImpl(categoryRepository, productRepository, new CategoriaMapper());
    }

    // ─── helpers ────────────────────────────────────────────────────────────────

    private Categoria category(Long id, String name) {
        Categoria c = new Categoria();
        c.setId(id);
        c.setName(name);
        c.setDescription("Desc of " + name);
        return c;
    }

    // ─── list ───────────────────────────────────────────────────────────────────

    @Test
    void should_return_all_categories() {
        when(categoryRepository.findAll())
                .thenReturn(List.of(category(1L, "Electronics"), category(2L, "Books")));

        List<CategoryResponse> result = service.list();

        assertThat(result).hasSize(2);
        assertThat(result.get(0).name()).isEqualTo("Electronics");
    }

    // ─── getById ────────────────────────────────────────────────────────────────

    @Test
    void should_return_category_when_found() {
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category(1L, "Electronics")));

        CategoryResponse result = service.getById(1L);

        assertThat(result.id()).isEqualTo(1L);
        assertThat(result.name()).isEqualTo("Electronics");
    }

    @Test
    void should_throw_not_found_when_category_missing() {
        when(categoryRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById(99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ─── create ─────────────────────────────────────────────────────────────────

    @Test
    void should_create_category_when_name_is_unique() {
        CategoryRequest req = new CategoryRequest("NewCat", "A new category");
        when(categoryRepository.existsByName("NewCat")).thenReturn(false);
        when(categoryRepository.save(any(Categoria.class))).thenAnswer(inv -> {
            Categoria c = inv.getArgument(0);
            c.setId(5L);
            return c;
        });

        CategoryResponse result = service.create(req);

        assertThat(result.id()).isEqualTo(5L);
        assertThat(result.name()).isEqualTo("NewCat");
    }

    @Test
    void should_reject_create_when_name_already_exists() {
        when(categoryRepository.existsByName("Electronics")).thenReturn(true);

        assertThatThrownBy(() -> service.create(new CategoryRequest("Electronics", "Desc")))
                .isInstanceOf(DuplicateCategoryNameException.class);
        verify(categoryRepository, never()).save(any());
    }

    // ─── update ─────────────────────────────────────────────────────────────────

    @Test
    void should_update_category_when_new_name_is_unique() {
        Categoria existing = category(1L, "OldName");
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(categoryRepository.existsByNameAndIdNot("NewName", 1L)).thenReturn(false);
        when(categoryRepository.save(any(Categoria.class))).thenAnswer(inv -> inv.getArgument(0));

        CategoryResponse result = service.update(1L, new CategoryRequest("NewName", "Updated desc"));

        assertThat(result.name()).isEqualTo("NewName");
    }

    @Test
    void should_allow_update_keeping_same_name() {
        // Updating own name must NOT throw — existsByNameAndIdNot excludes self
        Categoria existing = category(1L, "Electronics");
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(categoryRepository.existsByNameAndIdNot("Electronics", 1L)).thenReturn(false);
        when(categoryRepository.save(any(Categoria.class))).thenAnswer(inv -> inv.getArgument(0));

        CategoryResponse result = service.update(1L, new CategoryRequest("Electronics", "Updated desc"));

        assertThat(result.name()).isEqualTo("Electronics");
    }

    @Test
    void should_reject_update_when_name_belongs_to_another_category() {
        Categoria existing = category(1L, "OldName");
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(categoryRepository.existsByNameAndIdNot("TakenName", 1L)).thenReturn(true);

        assertThatThrownBy(() -> service.update(1L, new CategoryRequest("TakenName", "Desc")))
                .isInstanceOf(DuplicateCategoryNameException.class);
        verify(categoryRepository, never()).save(any());
    }

    // ─── delete ─────────────────────────────────────────────────────────────────

    @Test
    void should_delete_category_when_no_products_reference_it() {
        Categoria existing = category(3L, "Empty");
        when(categoryRepository.findById(3L)).thenReturn(Optional.of(existing));
        when(productRepository.existsByCategoryId(3L)).thenReturn(false);

        service.delete(3L);

        verify(categoryRepository).delete(existing);
    }

    @Test
    void should_throw_category_in_use_when_products_exist() {
        Categoria existing = category(1L, "Electronics");
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(productRepository.existsByCategoryId(1L)).thenReturn(true);

        assertThatThrownBy(() -> service.delete(1L))
                .isInstanceOf(CategoryInUseException.class);
        verify(categoryRepository, never()).delete(any(Categoria.class));
    }

    @Test
    void should_throw_not_found_on_delete_when_category_missing() {
        when(categoryRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(99L))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(categoryRepository, never()).delete(any(Categoria.class));
    }
}
