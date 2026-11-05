package com.yongyongton.localdemand.offer;

import com.yongyongton.localdemand.offer.OfferDtos.CreateOfferRequest;
import com.yongyongton.localdemand.offer.OfferDtos.CreateOfferResponse;
import com.yongyongton.localdemand.offer.OfferDtos.OrderRequest;
import com.yongyongton.localdemand.offer.OfferDtos.OrderResponse;
import com.yongyongton.localdemand.offer.OfferDtos.OwnerOfferItem;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 선주문 수령 API 6개 (명세 5-4, 5-5). 로그인 필요. */
@RestController
public class OfferController {

    private final OfferService offerService;

    public OfferController(OfferService offerService) {
        this.offerService = offerService;
    }

    /** 사장님 · 선주문 수령 제안 만들기 (화면 5). 201. */
    @PostMapping("/offers")
    @ResponseStatus(HttpStatus.CREATED)
    public CreateOfferResponse create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreateOfferRequest request) {
        return offerService.create(jwt, request);
    }

    /** 사장님 · 내 제안 목록. */
    @GetMapping("/owner/offers")
    public List<OwnerOfferItem> ownerOffers(@AuthenticationPrincipal Jwt jwt) {
        return offerService.ownerOffers(jwt);
    }

    /** 사장님 · 제안 취소. 응답은 내 제안 목록 한 줄. */
    @PostMapping("/offers/{id}/cancel")
    public OwnerOfferItem cancel(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        return offerService.cancel(jwt, id);
    }

    /** 사장님 · 수령 끝. 응답은 내 제안 목록 한 줄. */
    @PostMapping("/offers/{id}/done")
    public OwnerOfferItem done(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        return offerService.done(jwt, id);
    }

    /** 주민 · 선주문. 본문을 빼면 수량 1. 201. */
    @PostMapping("/offers/{offerId}/orders")
    @ResponseStatus(HttpStatus.CREATED)
    public OrderResponse order(@AuthenticationPrincipal Jwt jwt, @PathVariable Long offerId,
                               @Valid @RequestBody(required = false) OrderRequest request) {
        return offerService.order(jwt, offerId, request);
    }

    /** 주민 · 선주문 취소. 204. */
    @DeleteMapping("/orders/{orderId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancelOrder(@AuthenticationPrincipal Jwt jwt, @PathVariable Long orderId) {
        offerService.cancelOrder(jwt, orderId);
    }
}
