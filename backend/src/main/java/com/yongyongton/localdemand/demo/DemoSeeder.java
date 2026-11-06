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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 시연용 예시 데이터 (명세 8번 결정 5, 5-1 "시연 계정은 시드로 미리 만들어 둬요").
 * DEMO_SEED_ENABLED=true 일 때만 서버가 켜질 때 돌아요. 발표 때 "예시 데이터"라고 밝혀요.
 *
 * <p><b>1. 예시 주민 요청</b>(처음 한 번만. 예시주민1 이 있으면 건너뛰어요). 숫자는 명세·와이어프레임 예시에 맞췄어요.
 * <ul>
 *   <li>구성동 과일·채소 29(시연 주민이 누르면 30) — 화면 5: 늦게까지 18·낱개 11·배달 7, 평일 저녁 60%·주말 30%·평일 낮 10%</li>
 *   <li>구성동 반찬 11(+시연 주민 = 12), 구성동 빵집 6 — 화면 1, 명세 gaps 예시</li>
 *   <li>보정동 과일·채소 14 — 명세 5-5 예시: 늦게까지 8·낱개 5·배달 3, 주말 9·평일 저녁 4·평일 낮 1</li>
 *   <li>마북동 과일·채소 9 — 화면 4 "평일 낮이 많아요"</li>
 * </ul>
 *
 * <p><b>2. 시연 계정 되돌리기</b>(켤 때마다). 그래서 서버를 다시 켜면 29 → 30 시연을 몇 번이든 반복할 수 있어요.
 * <ul>
 *   <li>시연 주민: 구성동 주민, 요청은 반찬 하나만 남기고 지워요. 선주문도 지워요</li>
 *   <li>시연 사장님: 싱싱과일(과일·채소) 가게를 마북동 지점으로 되돌리고, 만든 제안·그 선주문을 지워요</li>
 *   <li>시연 창업자: 역할만 되돌려요</li>
 * </ul>
 * 예시 주민·다른 손님 계정의 데이터는 건드리지 않아요.
 *
 * <p>동·업종 데이터(V3·V4)가 없으면 서버가 켜지지 않게 멈춰요. 시연 서버에서 빈 화면으로 발표하는 일을 막으려고요.
 */
@Component
@ConditionalOnProperty(name = "app.demo-seed.enabled", havingValue = "true")
public class DemoSeeder implements ApplicationRunner {

    static final String FAKE_PREFIX = "예시주민";

    static final String GUSEONG = "4146356000";   // 구성동
    static final String BOJEONG = "4146359000";   // 보정동
    static final String MABUK = "4146357000";     // 마북동

    /** 마북동 경계 안. 동 중심까지 구성동 약 2.0km, 보정동 약 2.8km, 마북동 약 2.6km → 3km 칩에 세 동이 다 보여요. */
    static final double SHOP_LAT = 37.3108;
    static final double SHOP_LON = 127.1398;

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

    /** 시드가 쓰는 동·업종 id. */
    record Refs(Long guseong, Long bojeong, Long mabuk, Long fruit, Long sideDish, Long bakery) {
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        Refs refs = loadRefs();
        if (userRepository.existsByNickname(FAKE_PREFIX + 1)) {
            log.info("시연 시드: 예시 주민 요청은 이미 있어서 건너뛰어요.");
        } else {
            seedFakeRequests(refs);
        }
        resetDemoAccounts(refs);
    }

    /** 동·업종이 없으면 멈춰요(V3·V4 마이그레이션이 안 돌았거나 코드가 바뀐 경우). */
    Refs loadRefs() {
        return new Refs(dong(GUSEONG), dong(BOJEONG), dong(MABUK),
                category("FRUIT_VEG"), category("SIDE_DISH"), category("BAKERY"));
    }

    private void seedFakeRequests(Refs r) {
        // 구성동 주민 29명: 모두 과일, 1~11번은 반찬, 12~17번은 빵집도 요청
        List<AppUser> guseong = fakeResidents(1, 29, r.guseong());
        save(guseong, r.guseong(), r.fruit(), spread(29, 17, 10, 7, 17, 9, 3));
        save(guseong.subList(0, 11), r.guseong(), r.sideDish(), spread(11, 5, 4, 2, 7, 3, 1));
        save(guseong.subList(11, 17), r.guseong(), r.bakery(), spread(6, 2, 2, 0, 3, 2, 1));
        // 보정동 14명: 명세 5-5 예시 그대로(주말이 가장 많아요)
        save(fakeResidents(30, 14, r.bojeong()), r.bojeong(), r.fruit(), spread(14, 8, 5, 3, 4, 9, 1));
        // 마북동 9명: 평일 낮이 가장 많아요
        save(fakeResidents(44, 9, r.mabuk()), r.mabuk(), r.fruit(), spread(9, 3, 2, 1, 2, 2, 5));
        log.info("시연 시드: 예시 주민 52명, 요청 69건을 넣었어요.");
    }

    /** 시연 계정을 발표 시작 상태로 되돌려요. */
    private void resetDemoAccounts(Refs r) {
        AppUser resident = demoUser(Role.RESIDENT);
        AppUser owner = demoUser(Role.OWNER);
        AppUser founder = demoUser(Role.FOUNDER);

        em.createQuery("DELETE FROM PreOrder p WHERE p.user.id = :resident"
                        + " OR p.offer.id IN (SELECT o.id FROM Offer o WHERE o.owner.id = :owner)")
                .setParameter("resident", resident.getId())
                .setParameter("owner", owner.getId())
                .executeUpdate();
        em.createQuery("DELETE FROM Offer o WHERE o.owner.id = :owner")
                .setParameter("owner", owner.getId())
                .executeUpdate();
        em.createQuery("DELETE FROM ShopRequest r WHERE r.user.id = :resident")
                .setParameter("resident", resident.getId())
                .executeUpdate();

        resident.changeProfile(Role.RESIDENT, r.guseong());
        requestRepository.save(new ShopRequest(resident, r.guseong(), r.sideDish(),
                "퇴근하고 반찬 살 데가 없어요", List.of(RequestCondition.LATE_OPEN),
                List.of(TimeSlot.WEEKDAY_EVENING), Frequency.WEEKLY_PLUS));

        owner.changeProfile(Role.OWNER, null);
        Long shopDongId = dongLocator.findDongId(SHOP_LAT, SHOP_LON).orElse(r.mabuk());
        OwnerShop shop = ownerShopRepository.findByUserId(owner.getId()).orElse(null);
        if (shop == null) {
            ownerShopRepository.save(new OwnerShop(owner, "싱싱과일", r.fruit(), SHOP_LAT, SHOP_LON, shopDongId));
        } else {
            shop.update("싱싱과일", r.fruit(), SHOP_LAT, SHOP_LON, shopDongId);
        }

        founder.changeProfile(Role.FOUNDER, null);
        log.info("시연 시드: 시연 계정 3개를 발표 시작 상태로 되돌렸어요.");
    }

    /** 요청 한 건의 조건·시간대·빈도. */
    record Spec(List<RequestCondition> conditions, TimeSlot slot, Frequency frequency) {
    }

    /**
     * n건에 조건·시간대를 정확히 나눠요.
     * 늦게까지 영업 late건, 낱개 구매 small건, 배달 delivery건 / 평일 저녁 evening건, 주말 weekend건, 평일 낮 day건.
     * 시간대는 한 건에 하나씩이라 evening + weekend + day = n 이어야 해요.
     */
    static List<Spec> spread(int n, int late, int small, int delivery, int evening, int weekend, int day) {
        if (evening + weekend + day != n || late > n || small > n || delivery > n) {
            throw new IllegalArgumentException("시드 숫자가 맞지 않아요: n=" + n);
        }
        int deliveryStart = n / 3;
        List<Spec> specs = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            List<RequestCondition> conditions = new ArrayList<>();
            if (i < late) {
                conditions.add(RequestCondition.LATE_OPEN);
            }
            if (i >= n - small) {
                conditions.add(RequestCondition.SMALL_QTY);
            }
            if ((i - deliveryStart + n) % n < delivery) {
                conditions.add(RequestCondition.DELIVERY);
            }
            TimeSlot slot = i < evening ? TimeSlot.WEEKDAY_EVENING
                    : i < evening + weekend ? TimeSlot.WEEKEND : TimeSlot.WEEKDAY_DAY;
            Frequency frequency = switch (i % 3) {
                case 0 -> Frequency.WEEKLY_PLUS;
                case 1 -> Frequency.MONTHLY_2_3;
                default -> Frequency.SOMETIMES;
            };
            specs.add(new Spec(conditions, slot, frequency));
        }
        return specs;
    }

    private void save(List<AppUser> users, Long dongId, Long categoryId, List<Spec> specs) {
        for (int i = 0; i < users.size(); i++) {
            Spec s = specs.get(i);
            requestRepository.save(new ShopRequest(users.get(i), dongId, categoryId, null,
                    s.conditions(), List.of(s.slot()), s.frequency()));
        }
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

    private AppUser demoUser(Role role) {
        String nickname = AuthService.demoNickname(role);
        return userRepository.findFirstByNicknameAndRoleOrderByIdAsc(nickname, role)
                .orElseGet(() -> {
                    AppUser user = new AppUser(nickname);
                    user.changeProfile(role, null);
                    return userRepository.save(user);
                });
    }

    private Long dong(String admCode) {
        return em.createQuery("SELECT d FROM Dong d WHERE d.admCode = :code", Dong.class)
                .setParameter("code", admCode).getResultStream().findFirst()
                .map(Dong::getId)
                .orElseThrow(() -> new IllegalStateException(
                        "시연 시드: 동(adm_code " + admCode + ")이 없어요. V3 동 데이터를 확인하거나 DEMO_SEED_ENABLED=false 로 켜요."));
    }

    private Long category(String code) {
        return em.createQuery("SELECT c FROM Category c WHERE c.code = :code", Category.class)
                .setParameter("code", code).getResultStream().findFirst()
                .map(Category::getId)
                .orElseThrow(() -> new IllegalStateException(
                        "시연 시드: 업종(" + code + ")이 없어요. V4 업종 데이터를 확인하거나 DEMO_SEED_ENABLED=false 로 켜요."));
    }
}
