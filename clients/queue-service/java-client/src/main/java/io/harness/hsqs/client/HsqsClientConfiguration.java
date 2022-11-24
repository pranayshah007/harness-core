package io.harness.hsqs.client;

import io.harness.remote.client.ServiceHttpClientConfig;
import io.harness.secret.ConfigSecret;
import lombok.Builder;
import lombok.Data;


@Data
@Builder
public class HsqsClientConfiguration {
    ServiceHttpClientConfig serviceHttpClientConfig;
    @ConfigSecret private String serviceSecret;
}
