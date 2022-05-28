/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.service;

import io.harness.accesscontrol.acl.api.Principal;
import io.harness.accesscontrol.acl.api.Resource;
import io.harness.accesscontrol.acl.api.ResourceScope;
import io.harness.accesscontrol.clients.AccessControlClient;
import io.harness.accesscontrol.principals.PrincipalType;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.service.beans.ServiceConfig;
import io.harness.cdng.service.beans.ServiceDefinition;
import io.harness.cdng.service.steps.ServiceStepParameters;
import io.harness.cdng.service.steps.ServiceStepParametersV2;
import io.harness.common.ParameterFieldHelper;
import io.harness.data.structure.EmptyPredicate;
import io.harness.eventsframework.schemas.entity.EntityDetailProtoDTO;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.mapper.TagMapper;
import io.harness.ng.core.service.entity.ServiceEntity;
import io.harness.ng.core.service.services.ServiceEntityService;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.plan.ExecutionPrincipalInfo;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.rbac.NGResourceType;
import io.harness.pms.rbac.PipelineRbacHelper;
import io.harness.pms.rbac.PrincipalTypeProtoToPrincipalTypeMapper;
import io.harness.pms.tags.TagUtils;
import io.harness.rbac.CDNGRbacPermissions;
import io.harness.steps.EntityReferenceExtractorUtils;

import java.util.Optional;
import java.util.Set;
import lombok.experimental.UtilityClass;

@OwnedBy(HarnessTeam.CDC)
@UtilityClass
public class ServiceStepUtils {
  public void validateResources(EntityReferenceExtractorUtils entityReferenceExtractorUtils,
      PipelineRbacHelper pipelineRbacHelper, AccessControlClient accessControlClient, Ambiance ambiance,
      ServiceStepParameters stepParameters) {
    String accountIdentifier = AmbianceUtils.getAccountId(ambiance);
    String orgIdentifier = AmbianceUtils.getOrgIdentifier(ambiance);
    String projectIdentifier = AmbianceUtils.getProjectIdentifier(ambiance);
    ExecutionPrincipalInfo executionPrincipalInfo = ambiance.getMetadata().getPrincipalInfo();
    String principal = executionPrincipalInfo.getPrincipal();
    if (EmptyPredicate.isEmpty(principal)) {
      return;
    }

    PrincipalType principalType = PrincipalTypeProtoToPrincipalTypeMapper.convertToAccessControlPrincipalType(
        executionPrincipalInfo.getPrincipalType());
    ServiceConfig serviceConfig = stepParameters.getServiceConfigInternal().getValue();
    Set<EntityDetailProtoDTO> entityDetails =
        entityReferenceExtractorUtils.extractReferredEntities(ambiance, serviceConfig);
    pipelineRbacHelper.checkRuntimePermissions(ambiance, entityDetails);
    if (stepParameters.getServiceRefInternal() == null
        || EmptyPredicate.isEmpty(stepParameters.getServiceRefInternal().getValue())) {
      accessControlClient.checkForAccessOrThrow(Principal.of(principalType, principal),
          ResourceScope.of(accountIdentifier, orgIdentifier, projectIdentifier), Resource.of("SERVICE", null),
          CDNGRbacPermissions.SERVICE_CREATE_PERMISSION, "Validation for Service Step failed");
    }
  }

  public void validateResourcesV2(EntityReferenceExtractorUtils entityReferenceExtractorUtils,
      PipelineRbacHelper pipelineRbacHelper, AccessControlClient accessControlClient, Ambiance ambiance,
      ServiceStepParametersV2 stepParameters) {
    String accountIdentifier = AmbianceUtils.getAccountId(ambiance);
    String orgIdentifier = AmbianceUtils.getOrgIdentifier(ambiance);
    String projectIdentifier = AmbianceUtils.getProjectIdentifier(ambiance);
    ExecutionPrincipalInfo executionPrincipalInfo = ambiance.getMetadata().getPrincipalInfo();
    String principal = executionPrincipalInfo.getPrincipal();
    if (EmptyPredicate.isEmpty(principal)) {
      return;
    }

    PrincipalType principalType = PrincipalTypeProtoToPrincipalTypeMapper.convertToAccessControlPrincipalType(
        executionPrincipalInfo.getPrincipalType());
    ServiceDefinition serviceDefinition = stepParameters.getServiceDefinition().getValue();
    Set<EntityDetailProtoDTO> entityDetails =
        entityReferenceExtractorUtils.extractReferredEntities(ambiance, serviceDefinition);
    pipelineRbacHelper.checkRuntimePermissions(ambiance, entityDetails);
    accessControlClient.checkForAccessOrThrow(Principal.of(principalType, principal),
        ResourceScope.of(accountIdentifier, orgIdentifier, projectIdentifier),
        Resource.of(NGResourceType.SERVICE, stepParameters.getIdentifier()),
        CDNGRbacPermissions.SERVICE_RUNTIME_PERMISSION, "Validation for Service Step failed");
  }

  // NOTE: Returned service entity shouldn't contain a version. Multiple stages running in parallel might see
  // DuplicateKeyException if they're trying to deploy the same service.
  public ServiceEntity getServiceEntity(
      ServiceEntityService serviceEntityService, Ambiance ambiance, ServiceStepParameters stepParameters) {
    String accountId = AmbianceUtils.getAccountId(ambiance);
    String projectIdentifier = AmbianceUtils.getProjectIdentifier(ambiance);
    String orgIdentifier = AmbianceUtils.getOrgIdentifier(ambiance);

    if (stepParameters.getServiceRefInternal() != null
        && EmptyPredicate.isNotEmpty(stepParameters.getServiceRefInternal().getValue())) {
      String serviceIdentifier = stepParameters.getServiceRefInternal().getValue();
      Optional<ServiceEntity> serviceEntity =
          serviceEntityService.get(accountId, orgIdentifier, projectIdentifier, serviceIdentifier, false);
      if (serviceEntity.isPresent()) {
        ServiceEntity finalServiceEntity = serviceEntity.get();
        finalServiceEntity.setVersion(null);
        return finalServiceEntity;
      } else {
        throw new InvalidRequestException("Service with identifier " + serviceIdentifier + " does not exist");
      }
    }

    TagUtils.removeUuidFromTags(stepParameters.getTags());

    return ServiceEntity.builder()
        .identifier(stepParameters.getIdentifier())
        .name(stepParameters.getName())
        .description(ParameterFieldHelper.getParameterFieldValueHandleValueNull(stepParameters.getDescription()))
        .projectIdentifier(projectIdentifier)
        .orgIdentifier(orgIdentifier)
        .accountId(accountId)
        .tags(TagMapper.convertToList(stepParameters.getTags()))
        .build();
  }
}
