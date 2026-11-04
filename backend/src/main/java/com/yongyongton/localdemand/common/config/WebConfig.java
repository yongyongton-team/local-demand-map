package com.yongyongton.localdemand.common.config;

import com.yongyongton.localdemand.LocalDemandMapApplication;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.HandlerTypePredicate;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    /** 모든 API 주소 앞에 붙어요. 컨트롤러에는 명세처럼 "/dongs"만 쓰면 "/api/v1/dongs"가 돼요. */
    public static final String API_PREFIX = "/api/v1";

    @Override
    public void configurePathMatch(PathMatchConfigurer configurer) {
        configurer.addPathPrefix(API_PREFIX, HandlerTypePredicate.forBasePackageClass(LocalDemandMapApplication.class));
    }
}
