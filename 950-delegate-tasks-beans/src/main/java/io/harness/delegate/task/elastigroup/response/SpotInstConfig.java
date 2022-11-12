package io.harness.delegate.task.elastigroup.response;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.encryption.SecretRefData;
import io.harness.encryption.SecretReference;

import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
@OwnedBy(HarnessTeam.CDP)
public class SpotInstConfig {
  String accountId;
  @SecretReference SecretRefData accountIdRef;
  @NotNull @SecretReference SecretRefData apiTokenRef;
}
