package com.yongyongton.localdemand.request;

import com.yongyongton.localdemand.auth.AppUser;
import com.yongyongton.localdemand.auth.CurrentUser;
import com.yongyongton.localdemand.auth.OwnerShop;
import com.yongyongton.localdemand.auth.OwnerShopRepository;
import com.yongyongton.localdemand.auth.Role;
import com.yongyongton.localdemand.common.KstTime;
import com.yongyongton.localdemand.common.error.ApiException;
import com.yongyongton.localdemand.common.error.ErrorCode;
import com.yongyongton.localdemand.dong.Category;
import com.yongyongton.localdemand.dong.Dong;
import com.yongyongton.localdemand.offer.OfferService;
import com.yongyongton.localdemand.request.RequestDtos.CreateRequest;
import com.yongyongton.localdemand.request.RequestDtos.CreateResponse;
import com.yongyongton.localdemand.request.RequestDtos.IdName;
import com.yongyongton.localdemand.request.RequestDtos.MyRequest;
import com.yongyongton.localdemand.request.RequestDtos.RequestSummary;
import jakarta.persistence.EntityManager;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 주민 요청 (명세 5-3). */
@Service
public class RequestService {

    /** 사장님에게 알리는 거리: 동 중심에서 가게까지 직선 3km (명세 notifiedOwnerCount). */
    private static final double NOTIFY_RADIUS_KM = 3.0;

    private final CurrentUser currentUser;
    private final ShopRequestRepository requestRepository;
    private final OwnerShopRepository ownerShopRepository;
    private final OfferService offerService;
    private final EntityManager em;
    private final int goal;

    public RequestService(CurrentUser currentUser, ShopRequestRepository requestRepository,
                          OwnerShopRepository ownerShopRepository, OfferService offerService, EntityManager em,
                          @Value("${app.request.goal:30}") int goal) {
        this.currentUser = currentUser;
        this.requestRepository = requestRepository;
        this.ownerShopRepository = ownerShopRepository;
        this.offerService = offerService;
        this.em = em;
        this.goal = goal;
    }

    /**
     * POST /requests · 주민만(아니면 403), 내 동네에만(아니면 400), 없는 업종이면 404,
     * 같은 동·같은 업종을 또 보내면 409 ALREADY_REQUESTED.
     */
    @Transactional
    public CreateResponse create(Jwt jwt, CreateRequest req) {
        AppUser user = currentUser.require(jwt, Role.RESIDENT);
        if (user.getDongId() == null) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "먼저 내 동네를 정해 주세요.");
        }
        if (!user.getDongId().equals(req.dongId())) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "내 동네에만 요청할 수 있어요.");
        }
        if (em.find(Category.class, req.categoryId()) == null) {
            throw new ApiException(ErrorCode.NOT_FOUND, "없는 업종이에요.");
        }
        if (requestRepository.existsByUserIdAndDongIdAndCategoryId(user.getId(), req.dongId(), req.categoryId())) {
            throw new ApiException(ErrorCode.ALREADY_REQUESTED);
        }

        String text = req.text() == null || req.text().isBlank() ? null : req.text().trim();
        ShopRequest saved;
        try {
            // 동시에 두 번 들어오면 DB 고유 제약에 걸려요 → 같은 409로 바꿔요
            saved = requestRepository.saveAndFlush(new ShopRequest(user, req.dongId(), req.categoryId(), text,
                    distinct(req.conditions()), distinct(req.timeSlots()), req.frequency()));
        } catch (DataIntegrityViolationException e) {
            throw new ApiException(ErrorCode.ALREADY_REQUESTED);
        }

        long count = requestRepository.countByDongIdAndCategoryId(req.dongId(), req.categoryId());
        boolean reachedGoal = count >= goal;
        long notified = reachedGoal ? nearbyOwnerCount(req.dongId(), req.categoryId()) : 0;
        return new CreateResponse(
                new RequestSummary(saved.getId(), saved.getDongId(), saved.getCategoryId(),
                        KstTime.of(saved.getCreatedAt())),
                count, goal, reachedGoal, notified);
    }

    /** GET /me/requests · 최근 요청이 맨 위. 없으면 []. offers 는 그 동·업종 선주문 제안(취소된 것 빼고). */
    @Transactional(readOnly = true)
    public List<MyRequest> myRequests(Jwt jwt) {
        AppUser user = currentUser.get(jwt);
        return requestRepository.findByUserIdOrderByCreatedAtDescIdDesc(user.getId()).stream()
                .map(r -> new MyRequest(
                        r.getId(),
                        dongView(r.getDongId()),
                        categoryView(r.getCategoryId()),
                        requestRepository.countByDongIdAndCategoryId(r.getDongId(), r.getCategoryId()),
                        r.getTimeSlots(),
                        r.getFrequency(),
                        KstTime.of(r.getCreatedAt()),
                        offerService.offersFor(r.getDongId(), r.getCategoryId(), user.getId())))
                .toList();
    }

    /** DELETE /requests/{id} · 내 요청만(아니면 403), 없으면 404. */
    @Transactional
    public void delete(Jwt jwt, Long requestId) {
        AppUser user = currentUser.get(jwt);
        ShopRequest request = requestRepository.findById(requestId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
        if (!request.getUser().getId().equals(user.getId())) {
            throw new ApiException(ErrorCode.FORBIDDEN);
        }
        requestRepository.delete(request);
    }

    /** 동 중심에서 3km 안에 있는 같은 업종 사장님 가게 수. MVP에서는 실제 알림 대신 화면 4에 보여요. */
    private long nearbyOwnerCount(Long dongId, Long categoryId) {
        Dong dong = em.find(Dong.class, dongId);
        if (dong == null) {
            return 0;
        }
        return ownerShopRepository.findByCategoryId(categoryId).stream()
                .filter(s -> distanceKm(dong.getCenterLat(), dong.getCenterLon(), s.getLat(), s.getLon())
                        <= NOTIFY_RADIUS_KM)
                .map(OwnerShop::getId)
                .count();
    }

    /** 두 좌표 사이 직선거리(km, 하버사인 공식). */
    static double distanceKm(double lat1, double lon1, double lat2, double lon2) {
        double r = 6371.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 2 * r * Math.asin(Math.sqrt(a));
    }

    private IdName dongView(Long dongId) {
        Dong d = em.find(Dong.class, dongId);
        return d == null ? new IdName(dongId, null) : new IdName(d.getId(), d.getName());
    }

    private IdName categoryView(Long categoryId) {
        Category c = em.find(Category.class, categoryId);
        return c == null ? new IdName(categoryId, null) : new IdName(c.getId(), c.getName());
    }

    private static <E> List<E> distinct(List<E> values) {
        return values == null ? List.of() : values.stream().distinct().toList();
    }
}
