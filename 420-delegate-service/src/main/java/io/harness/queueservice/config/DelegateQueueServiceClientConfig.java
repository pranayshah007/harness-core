package io.harness.queueservice.config;

import io.harness.remote.client.ServiceHttpClientConfig;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class DelegateQueueServiceClientConfig {
    ServiceHttpClientConfig queueServiceConfig;
    String authToken;
}
