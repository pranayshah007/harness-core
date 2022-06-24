package io.harness.dtos.gitops;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.entities.instanceinfo.InstanceInfo;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
@OwnedBy(HarnessTeam.CDP)
@Getter
@Setter
@Builder
public class GitOpsInstanceRequestDTO {
  private String orgIdentifier;
  private String projectIdentifier;
  private String envIdentifier;
  private String serviceIdentifier;
  private String instanceKey;
  private String lastDeployedById;
  private String lastDeployedByName;
  private long lastDeployedAt;
  private InstanceInfo instanceInfo; // private K8sBasicInfo instanceInfo

  /* can derive (set ourselves)
  private String envName;
  private EnvironmentType envType;
  String serviceName;
  Long createdAt;
  Long lastModifiedAt;
  private boolean isDeleted; // default -- not deleted
   */
}
