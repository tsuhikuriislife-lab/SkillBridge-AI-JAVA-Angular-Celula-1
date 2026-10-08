package com.riwi.skillbridge.application.port.out;

import com.riwi.skillbridge.application.model.CategoryOption;

import java.util.List;
import java.util.UUID;

public interface CategoryPort {
    boolean existsById(UUID id);

    List<CategoryOption> findAll();
}