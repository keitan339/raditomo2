package com.raditomo.config;

import com.raditomo.auth.service.GoogleOAuthProperties;
import com.raditomo.radiko.RadikoProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

@Configuration
@EnableConfigurationProperties({GoogleOAuthProperties.class, RadikoProperties.class})
public class AppConfig {

    @Bean
    public RestClient restClient() {
        return RestClient.builder().build();
    }

    @Bean
    public HttpClient radikoHttpClient(RadikoProperties props) {
        return HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(props.httpConnectTimeoutSeconds()))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }
}
