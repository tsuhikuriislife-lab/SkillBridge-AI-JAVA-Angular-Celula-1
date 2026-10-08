package com.riwi.skillbridge.application.service;

import com.riwi.skillbridge.application.model.CategoryOption;
import com.riwi.skillbridge.application.port.in.ListCategoriesUseCase;
import com.riwi.skillbridge.application.port.out.CategoryPort;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CategoryService implements ListCategoriesUseCase {
    private final CategoryPort categories;

    public CategoryService(CategoryPort categories) {
        this.categories = categories;
    }

    @Override
    public List<CategoryOption> list() {
        return categories.findAll();
    }
}