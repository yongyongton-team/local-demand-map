package com.yongyongton.localdemand.auth;

import com.yongyongton.localdemand.auth.MeDtos.CategoryView;
import com.yongyongton.localdemand.auth.MeDtos.DongView;
import com.yongyongton.localdemand.auth.MeDtos.MeResponse;
import com.yongyongton.localdemand.auth.MeDtos.ShopDongView;
import com.yongyongton.localdemand.auth.MeDtos.ShopView;
import com.yongyongton.localdemand.auth.MeDtos.UpdateMeRequest;
import com.yongyongton.localdemand.common.error.ApiException;
import com.yongyongton.localdemand.common.error.ErrorCode;
import com.yongyongton.localdemand.dong.Category;
import com.yongyongton.localdemand.dong.Dong;
import jakarta.persistence.EntityManager;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 내 정보 (명세 5-1). dong·category 는 A 담당 엔티티라 리포지토리를 따로 만들지 않고 EntityManager로 id 조회만 해요.
 */
@Service
public class MeService {

    private final CurrentUser currentUser;
    private final OwnerShopRepository ownerShopRepository;
    private final EntityManager em;

    public MeService(CurrentUser currentUser, OwnerShopRepository ownerShopRepository, EntityManager em) {
        this.currentUser = currentUser;
        this.ownerShopRepository = ownerShopRepository;
        this.em = em;
    }

    @Transactional(readOnly = true)
    public MeResponse get(Jwt jwt) {
        return toResponse(currentUser.get(jwt));
    }

    /** PUT /me · 역할·동네 정하기(역할 바꾸기 포함). 없는 동이면 404. */
    @Transactional
    public MeResponse update(Jwt jwt, UpdateMeRequest request) {
        AppUser user = currentUser.get(jwt);
        Long dongId = request.dongId() != null ? request.dongId() : user.getDongId();
        if (request.dongId() != null && em.find(Dong.class, request.dongId()) == null) {
            throw new ApiException(ErrorCode.NOT_FOUND, "없는 동네예요.");
        }
        user.changeProfile(request.role(), dongId);
        return toResponse(user);
    }

    private MeResponse toResponse(AppUser user) {
        DongView dong = null;
        if (user.getDongId() != null) {
            Dong d = em.find(Dong.class, user.getDongId());
            if (d != null) {
                dong = new DongView(d.getId(), d.getName(), d.getGu());
            }
        }
        ShopView shop = ownerShopRepository.findByUserId(user.getId()).map(this::toShopView).orElse(null);
        return new MeResponse(user.getId(), user.getNickname(), user.getRole(), dong, shop);
    }

    private ShopView toShopView(OwnerShop s) {
        Category c = em.find(Category.class, s.getCategoryId());
        CategoryView category = c == null ? null : new CategoryView(c.getId(), c.getName());
        ShopDongView dong = null;
        if (s.getDongId() != null) {
            Dong d = em.find(Dong.class, s.getDongId());
            if (d != null) {
                dong = new ShopDongView(d.getId(), d.getName());
            }
        }
        return new ShopView(s.getShopName(), category, dong, s.getLat(), s.getLon());
    }
}
