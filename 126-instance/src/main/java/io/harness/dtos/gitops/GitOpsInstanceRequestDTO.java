package io.harness.dtos.gitops;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.entities.instanceinfo.InstanceInfo;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Value;

@OwnedBy(HarnessTeam.CDP)
@Value
@Builder
public class GitOpsInstanceRequestDTO {
  @NotEmpty private String orgIdentifier;
  @NotEmpty private String projectIdentifier;
  @NotEmpty private String envIdentifier;
  @NotEmpty private String serviceIdentifier;
  @NotEmpty private String instanceKey;
  @NotEmpty private String lastDeployedById;
  @NotEmpty private String lastDeployedByName;
  @NotEmpty private long lastDeployedAt;
  @NotNull private InstanceInfo instanceInfo; // private K8sBasicInfo instanceInfo
  /* can derive (set ourselves)
  private String envName;
  private EnvironmentType envType;
  String serviceName;
  Long createdAt;
  Long lastModifiedAt;
  private boolean isDeleted; // default -- not deleted
   */
}
