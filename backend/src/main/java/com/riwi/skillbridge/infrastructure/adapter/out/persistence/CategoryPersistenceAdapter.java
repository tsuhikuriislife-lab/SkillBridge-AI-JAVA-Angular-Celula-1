package com.riwi.skillbridge.infrastructure.adapter.out.persistence;

import com.riwi.skillbridge.application.model.CategoryOption;
import com.riwi.skillbridge.application.port.out.CategoryPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
public class CategoryPersistenceAdapter implements CategoryPort {
    private final JdbcTemplate jdbc;

    public CategoryPersistenceAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean existsById(UUID id) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM categories WHERE id = ? AND status = 'ACTIVE'", Integer.class, id);
        return count != null && count > 0;
    }

    @Override
    public List<CategoryOption> findAll() {
        return jdbc.query(
                "SELECT id, name FROM categories WHERE status = 'ACTIVE' ORDER BY name ASC",
                (rs, rowNum) -> new CategoryOption(rs.getObject("id", UUID.class), rs.getString("name")));
    }
}