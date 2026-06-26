package pe.com.krypton.service.impl;

import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.com.krypton.dto.request.CategoryRequest;
import pe.com.krypton.dto.response.CategoryResponse;
import pe.com.krypton.exception.CategoryInUseException;
import pe.com.krypton.exception.DuplicateCategoryNameException;
import pe.com.krypton.exception.ResourceNotFoundException;
import pe.com.krypton.mapper.CategoriaMapper;
import pe.com.krypton.entity.Categoria;
import pe.com.krypton.repository.CategoriaRepository;
import pe.com.krypton.repository.ProductoRepository;
import pe.com.krypton.service.CategoriaService;

@Service
public class CategoriaServiceImpl implements CategoriaService {

    private final CategoriaRepository categoryRepository;
    private final ProductoRepository productRepository;
    private final CategoriaMapper categoryMapper;

    public CategoriaServiceImpl(CategoriaRepository categoryRepository,
                                ProductoRepository productRepository,
                                CategoriaMapper categoryMapper) {
        this.categoryRepository = categoryRepository;
        this.productRepository = productRepository;
        this.categoryMapper = categoryMapper;
    }

    @Override
    @Transactional(readOnly = true)
    public List<CategoryResponse> list() {
        return categoryRepository.findAll().stream()
                .map(categoryMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public CategoryResponse getById(Long id) {
        return categoryMapper.toResponse(findOrThrow(id));
    }

    @Override
    @Transactional
    public CategoryResponse create(CategoryRequest request) {
        if (categoryRepository.existsByName(request.name())) {
            throw new DuplicateCategoryNameException("El nombre de categoría ya está registrado: " + request.name());
        }
        Categoria category = new Categoria();
        category.setName(request.name());
        category.setDescription(request.description());
        return categoryMapper.toResponse(categoryRepository.save(category));
    }

    @Override
    @Transactional
    public CategoryResponse update(Long id, CategoryRequest request) {
        Categoria category = findOrThrow(id);

        if (categoryRepository.existsByNameAndIdNot(request.name(), id)) {
            throw new DuplicateCategoryNameException(
                    "El nombre de categoría ya está registrado en otra categoría: " + request.name());
        }
        category.setName(request.name());
        category.setDescription(request.description());
        return categoryMapper.toResponse(categoryRepository.save(category));
    }

    @Override
    @Transactional
    public void delete(Long id) {
        Categoria category = findOrThrow(id);

        // Guard: evaluar ANTES de cualquier escritura (diseño: regla de negocio en service)
        if (productRepository.existsByCategoryId(id)) {
            throw new CategoryInUseException(
                    "La categoría tiene productos asociados y no puede eliminarse: " + id);
        }
        categoryRepository.delete(category);
    }

    // ─── private helpers ────────────────────────────────────────────────────────

    private Categoria findOrThrow(Long id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Categoría no encontrada: " + id));
    }
}
