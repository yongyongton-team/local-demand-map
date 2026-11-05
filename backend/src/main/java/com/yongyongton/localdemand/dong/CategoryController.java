package com.yongyongton.localdemand.dong;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CategoryController {

    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @GetMapping("/categories")
    public List<CategoryResponse> getCategories() {
        return categoryService.getCategories().stream()
                .map(category -> new CategoryResponse(
                        category.getId(),
                        category.getCode(),
                        category.getName(),
                        category.isMartCheck()
                ))
                .toList();
    }

    public record CategoryResponse(
            Long id,
            String code,
            String name,
            boolean martCheck
    ) {
    }
}
