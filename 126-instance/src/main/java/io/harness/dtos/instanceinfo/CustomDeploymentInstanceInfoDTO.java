package io.harness.dtos.instanceinfo;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.util.InstanceSyncKey;

import java.util.Map;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
@OwnedBy(HarnessTeam.CDP)
@Data
@Builder
@EqualsAndHashCode(callSuper = true)
public class CustomDeploymentInstanceInfoDTO extends InstanceInfoDTO {
  @NotNull private String hostname;
  @NotNull private String instanceFetchScript;
  private Map<String, Object> properties;

  @Override
  public String prepareInstanceKey() {
    return InstanceSyncKey.builder().clazz(CustomDeploymentInstanceInfoDTO.class).part(hostname).build().toString();
  }

  @Override
  public String prepareInstanceSyncHandlerKey() {
    return InstanceSyncKey.builder().part(instanceFetchScript).build().toString();
  }

  @Override
  public String getPodName() {
    return hostname;
  }

  @Override
  public String getType() {
    return "CustomDeployment";
  }
}
