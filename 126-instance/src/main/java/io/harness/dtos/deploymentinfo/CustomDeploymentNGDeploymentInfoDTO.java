package io.harness.dtos.deploymentinfo;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;
import io.harness.ng.core.k8s.ServiceSpecType;
import io.harness.util.InstanceSyncKey;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.codehaus.commons.nullanalysis.NotNull;

@JsonIgnoreProperties(ignoreUnknown = true)
@Data
@Builder
@EqualsAndHashCode(callSuper = true)
@OwnedBy(CDP)
public class CustomDeploymentNGDeploymentInfoDTO extends DeploymentInfoDTO {
  @NotNull private String instanceFetchScriptHash;
  private String instanceFetchScript;
  private String scriptOutput;
  private List<String> tags;
  private String artifactName;
  private String artifactSourceName;
  private String artifactStreamId;
  private String artifactBuildNum;
  @Override
  public String getType() {
    return ServiceSpecType.CUSTOM_DEPLOYMENT;
  }
  @Override
  public String prepareInstanceSyncHandlerKey() {
    return InstanceSyncKey.builder().part(instanceFetchScriptHash).build().toString();
  }
}
