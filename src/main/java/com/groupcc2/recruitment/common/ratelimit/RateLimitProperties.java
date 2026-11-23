package com.groupcc2.recruitment.common.ratelimit;

import org.springframework.boot.context.properties.ConfigurationProperties;
@ConfigurationProperties(prefix = "app.rate-limit")
public class RateLimitProperties {
    private boolean enabled = true;
    private String trustedProxies = "127.0.0.1,::1";
    private Policy login = new Policy(10, 900);
    private Policy registration = new Policy(5, 3600);
    private Policy recovery = new Policy(5, 3600);
    private Policy reset = new Policy(10, 3600);
    private Policy upload = new Policy(20, 3600);

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getTrustedProxies() { return trustedProxies; }
    public void setTrustedProxies(String trustedProxies) { this.trustedProxies = trustedProxies; }
    public Policy getLogin() { return login; }
    public void setLogin(Policy login) { this.login = login; }
    public Policy getRegistration() { return registration; }
    public void setRegistration(Policy registration) { this.registration = registration; }
    public Policy getRecovery() { return recovery; }
    public void setRecovery(Policy recovery) { this.recovery = recovery; }
    public Policy getReset() { return reset; }
    public void setReset(Policy reset) { this.reset = reset; }
    public Policy getUpload() { return upload; }
    public void setUpload(Policy upload) { this.upload = upload; }

    public static class Policy {
        private int requests;
        private int windowSeconds;

        public Policy() {}
        public Policy(int requests, int windowSeconds) {
            this.requests = requests;
            this.windowSeconds = windowSeconds;
        }
        public int getRequests() { return requests; }
        public void setRequests(int requests) { this.requests = requests; }
        public int getWindowSeconds() { return windowSeconds; }
        public void setWindowSeconds(int windowSeconds) { this.windowSeconds = windowSeconds; }
    }
}
