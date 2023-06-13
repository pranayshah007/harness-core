/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.rbac.validator;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.accesscontrol.acl.api.AccessCheckResponseDTO;
import io.harness.accesscontrol.acl.api.AccessControlDTO;
import io.harness.accesscontrol.acl.api.PermissionCheckDTO;
import io.harness.accesscontrol.clients.AccessControlClient;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ng.core.EntityDetail;
import io.harness.pms.pipeline.PipelineSetupUsageHelper;
import io.harness.pms.rbac.PipelineRbacHelper;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.PIPELINE)
@Singleton
@Slf4j
public class PipelineRbacServiceImpl implements PipelineRbacService {
  @Inject private PipelineSetupUsageHelper pipelineSetupUsageHelper;
  @Inject private AccessControlClient accessControlClient;
  @Inject private PipelineRbacHelper pipelineRbacHelper;

  public void extractAndValidateStaticallyReferredEntities(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, String pipelineId, String pipelineYaml) {
    long start = System.currentTimeMillis();
    List<EntityDetail> entityDetails = pipelineSetupUsageHelper.getReferencesOfPipeline(
        accountIdentifier, orgIdentifier, projectIdentifier, pipelineId, pipelineYaml, null);
    validateStaticallyReferredEntities(entityDetails);
    log.info("[PMS_RBAC] Rbac validation for referred entities for size {} took {}ms", entityDetails.size(),
        System.currentTimeMillis() - start);
  }

  public void extractAndValidateStaticallyReferredEntities(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, String pipelineId, JsonNode pipelineJsonNode) {
    long start = System.currentTimeMillis();
    List<EntityDetail> entityDetails = pipelineSetupUsageHelper.getReferencesOfPipeline(
        accountIdentifier, orgIdentifier, projectIdentifier, pipelineId, pipelineJsonNode, null);
    validateStaticallyReferredEntities(entityDetails);
    log.info("[PMS_RBAC] Rbac validation for referred entities for size {} took {}ms", entityDetails.size(),
        System.currentTimeMillis() - start);
  }

  public void validateStaticallyReferredEntities(List<EntityDetail> entityDetails) {
    List<PermissionCheckDTO> permissionCheckDTOS =
        entityDetails.stream().map(pipelineRbacHelper::convertToPermissionCheckDTO).collect(Collectors.toList());
    if (isNotEmpty(permissionCheckDTOS)) {
      AccessCheckResponseDTO accessCheckResponseDTO = accessControlClient.checkForAccess(permissionCheckDTOS);
      if (accessCheckResponseDTO == null) {
        return;
      }
      List<AccessControlDTO> nonPermittedResources = accessCheckResponseDTO.getAccessControlList()
                                                         .stream()
                                                         .filter(accessControlDTO -> !accessControlDTO.isPermitted())
                                                         .collect(Collectors.toList());
      if (nonPermittedResources.size() != 0) {
        PipelineRbacHelper.throwAccessDeniedError(nonPermittedResources);
      }
    }
  }
}
