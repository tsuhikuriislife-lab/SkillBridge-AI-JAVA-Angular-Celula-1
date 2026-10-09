package com.riwi.skillbridge.infrastructure.adapter.out.persistence.entity;

import com.riwi.skillbridge.domain.enums.CatalogType;
import com.riwi.skillbridge.domain.enums.Status;
import jakarta.persistence.*;

import java.util.UUID;

@Entity
@Table(name = "catalog_items")
public class CatalogItemEntity {

    @Id
    private UUID id;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, unique = true, length = 50)
    private String code;

    @Column(length = 500)
    private String detail;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private CatalogType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Status status;

    protected CatalogItemEntity() {}

    public CatalogItemEntity(UUID id, String name, String code, String detail,
                             CatalogType type, Status status) {
        this.id = id;
        this.name = name;
        this.code = code;
        this.detail = detail;
        this.type = type;
        this.status = status;
    }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public String getCode() { return code; }
    public String getDetail() { return detail; }
    public CatalogType getType() { return type; }
    public Status getStatus() { return status; }
}
