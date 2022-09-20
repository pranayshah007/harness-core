/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.pms.rbac;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.accesscontrol.acl.api.AccessCheckResponseDTO;
import io.harness.accesscontrol.acl.api.AccessControlDTO;
import io.harness.accesscontrol.acl.api.PermissionCheckDTO;
import io.harness.accesscontrol.acl.api.Principal;
import io.harness.accesscontrol.acl.api.ResourceScope;
import io.harness.accesscontrol.clients.AccessControlClient;
import io.harness.accesscontrol.principals.PrincipalType;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.IdentifierRef;
import io.harness.data.structure.EmptyPredicate;
import io.harness.eraro.ErrorCode;
import io.harness.eventsframework.schemas.entity.EntityDetailProtoDTO;
import io.harness.exception.AccessDeniedException;
import io.harness.exception.WingsException;
import io.harness.ng.core.EntityDetail;
import io.harness.ng.core.entitydetail.EntityDetailProtoToRestMapper;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.plan.ExecutionPrincipalInfo;
import io.harness.pms.execution.utils.AmbianceUtils;

import com.google.common.collect.Lists;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

/**
 * Helper class to perform validation checks on EntityDetail object. It constructs the access permission on its
 * own for the given referredEntity
 */
@OwnedBy(HarnessTeam.PIPELINE)
@Singleton
@Slf4j
public class PipelineRbacHelper {
  @Inject EntityDetailProtoToRestMapper entityDetailProtoToRestMapper;
  @Inject @Named("PRIVILEGED") AccessControlClient accessControlClient;
  @Inject InternalReferredEntityExtractor internalReferredEntityExtractor;
  private final Duration RETRY_SLEEP_DURATION = Duration.ofSeconds(2);
  private final int MAX_ATTEMPTS = 3;

  public void checkRuntimePermissions(Ambiance ambiance, Set<EntityDetailProtoDTO> entityDetailsProto) {
    List<EntityDetail> entityDetails =
        entityDetailProtoToRestMapper.createEntityDetailsDTO(new ArrayList<>(entityDetailsProto));
    checkRuntimePermissions(ambiance, entityDetails, false);
  }

  public void checkRuntimePermissions(
      Ambiance ambiance, List<EntityDetail> entityDetails, boolean shouldExtractInternalEntities) {
    if (isEmpty(entityDetails)) {
      return;
    }
    ExecutionPrincipalInfo executionPrincipalInfo = ambiance.getMetadata().getPrincipalInfo();

    // NOTE: rbac should not be validated for triggers so this field is set to false for trigger based execution.
    if (!executionPrincipalInfo.getShouldValidateRbac()) {
      return;
    }
    String principal = executionPrincipalInfo.getPrincipal();
    if (EmptyPredicate.isEmpty(principal)) {
      throw new AccessDeniedException("Execution with empty principal found. Please contact harness customer care.",
          ErrorCode.NG_ACCESS_DENIED, WingsException.USER);
    }

    String accountId = AmbianceUtils.getAccountId(ambiance);
    if (shouldExtractInternalEntities) {
      entityDetails.addAll(internalReferredEntityExtractor.extractInternalEntities(accountId, entityDetails));
    }

    PrincipalType principalType = PrincipalTypeProtoToPrincipalTypeMapper.convertToAccessControlPrincipalType(
        executionPrincipalInfo.getPrincipalType());
    List<PermissionCheckDTO> permissionCheckDTOS =
        entityDetails.stream().map(this::convertToPermissionCheckDTO).collect(Collectors.toList());

    if (isNotEmpty(permissionCheckDTOS)) {
      AccessCheckResponseDTO accessCheckResponseDTO = accessControlClient.checkForAccess(
          Principal.builder().principalIdentifier(principal).principalType(principalType).build(), permissionCheckDTOS);

      if (accessCheckResponseDTO == null) {
        return;
      }

      List<AccessControlDTO> nonPermittedResources = accessCheckResponseDTO.getAccessControlList()
                                                         .stream()
                                                         .filter(accessControlDTO -> !accessControlDTO.isPermitted())
                                                         .collect(Collectors.toList());
      if (!nonPermittedResources.isEmpty()) {
        throwAccessDeniedError(nonPermittedResources);
      }
    }
  }

  public static void throwAccessDeniedError(List<AccessControlDTO> nonPermittedResources) {
    /*
      allErrors has resource type as key. For each resource type, the value is a map with keys as resource identifiers.
      For each identifier, the value is a list of permissions
       */
    Map<String, Map<String, List<String>>> allErrors = new HashMap<>();
    for (AccessControlDTO accessControlDTO : nonPermittedResources) {
      if (allErrors.containsKey(accessControlDTO.getResourceType())) {
        Map<String, List<String>> resourceToPermissions = allErrors.get(accessControlDTO.getResourceType());
        if (resourceToPermissions.containsKey(accessControlDTO.getResourceIdentifier())) {
          List<String> permissions = resourceToPermissions.get(accessControlDTO.getResourceIdentifier());
          permissions.add(accessControlDTO.getPermission());
        } else {
          resourceToPermissions.put(
              accessControlDTO.getResourceIdentifier(), Lists.newArrayList(accessControlDTO.getPermission()));
        }
      } else {
        Map<String, List<String>> resourceToPermissions = new HashMap<>();
        List<String> permissions = new ArrayList<>();
        permissions.add(accessControlDTO.getPermission());
        resourceToPermissions.put(accessControlDTO.getResourceIdentifier(), permissions);
        allErrors.put(accessControlDTO.getResourceType(), resourceToPermissions);
      }
    }

    StringBuilder errors = new StringBuilder();
    for (String resourceType : allErrors.keySet()) {
      for (String resourceIdentifier : allErrors.get(resourceType).keySet()) {
        if (EmptyPredicate.isEmpty(resourceIdentifier)) {
          errors.append(String.format("For %s, these permissions are not there: %s.\n", resourceType,
              allErrors.get(resourceType).get(resourceIdentifier).toString()));
        } else {
          errors.append(String.format("For %s with identifier %s, these permissions are not there: %s.\n", resourceType,
              resourceIdentifier, allErrors.get(resourceType).get(resourceIdentifier).toString()));
        }
      }
    }

    throw new AccessDeniedException(errors.toString(), ErrorCode.NG_ACCESS_DENIED, WingsException.USER);
  }

  public PermissionCheckDTO convertToPermissionCheckDTO(EntityDetail entityDetail) {
    IdentifierRef identifierRef = (IdentifierRef) entityDetail.getEntityRef();
    if (identifierRef.getMetadata() != null
        && identifierRef.getMetadata().getOrDefault("new", "false").equals("true")) {
      return PermissionCheckDTO.builder()
          .permission(PipelineReferredEntityPermissionHelper.getPermissionForGivenType(entityDetail.getType(), true))
          .resourceIdentifier(null)
          .resourceScope(ResourceScope.builder()
                             .accountIdentifier(identifierRef.getAccountIdentifier())
                             .orgIdentifier(identifierRef.getOrgIdentifier())
                             .projectIdentifier(identifierRef.getProjectIdentifier())
                             .build())
          .resourceType(PipelineReferredEntityPermissionHelper.getEntityName(entityDetail.getType()))
          .build();
    }
    return PermissionCheckDTO.builder()
        .permission(PipelineReferredEntityPermissionHelper.getPermissionForGivenType(entityDetail.getType(), false))
        .resourceIdentifier(identifierRef.getIdentifier())
        .resourceScope(ResourceScope.builder()
                           .accountIdentifier(identifierRef.getAccountIdentifier())
                           .orgIdentifier(identifierRef.getOrgIdentifier())
                           .projectIdentifier(identifierRef.getProjectIdentifier())
                           .build())
        .resourceType(PipelineReferredEntityPermissionHelper.getEntityName(entityDetail.getType()))
        .build();
  }
}
