package io.harness.accesscontrol;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.remote.client.ServiceHttpClientConfig;

import lombok.Value;

@OwnedBy(HarnessTeam.PL)
@Value
public class NgManagerServiceConfiguration {
  ServiceHttpClientConfig ngManagerServiceConfig;
  String ngManagerServiceSecret;
}
