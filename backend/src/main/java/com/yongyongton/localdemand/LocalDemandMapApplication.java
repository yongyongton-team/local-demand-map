package com.yongyongton.localdemand;

import java.util.TimeZone;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class LocalDemandMapApplication {

    public static void main(String[] args) {
        // 명세의 시각은 모두 한국 시간이에요. 서버 기본 시간대가 UTC인 클라우드에서도 같게 맞춰요.
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Seoul"));
        SpringApplication.run(LocalDemandMapApplication.class, args);
    }
}
