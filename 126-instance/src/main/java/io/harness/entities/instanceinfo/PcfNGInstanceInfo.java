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
public class PcfNGInstanceInfo extends InstanceInfo {
  @NotNull private String id;
  @NotNull private String organization;
  @NotNull private String space;
  @NotNull private String pcfApplicationName;
  private String pcfApplicationGuid;
  private String instanceIndex;
}
