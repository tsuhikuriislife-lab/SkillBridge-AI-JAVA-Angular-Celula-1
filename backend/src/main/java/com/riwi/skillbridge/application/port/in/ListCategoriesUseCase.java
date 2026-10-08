package com.riwi.skillbridge.application.port.in;

import com.riwi.skillbridge.application.model.CategoryOption;

import java.util.List;

public interface ListCategoriesUseCase {
    List<CategoryOption> list();
}