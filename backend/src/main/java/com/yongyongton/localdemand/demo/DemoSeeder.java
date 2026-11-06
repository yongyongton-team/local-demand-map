package com.yongyongton.localdemand.demo;

import com.yongyongton.localdemand.auth.AppUser;
import com.yongyongton.localdemand.auth.AppUserRepository;
import com.yongyongton.localdemand.auth.AuthService;
import com.yongyongton.localdemand.auth.DongLocator;
import com.yongyongton.localdemand.auth.OwnerShop;
import com.yongyongton.localdemand.auth.OwnerShopRepository;
import com.yongyongton.localdemand.auth.Role;
import com.yongyongton.localdemand.dong.Category;
import com.yongyongton.localdemand.dong.Dong;
import com.yongyongton.localdemand.request.Frequency;
import com.yongyongton.localdemand.request.RequestCondition;
import com.yongyongton.localdemand.request.ShopRequest;
import com.yongyongton.localdemand.request.ShopRequestRepository;
import com.yongyongton.localdemand.request.TimeSlot;
import jakarta.persistence.EntityManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 시연용 예시 데이터 (명세 8번 결정 5, 5-1 "시연 계정은 시드로 미리 만들어 둬요").
 * DEMO_SEED_ENABLED=true 일 때만 서버가 켜질 때 한 번 넣어요. 발표 때 "예시 데이터"라고 밝혀요.
 * <ul>
 *   <li>시연 주민: 구성동, 반찬가게만 미리 요청(과일은 발표 때 직접 눌러 29 → 30)</li>
 *   <li>시연 사장님: 싱싱과일(과일·채소), 신갈동. 구성동까지 약 1.9km</li>
 *   <li>시연 창업자</li>
 *   <li>예시 주민 요청: 구성동 과일·채소 29, 구성동 반찬 11(+시연 주민 1 = 12), 보정동 과일·채소 14, 마북동 과일·채소 9</li>
 * </ul>
 * 이미 넣었으면(예시주민1 이 있으면) 다시 넣지 않아요. 동·업종 데이터(V3·V4)가 없으면 넣지 않고 로그만 남겨요.
 */
@Component
@ConditionalOnProperty(name = "app.demo-seed.enabled", havingValue = "true")
public class DemoSeeder implements ApplicationRunner {

    static final String FAKE_PREFIX = "예시주민";

    static final String GUSEONG = "4146356000";   // 구성동
    static final String SINGAL = "4146351000";    // 신갈동
    static final String BOJEONG = "4146359000";   // 보정동
    static final String MABUK = "4146357000";     // 마북동

    /** 신갈동 경계 안. 구성동 중심 약 1.9km, 마북동 약 1.7km, 보정동 약 4.0km. */
    static final double SHOP_LAT = 37.2849;
    static final double SHOP_LON = 127.1142;

    private static final Logger log = LoggerFactory.getLogger(DemoSeeder.class);

    private final AppUserRepository userRepository;
    private final OwnerShopRepository ownerShopRepository;
    private final ShopRequestRepository requestRepository;
    private final DongLocator dongLocator;
    private final EntityManager em;

    public DemoSeeder(AppUserRepository userRepository, OwnerShopRepository ownerShopRepository,
                      ShopRequestRepository requestRepository, DongLocator dongLocator, EntityManager em) {
        this.userRepository = userRepository;
        this.ownerShopRepository = ownerShopRepository;
        this.requestRepository = requestRepository;
        this.dongLocator = dongLocator;
        this.em = em;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (userRepository.existsByNickname(FAKE_PREFIX + 1)) {
            log.info("시연 시드: 이미 들어 있어서 건너뛰어요.");
            return;
        }
        Optional<Dong> guseong = dong(GUSEONG);
        Optional<Dong> singal = dong(SINGAL);
        Optional<Dong> bojeong = dong(BOJEONG);
        Optional<Dong> mabuk = dong(MABUK);
        Optional<Category> fruit = category("FRUIT_VEG");
        Optional<Category> sideDish = category("SIDE_DISH");
        if (guseong.isEmpty() || singal.isEmpty() || bojeong.isEmpty() || mabuk.isEmpty()
                || fruit.isEmpty() || sideDish.isEmpty()) {
            log.warn("시연 시드: 동·업종 데이터가 없어서 넣지 않았어요(V3·V4 마이그레이션 확인).");
            return;
        }
        Long fruitId = fruit.get().getId();
        Long sideDishId = sideDish.get().getId();

        // 예시 주민 요청. 구성동 주민 29명은 모두 과일, 그중 11명은 반찬도 요청
        List<AppUser> guseongUsers = fakeResidents(1, 29, guseong.get().getId());
        for (int i = 0; i < guseongUsers.size(); i++) {
            request(guseongUsers.get(i), guseong.get().getId(), fruitId, i);
            if (i < 11) {
                request(guseongUsers.get(i), guseong.get().getId(), sideDishId, i + 3);
            }
        }
        List<AppUser> bojeongUsers = fakeResidents(30, 14, bojeong.get().getId());
        for (int i = 0; i < bojeongUsers.size(); i++) {
            request(bojeongUsers.get(i), bojeong.get().getId(), fruitId, i + 5);
        }
        List<AppUser> mabukUsers = fakeResidents(44, 9, mabuk.get().getId());
        for (int i = 0; i < mabukUsers.size(); i++) {
            request(mabukUsers.get(i), mabuk.get().getId(), fruitId, i + 7);
        }

        // 시연 계정 (POST /auth/demo 가 찾는 이름과 같아요)
        AppUser resident = demoUser(Role.RESIDENT);
        resident.changeProfile(Role.RESIDENT, guseong.get().getId());
        if (!requestRepository.existsByUserIdAndDongIdAndCategoryId(resident.getId(), guseong.get().getId(), sideDishId)) {
            requestRepository.save(new ShopRequest(resident, guseong.get().getId(), sideDishId,
                    "퇴근하고 반찬 살 데가 없어요", List.of(RequestCondition.LATE_OPEN),
                    List.of(TimeSlot.WEEKDAY_EVENING), Frequency.WEEKLY_PLUS));
        }

        AppUser owner = demoUser(Role.OWNER);
        Long shopDongId = dongLocator.findDongId(SHOP_LAT, SHOP_LON).orElse(singal.get().getId());
        OwnerShop shop = ownerShopRepository.findByUserId(owner.getId()).orElse(null);
        if (shop == null) {
            ownerShopRepository.save(new OwnerShop(owner, "싱싱과일", fruitId, SHOP_LAT, SHOP_LON, shopDongId));
        } else {
            shop.update("싱싱과일", fruitId, SHOP_LAT, SHOP_LON, shopDongId);
        }

        demoUser(Role.FOUNDER);

        log.info("시연 시드: 예시 주민 {}명, 요청 {}건과 시연 계정 3개를 넣었어요.",
                guseongUsers.size() + bojeongUsers.size() + mabukUsers.size(), 29 + 11 + 14 + 9 + 1);
    }

    /** 예시주민{start}부터 count명. 주민·동네까지 정해 둬요. */
    private List<AppUser> fakeResidents(int start, int count, Long dongId) {
        List<AppUser> users = new ArrayList<>();
        for (int k = start; k < start + count; k++) {
            AppUser user = new AppUser(FAKE_PREFIX + k);
            user.changeProfile(Role.RESIDENT, dongId);
            users.add(userRepository.save(user));
        }
        return users;
    }

    /**
     * 조건·시간대·빈도를 i로 고르게 나눠요. 화면 5 예시처럼 늦게까지 영업이 가장 많고, 평일 저녁이 60% 정도가 돼요.
     */
    private void request(AppUser user, Long dongId, Long categoryId, int i) {
        List<RequestCondition> conditions = new ArrayList<>();
        if (i % 5 < 3) {
            conditions.add(RequestCondition.LATE_OPEN);
        }
        if (i % 8 < 3) {
            conditions.add(RequestCondition.SMALL_QTY);
        }
        if (i % 4 == 3) {
            conditions.add(RequestCondition.DELIVERY);
        }
        List<TimeSlot> slots = new ArrayList<>();
        int slot = i % 10;
        slots.add(slot < 6 ? TimeSlot.WEEKDAY_EVENING : slot < 9 ? TimeSlot.WEEKEND : TimeSlot.WEEKDAY_DAY);
        Frequency frequency = switch (i % 3) {
            case 0 -> Frequency.WEEKLY_PLUS;
            case 1 -> Frequency.MONTHLY_2_3;
            default -> Frequency.SOMETIMES;
        };
        requestRepository.save(new ShopRequest(user, dongId, categoryId, null, conditions, slots, frequency));
    }

    private AppUser demoUser(Role role) {
        String nickname = AuthService.demoNickname(role);
        return userRepository.findFirstByNicknameAndRoleOrderByIdAsc(nickname, role)
                .orElseGet(() -> {
                    AppUser user = new AppUser(nickname);
                    user.changeProfile(role, null);
                    return userRepository.save(user);
                });
    }

    private Optional<Dong> dong(String admCode) {
        return em.createQuery("SELECT d FROM Dong d WHERE d.admCode = :code", Dong.class)
                .setParameter("code", admCode).getResultStream().findFirst();
    }

    private Optional<Category> category(String code) {
        return em.createQuery("SELECT c FROM Category c WHERE c.code = :code", Category.class)
                .setParameter("code", code).getResultStream().findFirst();
    }
}
