package io.harness.terragrunt.v2.request;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Value;
import lombok.experimental.SuperBuilder;

@Value
@OwnedBy(CDP)
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class TerragruntCliRequest extends AbstractTerragruntCliRequest {
  @Builder.Default TerragruntCliArgs args = TerragruntCliArgs.builder().build();
}
