/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.mappers.instanceinfo;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.dtos.instanceinfo.GitOpsInstanceInfoDTO;
import io.harness.entities.instanceinfo.GitopsInstanceInfo;

import lombok.NonNull;
import lombok.experimental.UtilityClass;

@OwnedBy(HarnessTeam.GITOPS)
@UtilityClass
public class GitOpsInstanceInfoMapper {
  @NonNull
  public GitOpsInstanceInfoDTO toDTO(GitopsInstanceInfo info) {
    return GitOpsInstanceInfoDTO.builder()
        .containerList(info.getContainerList())
        .namespace(info.getNamespace())
        .podId(info.getPodId())
        .appIdentifier(info.getAppIdentifier())
        .clusterIdentifier(info.getClusterIdentifier())
        .podName(info.getPodName())
        .agentIdentifier(info.getAgentIdentifier())
        .build();
  }

  @NonNull
  public GitopsInstanceInfo toEntity(GitOpsInstanceInfoDTO info) {
    return GitopsInstanceInfo.builder()
        .containerList(info.getContainerList())
        .namespace(info.getNamespace())
        .appIdentifier(info.getAppIdentifier())
        .clusterIdentifier(info.getClusterIdentifier())
        .podId(info.getPodId())
        .podName(info.getPodName())
        .agentIdentifier(info.getAgentIdentifier())
        .build();
  }
}
