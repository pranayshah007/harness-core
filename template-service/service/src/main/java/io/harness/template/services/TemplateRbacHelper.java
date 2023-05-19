/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.template.services;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.template.resources.beans.PermissionTypes.TEMPLATE_VIEW_PERMISSION;

import static org.apache.commons.lang3.StringUtils.isBlank;

import io.harness.accesscontrol.acl.api.AccessCheckResponseDTO;
import io.harness.accesscontrol.acl.api.AccessControlDTO;
import io.harness.accesscontrol.acl.api.PermissionCheckDTO;
import io.harness.accesscontrol.acl.api.ResourceScope;
import io.harness.accesscontrol.clients.AccessControlClient;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ng.core.dto.EntityScopeInfo;
import io.harness.template.entity.TemplateEntity;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
@OwnedBy(HarnessTeam.CDC)
public class TemplateRbacHelper {
  @Inject private AccessControlClient accessControlClient;
  final String TEMPLATE = "TEMPLATE";

  public List<TemplateEntity> getPermittedTemplateList(List<TemplateEntity> templateEntities) {
    if (isEmpty(templateEntities)) {
      return Collections.emptyList();
    }

    Map<EntityScopeInfo, TemplateEntity> templateMap = templateEntities.stream().collect(
        Collectors.toMap(TemplateRbacHelper::getEntityScopeInfoFromTemplate, Function.identity()));

    List<PermissionCheckDTO> permissionChecks =
        templateEntities.stream()
            .map(templateEntity
                -> PermissionCheckDTO.builder()
                       .permission(TEMPLATE_VIEW_PERMISSION)
                       .resourceIdentifier(templateEntity.getIdentifier())
                       .resourceScope(ResourceScope.of(templateEntity.getAccountId(), templateEntity.getOrgIdentifier(),
                           templateEntity.getProjectIdentifier()))
                       .resourceType(TEMPLATE)
                       .build())
            .collect(Collectors.toList());

    AccessCheckResponseDTO accessCheckResponse = accessControlClient.checkForAccessOrThrow(permissionChecks);

    List<TemplateEntity> permittedTemplates = new ArrayList<>();

    for (AccessControlDTO accessControlDTO : accessCheckResponse.getAccessControlList()) {
      if (accessControlDTO.isPermitted()) {
        TemplateEntity templateEntity =
            templateMap.get(TemplateRbacHelper.getEntityScopeInfoFromAccessControlDTO(accessControlDTO));

        if (templateEntity != null) {
          permittedTemplates.add(templateEntity);
        }
      }
    }

    return permittedTemplates;
  }

  private static EntityScopeInfo getEntityScopeInfoFromTemplate(TemplateEntity templateEntity) {
    return EntityScopeInfo.builder()
        .accountIdentifier(templateEntity.getAccountId())
        .orgIdentifier(isBlank(templateEntity.getOrgIdentifier()) ? null : templateEntity.getOrgIdentifier())
        .projectIdentifier(
            isBlank(templateEntity.getProjectIdentifier()) ? null : templateEntity.getProjectIdentifier())
        .identifier(templateEntity.getIdentifier())
        .build();
  }

  private static EntityScopeInfo getEntityScopeInfoFromAccessControlDTO(AccessControlDTO accessControlDTO) {
    return EntityScopeInfo.builder()
        .accountIdentifier(accessControlDTO.getResourceScope().getAccountIdentifier())
        .orgIdentifier(isBlank(accessControlDTO.getResourceScope().getOrgIdentifier())
                ? null
                : accessControlDTO.getResourceScope().getOrgIdentifier())
        .projectIdentifier(isBlank(accessControlDTO.getResourceScope().getProjectIdentifier())
                ? null
                : accessControlDTO.getResourceScope().getProjectIdentifier())
        .identifier(accessControlDTO.getResourceIdentifier())
        .build();
  }
}
