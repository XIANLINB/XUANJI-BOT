package XuanJi.core.storage;

import java.util.Map;

/**
 * 平台运行健康指标 SPI — 控制台「运行健康」页聚合各平台熔断/连接质量等指标。
 */
public interface HealthMetricProvider {

    /** 平台标识：qqbot / onebot。 */
    String platform();

    /** 该平台健康指标快照（熔断、重连次数、心跳延迟等），键值自定。 */
    Map<String, Object> healthMetrics();
}
