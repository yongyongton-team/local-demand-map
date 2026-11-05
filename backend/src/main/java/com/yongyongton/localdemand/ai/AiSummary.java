package com.yongyongton.localdemand.ai;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 검증을 통과한 AI 요약 문장 (V1 ai_summary). 같은 숫자(input_hash)로는 AI를 다시 부르지 않고 이 문장을 써요.
 * 기본 문장(TEMPLATE)은 저장하지 않아요.
 */
@Entity
@Table(name = "ai_summary")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AiSummary {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "dong_id", nullable = false)
    private Long dongId;

    @Column(name = "category_id", nullable = false)
    private Long categoryId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "kind", nullable = false, length = 20)
    private SummaryKind kind;

    /** AI에 넣은 숫자 JSON의 SHA-256 (지시문 버전 포함). */
    @Column(name = "input_hash", nullable = false, length = 64)
    private String inputHash;

    @Column(name = "text", nullable = false, length = 1000)
    private String text;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public AiSummary(Long dongId, Long categoryId, SummaryKind kind, String inputHash, String text) {
        this.dongId = dongId;
        this.categoryId = categoryId;
        this.kind = kind;
        this.inputHash = inputHash;
        this.text = text;
    }
}
