/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.dtos;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.entities.ArtifactDetails;
import io.harness.entities.InstanceType;
import io.harness.entities.instanceinfo.GitopsInstanceInfo;
import io.harness.ng.core.environment.beans.EnvironmentType;

import lombok.Builder;
import lombok.Data;
import lombok.experimental.UtilityClass;
import org.hibernate.validator.constraints.NotEmpty;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;

@Data
@Builder
@OwnedBy(HarnessTeam.GITOPS)
public class GitOpsInstanceDTO {
  private String accountIdentifier;
  private String orgIdentifier;
  private String projectIdentifier;
  private String instanceKey;
  @NotEmpty private InstanceType instanceType;

  private String envIdentifier;
  private String envName;
  private EnvironmentType envType;

  private String serviceIdentifier;
  private String serviceName;

  private String infrastructureMappingId;
  private String infrastructureKind;
  private String connectorRef;

  private String infraIdentifier;
  private String infraName;

  private ArtifactDetails primaryArtifact;

  private String lastDeployedById;
  private String lastDeployedByName;
  private long lastDeployedAt;

  private String lastPipelineExecutionId;
  private String lastPipelineExecutionName;

  private GitopsInstanceInfo instanceInfo;

  private boolean isDeleted;
  private long deletedAt;
  @CreatedDate Long createdAt;
  @LastModifiedDate Long lastModifiedAt;

  @UtilityClass
  public static class InstanceKeysAdditional {
    public static final String instanceInfoPodName = "instanceInfo.podName";
    public static final String instanceInfoNamespace = "instanceInfo.namespace";
    public static final String instanceInfoClusterIdentifier = "instanceInfo.clusterIdentifier";
    public static final String instanceInfoAgentIdentifier = "instanceInfo.agentIdentifier";
  }
}
