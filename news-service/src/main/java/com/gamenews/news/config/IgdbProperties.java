package com.gamenews.news.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.igdb")
@Getter
@Setter
public class IgdbProperties {

    private String clientId;
    private String clientSecret;
    private String apiBaseUrl = "https://api.igdb.com/v4";
    private String tokenUrl = "https://id.twitch.tv/oauth2/token";

    public boolean isConfigured() {
        return clientId != null && !clientId.isBlank()
                && clientSecret != null && !clientSecret.isBlank();
    }
}
