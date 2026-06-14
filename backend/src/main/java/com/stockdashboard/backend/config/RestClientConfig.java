package com.stockdashboard.backend.config;

import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.pool.PoolConcurrencyPolicy;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Configuration
public class RestClientConfig {

    /**
     * JDK HttpClient는 호스트당 커넥션 1개에 HTTP/2 멀티플렉싱을 사용해, 코스피 전체 종목
     * 갱신처럼 20개 스레드가 동시에 같은 호스트(m.stock.naver.com)로 요청을 보내면 서버의
     * 동시 스트림 제한을 초과해 "too many concurrent streams" 오류가 발생한다.
     * Apache HttpClient5의 커넥션 풀(호스트당 최대 30개)을 사용해 동시 요청을 여러 커넥션으로
     * 분산한다.
     */
    @Bean
    public RestClient restClient(RestClient.Builder builder) {
        ClientHttpRequestFactorySettings settings = ClientHttpRequestFactorySettings.defaults()
                .withConnectTimeout(Duration.ofSeconds(3))
                .withReadTimeout(Duration.ofSeconds(5));

        var connectionManager = PoolingHttpClientConnectionManagerBuilder.create()
                .setMaxConnTotal(60)
                .setMaxConnPerRoute(30)
                .setPoolConcurrencyPolicy(PoolConcurrencyPolicy.STRICT)
                .build();

        ClientHttpRequestFactoryBuilder<HttpComponentsClientHttpRequestFactory> factoryBuilder =
                ClientHttpRequestFactoryBuilder.httpComponents()
                        .withHttpClientCustomizer(clientBuilder -> clientBuilder.setConnectionManager(connectionManager));

        return builder
                .requestFactory(factoryBuilder.build(settings))
                .defaultHeader(HttpHeaders.USER_AGENT, "Mozilla/5.0")
                .build();
    }
}
