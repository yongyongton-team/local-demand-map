package com.yongyongton.localdemand.demo;

import com.yongyongton.localdemand.auth.AppUser;
import com.yongyongton.localdemand.auth.AppUserRepository;
import com.yongyongton.localdemand.auth.AuthService;
import com.yongyongton.localdemand.auth.DongLocator;
import com.yongyongton.localdemand.auth.OwnerShop;
import com.yongyongton.localdemand.auth.OwnerShopRepository;
import com.yongyongton.localdemand.auth.Role;
import com.yongyongton.localdemand.common.KstTime;
import com.yongyongton.localdemand.dong.Category;
import com.yongyongton.localdemand.dong.Dong;
import com.yongyongton.localdemand.offer.Offer;
import com.yongyongton.localdemand.offer.PreOrder;
import com.yongyongton.localdemand.request.Frequency;
import com.yongyongton.localdemand.request.RequestCondition;
import com.yongyongton.localdemand.request.ShopRequest;
import com.yongyongton.localdemand.request.ShopRequestRepository;
import com.yongyongton.localdemand.request.TimeSlot;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 시연용 예시 데이터 (명세 8번 결정 5, 5-1 "시연 계정은 시드로 미리 만들어 둬요").
 * DEMO_SEED_ENABLED=true 일 때만 서버가 켜질 때 돌아요. 발표 때 "예시 데이터"라고 밝혀요.
 *
 * <p><b>1. 예시 주민 요청</b>(켤 때마다 예시 주민 요청을 지우고 다시 넣어요 → 예전에 시드를 넣은 DB도 항상 아래 숫자로 맞춰져요).
 * 숫자는 명세·와이어프레임 예시에 맞췄어요.
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
 * 계정은 닉네임으로만 찾아요(시연 중에 역할을 바꿔도 같은 계정을 다시 쓰고 역할을 되돌려요).
 *
 * <p><b>3. 예시 선주문 제안</b>(켤 때마다 지우고 다시 만들어요 → 마감 시각이 늘 서버를 켠 때 기준이에요).
 * <ul>
 *   <li>예시 반찬가게(오늘반찬)의 구성동 반찬 제안 1건(OPEN): 시연 주민의 반찬 요청에 보여요. 시연 주민은 아직 선주문하지 않았어요</li>
 *   <li>시연 사장님 제안 2건: 보정동 과일 OPEN, 마북동 과일 CONFIRMED(마감이 지났고 최소 주문 수를 채움)</li>
 *   <li>구성동 과일은 비워 둬요. 발표 때 시연 사장님이 직접 제안하는 장면이에요</li>
 * </ul>
 *
 * <p><b>4. 손님 데이터 지우기</b>(DEMO_RESET_GUEST_DATA=true 일 때만). 예시 주민·예시 사장님·시연 계정이 아닌 계정이 만든
 * 요청·제안·선주문·가게를 지워요. 계정 자체는 남겨요. 리허설·결선 직전에 켜서 구성동 과일 29를 지켜요.
 * 꺼져 있으면 다른 손님 계정의 데이터는 건드리지 않아요.
 *
 * <p>동·업종 데이터(V3·V4)가 없으면 서버가 켜지지 않게 멈춰요. 시연 서버에서 빈 화면으로 발표하는 일을 막으려고요.
 */
@Component
@ConditionalOnProperty(name = "app.demo-seed.enabled", havingValue = "true")
public class DemoSeeder implements ApplicationRunner {

    static final String FAKE_PREFIX = "예시주민";
    /** 예시 반찬가게 사장님. 시연 주민의 반찬 요청에 제안을 보여 주려고 만들어요. */
    static final String FAKE_OWNER = "예시사장님";
    static final String FAKE_SHOP = "오늘반찬";

    static final String GUSEONG = "4146356000";   // 구성동
    static final String BOJEONG = "4146359000";   // 보정동
    static final String MABUK = "4146357000";     // 마북동

    /** 마북동 경계 안. 동 중심까지 구성동 약 2.0km, 보정동 약 2.8km, 마북동 약 2.6km → 3km 칩에 세 동이 다 보여요. */
    static final double SHOP_LAT = 37.3108;
    static final double SHOP_LON = 127.1398;

    /** 예시 반찬가게 위치. 구성동 중심(37.2935, 127.1325)에서 약 200m. */
    static final double FAKE_SHOP_LAT = 37.2950;
    static final double FAKE_SHOP_LON = 127.1335;

    private static final Logger log = LoggerFactory.getLogger(DemoSeeder.class);

    private final AppUserRepository userRepository;
    private final OwnerShopRepository ownerShopRepository;
    private final ShopRequestRepository requestRepository;
    private final DongLocator dongLocator;
    private final EntityManager em;
    private final boolean resetGuestData;

    public DemoSeeder(AppUserRepository userRepository, OwnerShopRepository ownerShopRepository,
                      ShopRequestRepository requestRepository, DongLocator dongLocator, EntityManager em,
                      @Value("${app.demo-seed.reset-guest-data:false}") boolean resetGuestData) {
        this.userRepository = userRepository;
        this.ownerShopRepository = ownerShopRepository;
        this.requestRepository = requestRepository;
        this.dongLocator = dongLocator;
        this.em = em;
        this.resetGuestData = resetGuestData;
    }

    /** 시드가 쓰는 동·업종 id. */
    record Refs(Long guseong, Long bojeong, Long mabuk, Long fruit, Long sideDish, Long bakery) {
    }

    /** 예시 주민(동별). 선주문 시드에도 써요. */
    record FakeResidents(List<AppUser> guseong, List<AppUser> bojeong, List<AppUser> mabuk) {

        List<Long> ids() {
            List<Long> ids = new ArrayList<>();
            for (List<AppUser> group : List.of(guseong, bojeong, mabuk)) {
                group.forEach(u -> ids.add(u.getId()));
            }
            return ids;
        }
    }

    /** 시연 계정 3개와 예시 반찬가게 사장님. */
    record DemoUsers(AppUser resident, AppUser owner, AppUser founder, AppUser fakeOwner) {
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        seed(resetGuestData);
    }

    /** 서버를 켤 때와 같은 일을 해요. 테스트에서 손님 데이터 지우기를 켜고 끄려고 따로 뒀어요. */
    @Transactional
    public void seed(boolean clearGuestData) {
        Refs refs = loadRefs();
        FakeResidents fakes = seedFakeRequests(refs);
        DemoUsers demo = resetDemoAccounts(refs, fakes);
        if (clearGuestData) {
            clearGuestData(fakes, demo);
        }
        seedOffers(refs, fakes, demo);
    }

    /** 동·업종이 없으면 멈춰요(V3·V4 마이그레이션이 안 돌았거나 코드가 바뀐 경우). */
    Refs loadRefs() {
        return new Refs(dong(GUSEONG), dong(BOJEONG), dong(MABUK),
                category("FRUIT_VEG"), category("SIDE_DISH"), category("BAKERY"));
    }

    private FakeResidents seedFakeRequests(Refs r) {
        List<AppUser> guseong = fakeResidents(1, 29, r.guseong());
        List<AppUser> bojeong = fakeResidents(30, 14, r.bojeong());
        List<AppUser> mabuk = fakeResidents(44, 9, r.mabuk());

        FakeResidents fakes = new FakeResidents(guseong, bojeong, mabuk);

        // 예전 시드 요청은 지우고 지금 숫자로 다시 넣어요(분포를 바꿔도 기존 DB에 반영돼요)
        em.createQuery("DELETE FROM ShopRequest r WHERE r.user.id IN :ids")
                .setParameter("ids", fakes.ids())
                .executeUpdate();

        // 구성동 주민 29명: 모두 과일, 1~11번은 반찬, 12~17번은 빵집도 요청
        save(guseong, r.guseong(), r.fruit(), spread(29, 17, 10, 7, 17, 9, 3));
        save(guseong.subList(0, 11), r.guseong(), r.sideDish(), spread(11, 5, 4, 2, 7, 3, 1));
        save(guseong.subList(11, 17), r.guseong(), r.bakery(), spread(6, 2, 2, 0, 3, 2, 1));
        // 보정동 14명: 명세 5-5 예시 그대로(주말이 가장 많아요)
        save(bojeong, r.bojeong(), r.fruit(), spread(14, 8, 5, 3, 4, 9, 1));
        // 마북동 9명: 평일 낮이 가장 많아요
        save(mabuk, r.mabuk(), r.fruit(), spread(9, 3, 2, 1, 2, 2, 5));
        log.info("시연 시드: 예시 주민 52명의 요청 69건을 지금 숫자로 맞췄어요.");
        return fakes;
    }

    /** 시연 계정을 발표 시작 상태로 되돌려요. 예시 선주문(예시 주민의 선주문, 시연·예시 사장님 제안)도 지워요. */
    private DemoUsers resetDemoAccounts(Refs r, FakeResidents fakes) {
        AppUser resident = demoUser(Role.RESIDENT);
        AppUser owner = demoUser(Role.OWNER);
        AppUser founder = demoUser(Role.FOUNDER);
        AppUser fakeOwner = fakeOwner();

        List<Long> owners = List.of(owner.getId(), fakeOwner.getId());
        List<Long> orderers = new ArrayList<>(fakes.ids());
        orderers.add(resident.getId());
        em.createQuery("DELETE FROM PreOrder p WHERE p.user.id IN :orderers"
                        + " OR p.offer.id IN (SELECT o.id FROM Offer o WHERE o.owner.id IN :owners)")
                .setParameter("orderers", orderers)
                .setParameter("owners", owners)
                .executeUpdate();
        em.createQuery("DELETE FROM Offer o WHERE o.owner.id IN :owners")
                .setParameter("owners", owners)
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

        fakeOwner.changeProfile(Role.OWNER, null);
        Long fakeShopDongId = dongLocator.findDongId(FAKE_SHOP_LAT, FAKE_SHOP_LON).orElse(r.guseong());
        OwnerShop fakeShop = ownerShopRepository.findByUserId(fakeOwner.getId()).orElse(null);
        if (fakeShop == null) {
            ownerShopRepository.save(new OwnerShop(fakeOwner, FAKE_SHOP, r.sideDish(),
                    FAKE_SHOP_LAT, FAKE_SHOP_LON, fakeShopDongId));
        } else {
            fakeShop.update(FAKE_SHOP, r.sideDish(), FAKE_SHOP_LAT, FAKE_SHOP_LON, fakeShopDongId);
        }
        log.info("시연 시드: 시연 계정 3개를 발표 시작 상태로 되돌렸어요.");
        return new DemoUsers(resident, owner, founder, fakeOwner);
    }

    /**
     * 예시 선주문 제안 3건. 마감·수령 시각은 지금(KST) 기준이라 서버를 다시 켜면 다시 미래로 맞춰져요.
     * 상태는 저장하지 않고 읽을 때 계산해요(OPEN 이고 마감이 지났으면 주문 수에 따라 CONFIRMED / CLOSED).
     */
    private void seedOffers(Refs r, FakeResidents fakes, DemoUsers demo) {
        LocalDateTime now = LocalDateTime.now(KstTime.ZONE).truncatedTo(ChronoUnit.HOURS);

        // 시연 주민의 반찬 요청에 보이는 제안. 예시 주민 4명이 선주문했고 시연 주민은 아직이에요(myOrder = null)
        Offer sideDish = persist(new Offer(demo.fakeOwner(), r.guseong(), r.sideDish(), "저녁 반찬 4종 세트",
                "그날 만든 반찬 4종을 한 팩에 담아 드려요", 12000,
                atHour(now.plusDays(3), 19), "구성동 행정복지센터 앞", 10, atHour(now.plusDays(2), 21)));
        order(sideDish, fakes.guseong().subList(0, 4));

        // 시연 사장님: 마감이 지났고 최소 주문 수를 채운 제안(CONFIRMED). 먼저 만들어서 목록에서 아래에 와요
        Offer confirmed = persist(new Offer(demo.owner(), r.mabuk(), r.fruit(), "과일 한 상자",
                "제철 과일을 골라 한 상자로 담아 드려요", 20000,
                atHour(now.plusDays(1), 11), "싱싱과일 마북점", 5, atHour(now.minusDays(1), 21)));
        order(confirmed, fakes.mabuk().subList(0, 6));

        // 시연 사장님: 모집 중인 제안(OPEN)
        Offer open = persist(new Offer(demo.owner(), r.bojeong(), r.fruit(), "제철 과일 꾸러미",
                "낱개로도 담아 드려요", 15000,
                atHour(now.plusDays(3), 18), "보정동 카페거리 입구", 10, atHour(now.plusDays(2), 21)));
        order(open, fakes.bojeong().subList(0, 6));

        log.info("시연 시드: 예시 선주문 제안 3건을 만들었어요.");
    }

    private Offer persist(Offer offer) {
        em.persist(offer);
        return offer;
    }

    private void order(Offer offer, List<AppUser> users) {
        users.forEach(u -> em.persist(new PreOrder(offer, u, 1)));
    }

    private static LocalDateTime atHour(LocalDateTime day, int hour) {
        return day.withHour(hour).withMinute(0).withSecond(0).withNano(0);
    }

    /**
     * 손님 데이터 지우기. 예시 주민·예시 사장님·시연 계정이 아닌 계정의 선주문·제안·요청·가게를 지워요.
     * 손님이 만든 제안에 예시 주민이 선주문한 것은 없지만(시드만 선주문해요), 손님 제안에 걸린 선주문은 누구 것이든 같이 지워요.
     */
    private void clearGuestData(FakeResidents fakes, DemoUsers demo) {
        List<Long> kept = new ArrayList<>(fakes.ids());
        kept.addAll(List.of(demo.resident().getId(), demo.owner().getId(), demo.founder().getId(),
                demo.fakeOwner().getId()));

        int orders = em.createQuery("DELETE FROM PreOrder p WHERE p.user.id NOT IN :kept"
                        + " OR p.offer.id IN (SELECT o.id FROM Offer o WHERE o.owner.id NOT IN :kept)")
                .setParameter("kept", kept).executeUpdate();
        int offers = em.createQuery("DELETE FROM Offer o WHERE o.owner.id NOT IN :kept")
                .setParameter("kept", kept).executeUpdate();
        int requests = em.createQuery("DELETE FROM ShopRequest r WHERE r.user.id NOT IN :kept")
                .setParameter("kept", kept).executeUpdate();
        int shops = em.createQuery("DELETE FROM OwnerShop s WHERE s.user.id NOT IN :kept")
                .setParameter("kept", kept).executeUpdate();
        log.info("시연 시드: 손님 데이터를 지웠어요 (요청 {}건, 제안 {}건, 선주문 {}건, 가게 {}곳).",
                requests, offers, orders, shops);
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

    /** 예시주민{start}부터 count명. 있으면 그대로 쓰고 없으면 만들어요. 주민·동네도 다시 맞춰요. */
    private List<AppUser> fakeResidents(int start, int count, Long dongId) {
        List<AppUser> users = new ArrayList<>();
        for (int k = start; k < start + count; k++) {
            String nickname = FAKE_PREFIX + k;
            AppUser user = userRepository.findFirstByNicknameOrderByIdAsc(nickname)
                    .orElseGet(() -> userRepository.save(new AppUser(nickname)));
            user.changeProfile(Role.RESIDENT, dongId);
            users.add(user);
        }
        return users;
    }

    /** 예시 반찬가게 사장님. 없으면 만들어요. */
    private AppUser fakeOwner() {
        return userRepository.findFirstByNicknameOrderByIdAsc(FAKE_OWNER)
                .orElseGet(() -> {
                    AppUser user = new AppUser(FAKE_OWNER);
                    user.changeProfile(Role.OWNER, null);
                    return userRepository.save(user);
                });
    }

    /** 시연 계정. 닉네임으로만 찾아요(역할은 resetDemoAccounts 에서 되돌려요). */
    private AppUser demoUser(Role role) {
        String nickname = AuthService.demoNickname(role);
        return userRepository.findFirstByNicknameOrderByIdAsc(nickname)
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
