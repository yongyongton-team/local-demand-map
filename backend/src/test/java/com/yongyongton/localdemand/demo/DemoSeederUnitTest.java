package com.yongyongton.localdemand.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.yongyongton.localdemand.auth.AppUserRepository;
import com.yongyongton.localdemand.auth.DongLocator;
import com.yongyongton.localdemand.auth.OwnerShopRepository;
import com.yongyongton.localdemand.dong.Dong;
import com.yongyongton.localdemand.request.RequestCondition;
import com.yongyongton.localdemand.request.ShopRequestRepository;
import com.yongyongton.localdemand.request.TimeSlot;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;

/** DB 없이 확인해요: 분포 계산이 정확한지, 동·업종이 없으면 멈추는지. */
class DemoSeederUnitTest {

    @Test
    @DisplayName("spread: 조건·시간대 건수가 정확히 나와요(구성동 과일 29건)")
    void spreadExact() {
        List<DemoSeeder.Spec> specs = DemoSeeder.spread(29, 17, 10, 7, 17, 9, 3);
        assertThat(specs).hasSize(29);
        assertThat(specs.stream().filter(s -> s.conditions().contains(RequestCondition.LATE_OPEN))).hasSize(17);
        assertThat(specs.stream().filter(s -> s.conditions().contains(RequestCondition.SMALL_QTY))).hasSize(10);
        assertThat(specs.stream().filter(s -> s.conditions().contains(RequestCondition.DELIVERY))).hasSize(7);
        assertThat(specs.stream().filter(s -> s.slot() == TimeSlot.WEEKDAY_EVENING)).hasSize(17);
        assertThat(specs.stream().filter(s -> s.slot() == TimeSlot.WEEKEND)).hasSize(9);
        assertThat(specs.stream().filter(s -> s.slot() == TimeSlot.WEEKDAY_DAY)).hasSize(3);
    }

    @Test
    @DisplayName("spread: 시간대 합이 건수와 다르면 잘못 쓴 숫자라 멈춰요")
    void spreadMismatch() {
        assertThatThrownBy(() -> DemoSeeder.spread(10, 1, 1, 1, 5, 4, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("동 데이터가 없으면 아무것도 넣지 않고 서버가 켜지지 않게 멈춰요")
    @SuppressWarnings("unchecked")
    void stopsWithoutBaseData() {
        AppUserRepository users = mock(AppUserRepository.class);
        OwnerShopRepository shops = mock(OwnerShopRepository.class);
        ShopRequestRepository requests = mock(ShopRequestRepository.class);
        EntityManager em = mock(EntityManager.class);
        TypedQuery<Dong> empty = mock(TypedQuery.class);
        when(em.createQuery(anyString(), eq(Dong.class))).thenReturn(empty);
        when(empty.setParameter(anyString(), org.mockito.ArgumentMatchers.any())).thenReturn(empty);
        when(empty.getResultStream()).thenReturn(Stream.empty());

        DemoSeeder seeder = new DemoSeeder(users, shops, requests, mock(DongLocator.class), em, false);

        assertThatThrownBy(() -> seeder.run(new DefaultApplicationArguments()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("동(adm_code " + DemoSeeder.GUSEONG + ")이 없어요");
        verifyNoInteractions(users, shops, requests);
    }
}
