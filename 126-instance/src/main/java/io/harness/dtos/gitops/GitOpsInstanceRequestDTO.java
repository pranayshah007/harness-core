package io.harness.dtos.gitops;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.entities.instanceinfo.K8sBasicInfo;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;

import io.harness.entities.instanceinfo.K8sBasicInfo;
import lombok.Builder;
import lombok.Value;

@OwnedBy(HarnessTeam.CDP)
@Value
@Builder
public class GitOpsInstanceRequestDTO {
  @NotEmpty private String orgIdentifier;
  @NotEmpty private String projectIdentifier;
  @NotEmpty private String envIdentifierserviceIdentifier;
  @NotEmpty private String serviceIdentifier;
  @NotEmpty private String instanceKey;
  @NotEmpty private String lastDeployedById;
  @NotEmpty private String lastDeployedByName;
  @NotNull  private long lastDeployedAt;
  @NotNull  private K8sBasicInfo instanceInfo; // private K8sBasicInfo instanceInfo
  /* can derive (set ourselves)
  private String envName;
  private EnvironmentType envType;
  String serviceName;
  Long createdAt;
  Long lastModifiedAt;
  private boolean isDeleted; // default -- not deleted
   */
}
