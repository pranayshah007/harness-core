package io.harness.beans.execution;

import io.harness.remote.client.ServiceHttpClientConfig;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class QueueServiceClient {
  ServiceHttpClientConfig queueServiceConfig;
  String authToken;
}
