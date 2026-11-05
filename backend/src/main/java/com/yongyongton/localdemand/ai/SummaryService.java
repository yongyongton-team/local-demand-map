package com.yongyongton.localdemand.ai;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

/**
 * 요약 문장 (명세 6-2). 화면 5(사장님)와 화면 7(창업자 리포트)이 같이 써요.
 * <ol>
 *   <li>같은 숫자로 저장해 둔 문장이 있으면 그대로 써요(AI를 다시 부르지 않음)</li>
 *   <li>없으면 AI에 숫자만 넣어 문장을 받고, {@link SummaryValidator}로 검사해요</li>
 *   <li>AI 실패·검사 탈락이면 부르는 쪽이 준 기본 문장(by: TEMPLATE)을 써요. 오류로 바꾸지 않아요</li>
 * </ol>
 * AI를 기다리는 동안 DB 연결을 잡고 있지 않게, 부르는 쪽은 트랜잭션 밖에서 불러요.
 */
@Service
public class SummaryService {

    /** 지시문을 바꾸면 이 값을 올려요. 옛 지시문으로 쓴 저장 문장을 다시 쓰지 않게 돼요. */
    static final String PROMPT_VERSION = "v1";

    private static final Logger log = LoggerFactory.getLogger(SummaryService.class);

    private final AiWriter aiWriter;
    private final AiSummaryRepository repository;
    private final ObjectMapper objectMapper;

    public SummaryService(AiWriter aiWriter, AiSummaryRepository repository, ObjectMapper objectMapper) {
        this.aiWriter = aiWriter;
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    /** 응답의 summary / explanation 모양: { "text": "...", "by": "AI" | "TEMPLATE" } */
    public record Summary(String text, String by) {

        public static Summary template(String text) {
            return new Summary(text, "TEMPLATE");
        }
    }

    /**
     * @param facts        AI에 넣을 숫자·이름. 서버가 계산한 값만 넣어요
     * @param templateText AI를 못 쓸 때 보여 줄 기본 문장
     */
    public Summary write(SummaryKind kind, long dongId, long categoryId, Map<String, Object> facts, String templateText) {
        String hash = hash(kind, facts);
        var saved = repository.findByDongIdAndCategoryIdAndKindAndInputHash(dongId, categoryId, kind, hash);
        if (saved.isPresent()) {
            return new Summary(saved.get().getText(), "AI");
        }

        String text;
        try {
            text = aiWriter.writeSummary(kind, facts);
        } catch (AiUnavailableException e) {
            log.info("요약 문장: 기본 문장 사용 (kind={}, 이유={})", kind, e.getMessage());
            return Summary.template(templateText);
        }
        if (!SummaryValidator.isValid(text, facts)) {
            log.info("요약 문장: 검사에서 떨어져 기본 문장 사용 (kind={})", kind);
            return Summary.template(templateText);
        }

        try {
            repository.save(new AiSummary(dongId, categoryId, kind, hash, text));
        } catch (DataIntegrityViolationException e) {
            // 같은 문장을 동시에 저장하려 한 경우. 이미 저장됐으니 그냥 넘어가요
        }
        return new Summary(text, "AI");
    }

    /** 지시문 버전 + 화면 + 숫자 JSON(키 이름 순으로 정렬)의 SHA-256. */
    String hash(SummaryKind kind, Map<String, Object> facts) {
        String input = PROMPT_VERSION + "|" + kind + "|" + objectMapper.writeValueAsString(sorted(facts));
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** 같은 숫자면 넣은 순서가 달라도 같은 해시가 나오게 맵의 키를 정렬해요. */
    private static Object sorted(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new TreeMap<>();
            map.forEach((k, v) -> result.put(String.valueOf(k), sorted(v)));
            return result;
        }
        if (value instanceof Collection<?> list) {
            return list.stream().map(SummaryService::sorted).toList();
        }
        return value;
    }
}
