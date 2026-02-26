package com.naren.kadiri.chatdemo.langfuse;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration properties for Langfuse integration.
 */
@Configuration
@ConfigurationProperties(prefix = "langfuse")
public class LangfuseProperties {

    private boolean enabled = true;
    private String publicKey;
    private String secretKey;
    private String host = "https://cloud.langfuse.com";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getPublicKey() {
        return publicKey;
    }

    public void setPublicKey(String publicKey) {
        this.publicKey = publicKey;
    }

    public String getSecretKey() {
        return secretKey;
    }

    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public boolean isConfigured() {
        return publicKey != null && !publicKey.isBlank()
            && secretKey != null && !secretKey.isBlank();
    }
}
