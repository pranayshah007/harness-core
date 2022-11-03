package io.harness.entities.instanceinfo;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

@OwnedBy(HarnessTeam.CDP)
@Data
@Builder
@EqualsAndHashCode(callSuper = true)
public class SpotInstanceInfo extends InstanceInfo {
  @NotNull private String serviceType;
  @NotNull private String infrastructureKey;
  @NotNull private String ec2InstanceId;
  @NotNull private String elastigroupId;
}
