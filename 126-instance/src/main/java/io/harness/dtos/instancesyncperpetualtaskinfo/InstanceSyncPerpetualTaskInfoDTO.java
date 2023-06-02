/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.dtos.instancesyncperpetualtaskinfo;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import java.util.List;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;

@OwnedBy(HarnessTeam.DX)
@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
@Builder
public class InstanceSyncPerpetualTaskInfoDTO {
  String id;
  String accountIdentifier;
  String infrastructureMappingId;
  List<DeploymentInfoDetailsDTO> deploymentInfoDetailsDTOList;
  String perpetualTaskId;
  String perpetualTaskIdV2;
  String connectorIdentifier;
  long createdAt;
  long lastUpdatedAt;
}
