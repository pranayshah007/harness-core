package io.harness.queueservice.config;


import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class DelegateQueueServiceConfig {
    DelegateQueueServiceClientConfig delegateQueueServiceClientConfig;
    String queueServiceToken;
    String topic;
}
