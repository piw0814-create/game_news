package com.gamenews.collector.config;

import com.gamenews.collector.source.RssSourceConfig;
import io.netty.channel.ChannelOption;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

@Configuration
public class WebClientConfig {

    private static final int MAX_IN_MEMORY_SIZE = 4 * 1024 * 1024;

    @Bean
    public WebClient.Builder webClientBuilder(RssSourceConfig sourceConfig) {
        HttpClient httpClient = HttpClient.create()
                .option(
                        ChannelOption.CONNECT_TIMEOUT_MILLIS,
                        sourceConfig.getHttp().getConnectTimeoutMs()
                );

        return WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .codecs(configurer ->
                        configurer.defaultCodecs().maxInMemorySize(MAX_IN_MEMORY_SIZE));
    }
}
