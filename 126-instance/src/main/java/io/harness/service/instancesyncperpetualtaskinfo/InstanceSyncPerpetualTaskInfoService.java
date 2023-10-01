/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.service.instancesyncperpetualtaskinfo;
import static io.harness.annotations.dev.HarnessTeam.DX;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.dtos.instancesyncperpetualtaskinfo.InstanceSyncPerpetualTaskInfoDTO;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_K8S})
@OwnedBy(DX)
public interface InstanceSyncPerpetualTaskInfoService {
  Optional<InstanceSyncPerpetualTaskInfoDTO> findByInfrastructureMappingId(String infrastructureMappingId);

  Optional<InstanceSyncPerpetualTaskInfoDTO> findByPerpetualTaskId(String accountIdentifier, String perpetualTaskId);

  InstanceSyncPerpetualTaskInfoDTO save(InstanceSyncPerpetualTaskInfoDTO instanceSyncPerpetualTaskInfoDTO);

  void deleteById(String accountIdentifier, String instanceSyncPerpetualTaskInfoId);

  InstanceSyncPerpetualTaskInfoDTO updateDeploymentInfoDetailsList(
      InstanceSyncPerpetualTaskInfoDTO instanceSyncPerpetualTaskInfoDTO);

  InstanceSyncPerpetualTaskInfoDTO updateDeploymentInfoListAndConnectorId(
      InstanceSyncPerpetualTaskInfoDTO instanceSyncPerpetualTaskInfoDTO, String connectorIdentifier);

  InstanceSyncPerpetualTaskInfoDTO updatePerpetualTaskIdV1OrV2(
      InstanceSyncPerpetualTaskInfoDTO instanceSyncPerpetualTaskInfoDTO);
  InstanceSyncPerpetualTaskInfoDTO updateLastSuccessfulRun(
      InstanceSyncPerpetualTaskInfoDTO instanceSyncPerpetualTaskInfoDTO);

  List<InstanceSyncPerpetualTaskInfoDTO> findAll(String accountId, String perpetualTaskId);

  Page<InstanceSyncPerpetualTaskInfoDTO> findAllInPages(Pageable pageRequest, String accountId, String perpetualTaskId);
}
