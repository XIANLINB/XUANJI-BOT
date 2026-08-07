package dev.xuanji.adapter.onebot.config;

import lombok.Generated;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix="xuanji.onebot")
public class OneBotProperties {
    private boolean enabled = false;
    private long apiTimeoutMs = 10000L;
    private boolean ignoreSelfMessage = true;
    private Reverse reverse = new Reverse();
    private Forward forward = new Forward();

    @Generated
    public OneBotProperties() {
    }

    @Generated
    public boolean isEnabled() {
        return this.enabled;
    }

    @Generated
    public long getApiTimeoutMs() {
        return this.apiTimeoutMs;
    }

    @Generated
    public boolean isIgnoreSelfMessage() {
        return this.ignoreSelfMessage;
    }

    @Generated
    public Reverse getReverse() {
        return this.reverse;
    }

    @Generated
    public Forward getForward() {
        return this.forward;
    }

    @Generated
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    @Generated
    public void setApiTimeoutMs(long apiTimeoutMs) {
        this.apiTimeoutMs = apiTimeoutMs;
    }

    @Generated
    public void setIgnoreSelfMessage(boolean ignoreSelfMessage) {
        this.ignoreSelfMessage = ignoreSelfMessage;
    }

    @Generated
    public void setReverse(Reverse reverse) {
        this.reverse = reverse;
    }

    @Generated
    public void setForward(Forward forward) {
        this.forward = forward;
    }

    @Generated
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }
        if (!(o instanceof OneBotProperties)) {
            return false;
        }
        OneBotProperties other = (OneBotProperties)o;
        if (!other.canEqual(this)) {
            return false;
        }
        if (this.isEnabled() != other.isEnabled()) {
            return false;
        }
        if (this.getApiTimeoutMs() != other.getApiTimeoutMs()) {
            return false;
        }
        if (this.isIgnoreSelfMessage() != other.isIgnoreSelfMessage()) {
            return false;
        }
        Reverse this$reverse = this.getReverse();
        Reverse other$reverse = other.getReverse();
        if (this$reverse == null ? other$reverse != null : !((Object)this$reverse).equals(other$reverse)) {
            return false;
        }
        Forward this$forward = this.getForward();
        Forward other$forward = other.getForward();
        return !(this$forward == null ? other$forward != null : !((Object)this$forward).equals(other$forward));
    }

    @Generated
    protected boolean canEqual(Object other) {
        return other instanceof OneBotProperties;
    }

    @Generated
    public int hashCode() {
        int PRIME = 59;
        int result = 1;
        result = result * 59 + (this.isEnabled() ? 79 : 97);
        long $apiTimeoutMs = this.getApiTimeoutMs();
        result = result * 59 + (int)($apiTimeoutMs >>> 32 ^ $apiTimeoutMs);
        result = result * 59 + (this.isIgnoreSelfMessage() ? 79 : 97);
        Reverse $reverse = this.getReverse();
        result = result * 59 + ($reverse == null ? 43 : ((Object)$reverse).hashCode());
        Forward $forward = this.getForward();
        result = result * 59 + ($forward == null ? 43 : ((Object)$forward).hashCode());
        return result;
    }

    @Generated
    public String toString() {
        return "OneBotProperties(enabled=" + this.isEnabled() + ", apiTimeoutMs=" + this.getApiTimeoutMs() + ", ignoreSelfMessage=" + this.isIgnoreSelfMessage() + ", reverse=" + String.valueOf(this.getReverse()) + ", forward=" + String.valueOf(this.getForward()) + ")";
    }

    public static class Reverse {
        private boolean enabled = true;
        private String path = "/onebot/ws";
        private String accessToken = "";

        @Generated
        public Reverse() {
        }

        @Generated
        public boolean isEnabled() {
            return this.enabled;
        }

        @Generated
        public String getPath() {
            return this.path;
        }

        @Generated
        public String getAccessToken() {
            return this.accessToken;
        }

        @Generated
        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        @Generated
        public void setPath(String path) {
            this.path = path;
        }

        @Generated
        public void setAccessToken(String accessToken) {
            this.accessToken = accessToken;
        }

        @Generated
        public boolean equals(Object o) {
            if (o == this) {
                return true;
            }
            if (!(o instanceof Reverse)) {
                return false;
            }
            Reverse other = (Reverse)o;
            if (!other.canEqual(this)) {
                return false;
            }
            if (this.isEnabled() != other.isEnabled()) {
                return false;
            }
            String this$path = this.getPath();
            String other$path = other.getPath();
            if (this$path == null ? other$path != null : !this$path.equals(other$path)) {
                return false;
            }
            String this$accessToken = this.getAccessToken();
            String other$accessToken = other.getAccessToken();
            return !(this$accessToken == null ? other$accessToken != null : !this$accessToken.equals(other$accessToken));
        }

        @Generated
        protected boolean canEqual(Object other) {
            return other instanceof Reverse;
        }

        @Generated
        public int hashCode() {
            int PRIME = 59;
            int result = 1;
            result = result * 59 + (this.isEnabled() ? 79 : 97);
            String $path = this.getPath();
            result = result * 59 + ($path == null ? 43 : $path.hashCode());
            String $accessToken = this.getAccessToken();
            result = result * 59 + ($accessToken == null ? 43 : $accessToken.hashCode());
            return result;
        }

        @Generated
        public String toString() {
            return "OneBotProperties.Reverse(enabled=" + this.isEnabled() + ", path=" + this.getPath() + ", accessToken=" + this.getAccessToken() + ")";
        }
    }

    public static class Forward {
        private boolean enabled = false;
        private String url = "";
        private String accessToken = "";
        private long reconnectIntervalMs = 5000L;

        @Generated
        public Forward() {
        }

        @Generated
        public boolean isEnabled() {
            return this.enabled;
        }

        @Generated
        public String getUrl() {
            return this.url;
        }

        @Generated
        public String getAccessToken() {
            return this.accessToken;
        }

        @Generated
        public long getReconnectIntervalMs() {
            return this.reconnectIntervalMs;
        }

        @Generated
        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        @Generated
        public void setUrl(String url) {
            this.url = url;
        }

        @Generated
        public void setAccessToken(String accessToken) {
            this.accessToken = accessToken;
        }

        @Generated
        public void setReconnectIntervalMs(long reconnectIntervalMs) {
            this.reconnectIntervalMs = reconnectIntervalMs;
        }

        @Generated
        public boolean equals(Object o) {
            if (o == this) {
                return true;
            }
            if (!(o instanceof Forward)) {
                return false;
            }
            Forward other = (Forward)o;
            if (!other.canEqual(this)) {
                return false;
            }
            if (this.isEnabled() != other.isEnabled()) {
                return false;
            }
            if (this.getReconnectIntervalMs() != other.getReconnectIntervalMs()) {
                return false;
            }
            String this$url = this.getUrl();
            String other$url = other.getUrl();
            if (this$url == null ? other$url != null : !this$url.equals(other$url)) {
                return false;
            }
            String this$accessToken = this.getAccessToken();
            String other$accessToken = other.getAccessToken();
            return !(this$accessToken == null ? other$accessToken != null : !this$accessToken.equals(other$accessToken));
        }

        @Generated
        protected boolean canEqual(Object other) {
            return other instanceof Forward;
        }

        @Generated
        public int hashCode() {
            int PRIME = 59;
            int result = 1;
            result = result * 59 + (this.isEnabled() ? 79 : 97);
            long $reconnectIntervalMs = this.getReconnectIntervalMs();
            result = result * 59 + (int)($reconnectIntervalMs >>> 32 ^ $reconnectIntervalMs);
            String $url = this.getUrl();
            result = result * 59 + ($url == null ? 43 : $url.hashCode());
            String $accessToken = this.getAccessToken();
            result = result * 59 + ($accessToken == null ? 43 : $accessToken.hashCode());
            return result;
        }

        @Generated
        public String toString() {
            return "OneBotProperties.Forward(enabled=" + this.isEnabled() + ", url=" + this.getUrl() + ", accessToken=" + this.getAccessToken() + ", reconnectIntervalMs=" + this.getReconnectIntervalMs() + ")";
        }
    }
}

