/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.helper;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.dtos.InfrastructureMappingDTO;
import io.harness.dtos.instancesyncperpetualtaskinfo.InstanceSyncPerpetualTaskInfoDTO;
import io.harness.exception.EntityNotFoundException;
import io.harness.ng.core.environment.beans.Environment;
import io.harness.ng.core.environment.services.EnvironmentService;
import io.harness.ng.core.service.entity.ServiceEntity;
import io.harness.ng.core.service.services.ServiceEntityService;
import io.harness.service.instancesyncperpetualtask.InstanceSyncPerpetualTaskService;
import io.harness.service.instancesyncperpetualtaskinfo.InstanceSyncPerpetualTaskInfoService;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Optional;
import lombok.AllArgsConstructor;

@OwnedBy(HarnessTeam.DX)
@Singleton
@AllArgsConstructor(onConstructor = @__({ @Inject }))
public class InstanceSyncHelper {
  private final InstanceSyncPerpetualTaskInfoService instanceSyncPerpetualTaskInfoService;
  private final InstanceSyncPerpetualTaskService instanceSyncPerpetualTaskService;
  private final ServiceEntityService serviceEntityService;
  private final EnvironmentService environmentService;

  public void cleanUpInstanceSyncPerpetualTaskInfo(InstanceSyncPerpetualTaskInfoDTO instanceSyncPerpetualTaskInfoDTO) {
    instanceSyncPerpetualTaskService.deletePerpetualTask(
        instanceSyncPerpetualTaskInfoDTO.getAccountIdentifier(), instanceSyncPerpetualTaskInfoDTO.getPerpetualTaskId());
    instanceSyncPerpetualTaskInfoService.deleteById(
        instanceSyncPerpetualTaskInfoDTO.getAccountIdentifier(), instanceSyncPerpetualTaskInfoDTO.getId());
  }

  public ServiceEntity fetchService(InfrastructureMappingDTO infrastructureMappingDTO) {
    Optional<ServiceEntity> serviceEntityOptional = serviceEntityService.get(
        infrastructureMappingDTO.getAccountIdentifier(), infrastructureMappingDTO.getOrgIdentifier(),
        infrastructureMappingDTO.getProjectIdentifier(), infrastructureMappingDTO.getServiceIdentifier(), false);
    return serviceEntityOptional.orElseThrow(()
                                                 -> new EntityNotFoundException("Service not found for serviceId : {}"
                                                     + infrastructureMappingDTO.getServiceIdentifier()));
  }

  public Environment fetchEnvironment(InfrastructureMappingDTO infrastructureMappingDTO) {
    Optional<Environment> environmentServiceOptional = environmentService.get(
        infrastructureMappingDTO.getAccountIdentifier(), infrastructureMappingDTO.getOrgIdentifier(),
        infrastructureMappingDTO.getProjectIdentifier(), infrastructureMappingDTO.getEnvIdentifier(), false);
    return environmentServiceOptional.orElseThrow(
        ()
            -> new EntityNotFoundException(
                "Environment not found for envId : {}" + infrastructureMappingDTO.getEnvIdentifier()));
  }
}
