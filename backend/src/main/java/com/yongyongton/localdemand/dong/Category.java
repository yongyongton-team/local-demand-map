package com.yongyongton.localdemand.dong;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "category")
public class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "code", nullable = false, length = 30)
    private String code;

    @Column(name = "name", nullable = false, length = 30)
    private String name;

    @Column(name = "sbiz_name", nullable = false, length = 50)
    private String sbizName;

    @Column(name = "mart_check", nullable = false)
    private boolean martCheck;

    protected Category() {
    }

    public Long getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public String getSbizName() {
        return sbizName;
    }

    public boolean isMartCheck() {
        return martCheck;
    }
}
