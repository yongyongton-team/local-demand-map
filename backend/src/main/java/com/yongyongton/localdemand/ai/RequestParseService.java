package com.yongyongton.localdemand.ai;

import com.yongyongton.localdemand.ai.AiWriter.CategoryOption;
import com.yongyongton.localdemand.ai.AiWriter.ConditionOption;
import com.yongyongton.localdemand.ai.AiWriter.ParsedRequest;
import com.yongyongton.localdemand.ai.ParseDtos.ConditionView;
import com.yongyongton.localdemand.ai.ParseDtos.IdName;
import com.yongyongton.localdemand.ai.ParseDtos.ParseRequest;
import com.yongyongton.localdemand.ai.ParseDtos.ParseResponse;
import com.yongyongton.localdemand.auth.AppUser;
import com.yongyongton.localdemand.auth.CurrentUser;
import com.yongyongton.localdemand.auth.Role;
import com.yongyongton.localdemand.common.error.ApiException;
import com.yongyongton.localdemand.common.error.ErrorCode;
import com.yongyongton.localdemand.dong.Category;
import com.yongyongton.localdemand.request.RequestCondition;
import com.yongyongton.localdemand.request.ShopRequestRepository;
import jakarta.persistence.EntityManager;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

/**
 * 요청 문장 정리 (명세 5-3 POST /requests/parse, 6-1). 저장하지 않아요.
 * AI가 낸 코드는 목록에 있는 것만 남겨요. AI를 쓸 수 없으면 502 AI_UNAVAILABLE → 프론트는 업종 칩으로 대신 고르게 해요.
 * AI를 기다리는 동안 DB 연결을 잡지 않게 트랜잭션을 쓰지 않아요(읽기만 해요).
 */
@Service
public class RequestParseService {

    static final int MAX_CANDIDATES = 3;

    private static final Logger log = LoggerFactory.getLogger(RequestParseService.class);

    private final CurrentUser currentUser;
    private final AiWriter aiWriter;
    private final ShopRequestRepository requestRepository;
    private final EntityManager em;

    public RequestParseService(CurrentUser currentUser, AiWriter aiWriter, ShopRequestRepository requestRepository,
                               EntityManager em) {
        this.currentUser = currentUser;
        this.aiWriter = aiWriter;
        this.requestRepository = requestRepository;
        this.em = em;
    }

    /** 주민만(아니면 403), 없는 동이면 404. */
    public ParseResponse parse(Jwt jwt, ParseRequest req) {
        AppUser user = currentUser.require(jwt, Role.RESIDENT);
        Long dongCount = em.createQuery("SELECT COUNT(d) FROM Dong d WHERE d.id = :id", Long.class)
                .setParameter("id", req.dongId())
                .getSingleResult();
        if (dongCount == 0) {
            throw new ApiException(ErrorCode.NOT_FOUND, "없는 동이에요.");
        }

        List<Category> categories = em.createQuery("SELECT c FROM Category c ORDER BY c.id", Category.class)
                .getResultList();
        Map<String, Category> byCode = categories.stream()
                .collect(Collectors.toMap(Category::getCode, Function.identity(), (a, b) -> a, LinkedHashMap::new));

        ParsedRequest parsed;
        try {
            parsed = aiWriter.parseRequest(req.text().trim(),
                    categories.stream().map(c -> new CategoryOption(c.getCode(), c.getName(), c.getSbizName())).toList(),
                    Arrays.stream(RequestCondition.values()).map(c -> new ConditionOption(c.name(), c.label())).toList());
        } catch (AiUnavailableException e) {
            log.info("요청 정리 실패 (이유={})", e.getMessage());
            throw new ApiException(ErrorCode.AI_UNAVAILABLE);
        }

        // 목록에 없는 코드는 버려요
        Category picked = parsed.categoryCode() == null ? null : byCode.get(parsed.categoryCode());
        List<ConditionView> conditions = parsed.conditions().stream()
                .map(RequestParseService::condition)
                .filter(c -> c != null)
                .distinct()
                .map(c -> new ConditionView(c, c.label()))
                .toList();

        List<IdName> candidates = List.of();
        if (picked == null) {
            Set<Category> chosen = new LinkedHashSet<>();
            parsed.candidateCodes().stream().map(byCode::get).filter(c -> c != null).forEach(chosen::add);
            if (chosen.size() < MAX_CANDIDATES) {
                // AI 후보가 모자라면 이 동네에서 요청이 많은 업종으로 채워요
                Map<Long, Category> byId = categories.stream()
                        .collect(Collectors.toMap(Category::getId, Function.identity()));
                mostRequested(req.dongId()).stream().map(byId::get).filter(c -> c != null).forEach(chosen::add);
            }
            candidates = chosen.stream().limit(MAX_CANDIDATES).map(c -> new IdName(c.getId(), c.getName())).toList();
        }

        boolean alreadyRequested = picked != null
                && requestRepository.existsByUserIdAndDongIdAndCategoryId(user.getId(), req.dongId(), picked.getId());
        return new ParseResponse(picked == null ? null : new IdName(picked.getId(), picked.getName()),
                conditions, alreadyRequested, candidates, aiWriter.by());
    }

    /** 이 동네에서 요청이 많은 업종 id (많은 순). */
    private List<Long> mostRequested(Long dongId) {
        return em.createQuery("SELECT r.categoryId FROM ShopRequest r WHERE r.dongId = :dongId"
                        + " GROUP BY r.categoryId ORDER BY COUNT(r) DESC, r.categoryId", Long.class)
                .setParameter("dongId", dongId)
                .setMaxResults(MAX_CANDIDATES)
                .getResultList();
    }

    private static RequestCondition condition(String code) {
        try {
            return RequestCondition.valueOf(code);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
