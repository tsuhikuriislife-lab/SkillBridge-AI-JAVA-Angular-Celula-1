package com.riwi.skillbridge.infrastructure.adapter.in.rest;

import com.riwi.skillbridge.application.model.CategoryOption;
import com.riwi.skillbridge.application.port.in.ListCategoriesUseCase;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/categories")
public class CategoryController {
    private final ListCategoriesUseCase useCase;

    public CategoryController(ListCategoriesUseCase useCase) {
        this.useCase = useCase;
    }

    @GetMapping
    public List<CategoryOption> list() {
        return useCase.list();
    }
}