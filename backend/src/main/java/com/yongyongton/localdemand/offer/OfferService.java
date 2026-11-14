package com.yongyongton.localdemand.offer;

import com.yongyongton.localdemand.auth.AppUser;
import com.yongyongton.localdemand.auth.CurrentUser;
import com.yongyongton.localdemand.auth.OwnerShop;
import com.yongyongton.localdemand.auth.OwnerShopRepository;
import com.yongyongton.localdemand.auth.Role;
import com.yongyongton.localdemand.common.KstTime;
import com.yongyongton.localdemand.common.error.ApiException;
import com.yongyongton.localdemand.common.error.ErrorCode;
import com.yongyongton.localdemand.dong.Dong;
import com.yongyongton.localdemand.offer.OfferDtos.CreateOfferRequest;
import com.yongyongton.localdemand.offer.OfferDtos.CreateOfferResponse;
import com.yongyongton.localdemand.offer.OfferDtos.IdName;
import com.yongyongton.localdemand.offer.OfferDtos.OfferSummary;
import com.yongyongton.localdemand.offer.OfferDtos.OrderRequest;
import com.yongyongton.localdemand.offer.OfferDtos.OrderResponse;
import com.yongyongton.localdemand.offer.OfferDtos.OrderView;
import com.yongyongton.localdemand.offer.OfferDtos.OwnerOfferItem;
import com.yongyongton.localdemand.offer.OfferDtos.ResidentOfferView;
import com.yongyongton.localdemand.request.ShopRequestRepository;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 선주문 수령 (명세 5-4, 5-5, 결정 4).
 * 상태 규칙: 마감 전에는 OPEN. 마감이 지나면 주문 수(수량 합계)가 minOrders 이상이면 CONFIRMED, 아니면 CLOSED.
 * 이 판정은 조회할 때 계산하고(명세 허용), 바꾸는 API에서는 계산한 상태를 저장한 뒤 규칙을 확인해요.
 */
@Service
public class OfferService {

    private final CurrentUser currentUser;
    private final OfferRepository offerRepository;
    private final PreOrderRepository preOrderRepository;
    private final OwnerShopRepository ownerShopRepository;
    private final ShopRequestRepository requestRepository;
    private final EntityManager em;

    public OfferService(CurrentUser currentUser, OfferRepository offerRepository,
                        PreOrderRepository preOrderRepository, OwnerShopRepository ownerShopRepository,
                        ShopRequestRepository requestRepository, EntityManager em) {
        this.currentUser = currentUser;
        this.offerRepository = offerRepository;
        this.preOrderRepository = preOrderRepository;
        this.ownerShopRepository = ownerShopRepository;
        this.requestRepository = requestRepository;
        this.em = em;
    }

    // ───────────── 사장님 ─────────────

    /**
     * POST /offers · 사장님만(403), 가게 등록 먼저(400), 내 가게 업종으로만(400), 없는 동 404,
     * 지금 < 마감 < 수령(400). 만들면 그 동·업종을 요청한 주민의 화면 3에 보여요.
     */
    @Transactional
    public CreateOfferResponse create(Jwt jwt, CreateOfferRequest req) {
        AppUser owner = currentUser.require(jwt, Role.OWNER);
        OwnerShop shop = ownerShopRepository.findByUserId(owner.getId())
                .orElseThrow(() -> new ApiException(ErrorCode.VALIDATION_ERROR, "먼저 가게를 등록해 주세요."));
        if (!shop.getCategoryId().equals(req.categoryId())) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "내 가게 업종으로만 제안할 수 있어요.");
        }
        if (em.find(Dong.class, req.dongId()) == null) {
            throw new ApiException(ErrorCode.NOT_FOUND, "없는 동네예요.");
        }
        LocalDateTime deadline = toKst(req.deadlineAt());
        LocalDateTime pickup = toKst(req.pickupAt());
        if (!now().isBefore(deadline) || !deadline.isBefore(pickup)) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "지금 < 주문 마감 < 수령 시각 순서여야 해요.");
        }
        String description = req.description() == null || req.description().isBlank() ? null : req.description().trim();
        Offer offer = offerRepository.save(new Offer(owner, req.dongId(), req.categoryId(), req.title().trim(),
                description, req.price(), pickup, req.pickupPlace().trim(), req.minOrders(), deadline));
        long notified = requestRepository.countByDongIdAndCategoryId(req.dongId(), req.categoryId());
        return new CreateOfferResponse(new OfferSummary(offer.getId(), offer.getStatus(), 0, offer.getMinOrders()),
                notified);
    }

    /** GET /owner/offers · 내 제안, 최근 것이 위. */
    @Transactional(readOnly = true)
    public List<OwnerOfferItem> ownerOffers(Jwt jwt) {
        AppUser owner = currentUser.require(jwt, Role.OWNER);
        return offerRepository.findByOwnerIdOrderByCreatedAtDescIdDesc(owner.getId()).stream()
                .map(o -> ownerItem(o, orderCount(o)))
                .toList();
    }

    /**
     * POST /offers/{id}/cancel · 내 제안만(403). OPEN·CONFIRMED일 때만, 아니면 409 OFFER_CLOSED.
     * 제안을 취소하면 그 제안의 선주문(RESERVED)도 모두 취소(CANCELLED)해요. 응답의 orderCount 는 0이 돼요.
     */
    @Transactional
    public OwnerOfferItem cancel(Jwt jwt, Long offerId) {
        Offer offer = myOffer(jwt, offerId);
        refreshStatus(offer);
        offer.cancel();
        preOrderRepository.findByOfferIdAndStatus(offer.getId(), OrderStatus.RESERVED)
                .forEach(PreOrder::cancel);
        return ownerItem(offer, orderCount(offer));
    }

    /**
     * POST /offers/{id}/done · 수령 끝. 최소 주문 수를 채운 제안(CONFIRMED)만, 아니면 409 OFFER_CLOSED.
     * 선주문들은 PICKED_UP 으로 바꿔요. 이 주문 수가 창업자 리포트의 "실제로 팔린 수요" 근거가 돼요.
     */
    @Transactional
    public OwnerOfferItem done(Jwt jwt, Long offerId) {
        Offer offer = myOffer(jwt, offerId);
        long count = refreshStatus(offer);
        if (offer.getStatus() != OfferStatus.CONFIRMED) {
            throw new ApiException(ErrorCode.OFFER_CLOSED, "최소 주문 수를 채운 제안만 수령 끝으로 바꿀 수 있어요.");
        }
        offer.markDone();
        preOrderRepository.findByOfferIdAndStatus(offer.getId(), OrderStatus.RESERVED)
                .forEach(PreOrder::markPickedUp);
        return ownerItem(offer, count);
    }

    // ───────────── 주민 ─────────────

    /**
     * POST /offers/{id}/orders · 주민만(403), 내 동네 제안만(400), 주문 받는 중(OPEN)일 때만(409 OFFER_CLOSED).
     * 이미 주문했으면 409 ALREADY_ORDERED. 취소했던 주문이면 다시 주문으로 되살려요.
     */
    @Transactional
    public OrderResponse order(Jwt jwt, Long offerId, OrderRequest req) {
        AppUser user = currentUser.require(jwt, Role.RESIDENT);
        Offer offer = offerRepository.findById(offerId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
        if (user.getDongId() == null || !user.getDongId().equals(offer.getDongId())) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "내 동네 제안에만 주문할 수 있어요.");
        }
        refreshStatus(offer);
        if (offer.getStatus() != OfferStatus.OPEN) {
            throw new ApiException(ErrorCode.OFFER_CLOSED);
        }
        int quantity = req == null || req.quantity() == null ? 1 : req.quantity();

        PreOrder order = preOrderRepository.findByOfferIdAndUserId(offer.getId(), user.getId()).orElse(null);
        if (order != null) {
            if (order.getStatus() != OrderStatus.CANCELLED) {
                throw new ApiException(ErrorCode.ALREADY_ORDERED);
            }
            order.reserveAgain(quantity);
        } else {
            try {
                order = preOrderRepository.saveAndFlush(new PreOrder(offer, user, quantity));
            } catch (DataIntegrityViolationException e) {
                throw new ApiException(ErrorCode.ALREADY_ORDERED);
            }
        }
        return new OrderResponse(orderView(order), orderCount(offer), offer.getMinOrders());
    }

    /** DELETE /orders/{id} · 내 주문만(403), 없으면 404. 마감 전(OPEN)에만, 아니면 409 OFFER_CLOSED. 이미 취소했으면 그냥 204. */
    @Transactional
    public void cancelOrder(Jwt jwt, Long orderId) {
        AppUser user = currentUser.get(jwt);
        PreOrder order = preOrderRepository.findById(orderId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
        if (!order.getUser().getId().equals(user.getId())) {
            throw new ApiException(ErrorCode.FORBIDDEN);
        }
        if (order.getStatus() == OrderStatus.CANCELLED) {
            return;
        }
        Offer offer = order.getOffer();
        refreshStatus(offer);
        if (order.getStatus() != OrderStatus.RESERVED || offer.getStatus() != OfferStatus.OPEN) {
            throw new ApiException(ErrorCode.OFFER_CLOSED, "주문 마감 전에만 취소할 수 있어요.");
        }
        order.cancel();
    }

    /** GET /me/requests 의 offers 칸. 그 동·업종 제안 중 취소된 것만 빼고, 최근 것이 위. */
    @Transactional(readOnly = true)
    public List<ResidentOfferView> offersFor(Long dongId, Long categoryId, Long userId) {
        return offerRepository.findByDongIdAndCategoryIdAndStatusNotOrderByCreatedAtDescIdDesc(
                        dongId, categoryId, OfferStatus.CANCELLED).stream()
                .map(o -> {
                    long count = orderCount(o);
                    String shopName = ownerShopRepository.findByUserId(o.getOwner().getId())
                            .map(OwnerShop::getShopName).orElse(null);
                    OrderView myOrder = preOrderRepository.findByOfferIdAndUserId(o.getId(), userId)
                            .filter(p -> p.getStatus() != OrderStatus.CANCELLED)
                            .map(OfferService::orderView)
                            .orElse(null);
                    return new ResidentOfferView(o.getId(), shopName, o.getTitle(), o.getPrice(),
                            KstTime.of(o.getPickupAt()), o.getPickupPlace(), o.getMinOrders(), count,
                            KstTime.of(o.getDeadlineAt()), effectiveStatus(o, count), myOrder);
                })
                .toList();
    }

    // ───────────── 공통 ─────────────

    private Offer myOffer(Jwt jwt, Long offerId) {
        AppUser owner = currentUser.require(jwt, Role.OWNER);
        Offer offer = offerRepository.findById(offerId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
        if (!offer.getOwner().getId().equals(owner.getId())) {
            throw new ApiException(ErrorCode.FORBIDDEN);
        }
        return offer;
    }

    /** 계산한 상태를 저장하고 주문 수를 돌려줘요. */
    private long refreshStatus(Offer offer) {
        long count = orderCount(offer);
        OfferStatus effective = effectiveStatus(offer, count);
        if (effective != offer.getStatus()) {
            offer.changeStatus(effective);
        }
        return count;
    }

    static OfferStatus effectiveStatus(Offer offer, long orderCount) {
        if (offer.getStatus() == OfferStatus.OPEN && now().isAfter(offer.getDeadlineAt())) {
            return orderCount >= offer.getMinOrders() ? OfferStatus.CONFIRMED : OfferStatus.CLOSED;
        }
        return offer.getStatus();
    }

    private long orderCount(Offer offer) {
        Long sum = preOrderRepository.sumQuantity(offer.getId(), OrderStatus.CANCELLED);
        return sum == null ? 0 : sum;
    }

    private OwnerOfferItem ownerItem(Offer o, long count) {
        Dong d = em.find(Dong.class, o.getDongId());
        IdName dong = d == null ? new IdName(o.getDongId(), null) : new IdName(d.getId(), d.getName());
        return new OwnerOfferItem(o.getId(), dong, o.getTitle(), o.getDescription(), o.getPrice(),
                KstTime.of(o.getPickupAt()), o.getPickupPlace(), count, o.getMinOrders(),
                effectiveStatus(o, count), KstTime.of(o.getDeadlineAt()));
    }

    private static OrderView orderView(PreOrder p) {
        return new OrderView(p.getId(), p.getOffer().getId(), p.getQuantity(), p.getStatus());
    }

    private static LocalDateTime now() {
        return LocalDateTime.now(KstTime.ZONE);
    }

    private static LocalDateTime toKst(OffsetDateTime time) {
        return time.atZoneSameInstant(KstTime.ZONE).toLocalDateTime();
    }
}
