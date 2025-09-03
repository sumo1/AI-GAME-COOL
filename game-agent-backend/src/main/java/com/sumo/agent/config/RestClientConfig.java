package com.sumo.agent.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.InetSocketAddress;
import java.net.Proxy;

/**
 * 统一提供可选代理与超时设置的 RestClient.Builder。
 * - 通过 application.yml 或环境变量配置：
 *   proxy.enabled / proxy.host / proxy.port / proxy.type(http|socks5)
 */
@Configuration
public class RestClientConfig {

    @Value("${proxy.enabled:false}")
    private boolean proxyEnabled;

    @Value("${proxy.host:127.0.0.1}")
    private String proxyHost;

    @Value("${proxy.port:7890}")
    private int proxyPort;

    @Value("${proxy.type:http}")
    private String proxyType;

    // 可配置的连接/读取超时（毫秒）。
    // 大模型长文本生成可能超过 60s，默认将读取超时提升到 180s。
    @Value("${http.client.connect-timeout-ms:30000}")
    private int connectTimeoutMs;

    @Value("${http.client.read-timeout-ms:180000}")
    private int readTimeoutMs;

    @Bean
    public RestClient.Builder restClientBuilder() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();

        // 设置超时（连接/读取）。
        // 摘要：为避免 DashScope 长响应被过早中断，将 read timeout 默认提高到 180s。
        requestFactory.setConnectTimeout(connectTimeoutMs);
        requestFactory.setReadTimeout(readTimeoutMs);

        if (proxyEnabled) {
            Proxy.Type type = "socks5".equalsIgnoreCase(proxyType) ? Proxy.Type.SOCKS : Proxy.Type.HTTP;
            Proxy proxy = new Proxy(type, new InetSocketAddress(proxyHost, proxyPort));
            requestFactory.setProxy(proxy);
        }

        return RestClient.builder().requestFactory(requestFactory);
    }
}
