package io.harness.queueservice.config;


import io.harness.remote.client.ServiceHttpClientConfig;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class DelegateQueueServiceConfig {
    ServiceHttpClientConfig queueServiceConfig;
    String topic;
}
