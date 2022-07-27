package io.harness.cdng.gitops.beans;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Setter;
import lombok.Value;
import lombok.experimental.NonFinal;

@OwnedBy(HarnessTeam.CDP)
@Value
@Builder
public class GitOpsInstance {
  @NotEmpty private String accountIdentifier;
  @NotEmpty private String orgIdentifier;
  @NotEmpty private String projectIdentifier;
  @NotEmpty private String applicationIdentifier;
  @NotEmpty private String agentIdentifier;
  @NotEmpty private String envIdentifier;
  @NotEmpty private String serviceIdentifier;
  @NotEmpty private String serviceEnvIdentifier;
  @NotEmpty private String buildId;
  @NotEmpty private String clusterIdentifier;
  @NotNull private long creationTimestamp;
  @NotNull private long lastDeployedAt;
  @NotNull private GitOpsInstanceRequest.K8sBasicInfo instanceInfo; // PodInfo

  @NonFinal @Setter private String pipelineName;
  @NonFinal @Setter private Long lastExecutedAt;
  @NonFinal @Setter private String pipelineExecutionId;

  @NonFinal @Setter private String lastDeployedByName;
  @NonFinal @Setter private String lastDeployedById;
}
