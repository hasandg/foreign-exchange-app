package com.hasandag.exchange.rate.config;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Configuration
public class WebClientConfig {

    private final String exchangeApiUrl;
    private final Duration connectTimeout;
    private final Duration readTimeout;
    private final Duration writeTimeout;
    private final Duration responseTimeout;

    public WebClientConfig(@Value("${exchange.api.url}") String exchangeApiUrl,
                          @Value("${exchange.webclient.connect-timeout:10s}") Duration connectTimeout,
                          @Value("${exchange.webclient.read-timeout:10s}") Duration readTimeout,
                          @Value("${exchange.webclient.write-timeout:10s}") Duration writeTimeout,
                          @Value("${exchange.webclient.response-timeout:15s}") Duration responseTimeout) {
        this.exchangeApiUrl = exchangeApiUrl;
        this.connectTimeout = connectTimeout;
        this.readTimeout = readTimeout;
        this.writeTimeout = writeTimeout;
        this.responseTimeout = responseTimeout;
    }

    @Bean
    public WebClient exchangeRateApiWebClient(WebClient.Builder webClientBuilder) {
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, (int) connectTimeout.toMillis())
                .responseTimeout(responseTimeout)
                .doOnConnected(conn -> conn
                        .addHandlerLast(new ReadTimeoutHandler(readTimeout.toSeconds(), TimeUnit.SECONDS))
                        .addHandlerLast(new WriteTimeoutHandler(writeTimeout.toSeconds(), TimeUnit.SECONDS)));

        return webClientBuilder
                .baseUrl(exchangeApiUrl)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }
} 