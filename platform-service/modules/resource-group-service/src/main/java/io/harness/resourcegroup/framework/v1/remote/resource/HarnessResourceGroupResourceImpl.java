/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.resourcegroup.framework.v1.remote.resource;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.resourcegroup.ResourceGroupPermissions.DELETE_RESOURCEGROUP_PERMISSION;
import static io.harness.resourcegroup.ResourceGroupPermissions.EDIT_RESOURCEGROUP_PERMISSION;
import static io.harness.resourcegroup.ResourceGroupPermissions.VIEW_RESOURCEGROUP_PERMISSION;
import static io.harness.resourcegroup.ResourceGroupResourceTypes.RESOURCE_GROUP;
import static io.harness.utils.PageUtils.getNGPageResponse;

import io.harness.accesscontrol.AccountIdentifier;
import io.harness.accesscontrol.acl.api.Resource;
import io.harness.accesscontrol.acl.api.ResourceScope;
import io.harness.accesscontrol.clients.AccessControlClient;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.Scope;
import io.harness.beans.ScopeLevel;
import io.harness.enforcement.client.annotation.FeatureRestrictionCheck;
import io.harness.enforcement.constants.FeatureRestrictionName;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.beans.PageRequest;
import io.harness.ng.beans.PageResponse;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.resourcegroup.framework.v2.remote.mapper.ResourceGroupMapper;
import io.harness.resourcegroup.framework.v2.service.ResourceGroupService;
import io.harness.resourcegroup.model.DynamicResourceSelector;
import io.harness.resourcegroup.model.ResourceSelectorByScope;
import io.harness.resourcegroup.model.StaticResourceSelector;
import io.harness.resourcegroup.v1.remote.dto.ManagedFilter;
import io.harness.resourcegroup.v1.remote.dto.ResourceGroupDTO;
import io.harness.resourcegroup.v1.remote.dto.ResourceGroupFilterDTO;
import io.harness.resourcegroup.v1.remote.dto.ResourceGroupRequest;
import io.harness.resourcegroup.v1.remote.dto.ResourceGroupResponse;
import io.harness.resourcegroup.v1.remote.resource.HarnessResourceGroupResource;
import io.harness.security.annotations.InternalApi;
import io.harness.security.annotations.NextGenManagerAuth;

import com.google.common.collect.Sets;
import com.google.inject.Inject;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;

@AllArgsConstructor(access = AccessLevel.PUBLIC, onConstructor = @__({ @Inject }))
@NextGenManagerAuth
@OwnedBy(HarnessTeam.PL)
public class HarnessResourceGroupResourceImpl implements HarnessResourceGroupResource {
  ResourceGroupService resourceGroupService;
  AccessControlClient accessControlClient;

  public ResponseDTO<ResourceGroupResponse> get(
      String identifier, String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountIdentifier, orgIdentifier, projectIdentifier),
        Resource.of(RESOURCE_GROUP, identifier), VIEW_RESOURCEGROUP_PERMISSION);
    Optional<ResourceGroupResponse> resourceGroupResponseOpt = Optional.ofNullable(ResourceGroupMapper.toV1Response(
        resourceGroupService
            .get(Scope.of(accountIdentifier, orgIdentifier, projectIdentifier), identifier, ManagedFilter.NO_FILTER)
            .orElse(null)));
    return ResponseDTO.newResponse(resourceGroupResponseOpt.orElse(null));
  }

  @InternalApi
  public ResponseDTO<ResourceGroupResponse> getInternal(
      String identifier, String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    Optional<ResourceGroupResponse> resourceGroupResponseOpt = Optional.ofNullable(ResourceGroupMapper.toV1Response(
        resourceGroupService
            .get(Scope.of(accountIdentifier, orgIdentifier, projectIdentifier), identifier,
                isEmpty(accountIdentifier) ? ManagedFilter.ONLY_MANAGED : ManagedFilter.NO_FILTER)
            .orElse(null)));
    return ResponseDTO.newResponse(resourceGroupResponseOpt.orElse(null));
  }

  public ResponseDTO<PageResponse<ResourceGroupResponse>> list(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, String searchTerm, PageRequest pageRequest) {
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountIdentifier, orgIdentifier, projectIdentifier),
        Resource.of(RESOURCE_GROUP, null), VIEW_RESOURCEGROUP_PERMISSION);
    return ResponseDTO.newResponse(getNGPageResponse(
        resourceGroupService
            .list(Scope.of(accountIdentifier, orgIdentifier, projectIdentifier), pageRequest, searchTerm)
            .map(ResourceGroupMapper::toV1Response)));
  }

  public ResponseDTO<PageResponse<ResourceGroupResponse>> list(
      ResourceGroupFilterDTO resourceGroupFilterDTO, String accountIdentifier, PageRequest pageRequest) {
    accessControlClient.checkForAccessOrThrow(
        ResourceScope.of(resourceGroupFilterDTO.getAccountIdentifier(), resourceGroupFilterDTO.getOrgIdentifier(),
            resourceGroupFilterDTO.getProjectIdentifier()),
        Resource.of(RESOURCE_GROUP, null), VIEW_RESOURCEGROUP_PERMISSION);
    return ResponseDTO.newResponse(getNGPageResponse(
        resourceGroupService.list(resourceGroupFilterDTO, pageRequest).map(ResourceGroupMapper::toV1Response)));
  }

  @FeatureRestrictionCheck(FeatureRestrictionName.CUSTOM_RESOURCE_GROUPS)
  public ResponseDTO<ResourceGroupResponse> create(@AccountIdentifier String accountIdentifier, String orgIdentifier,
      String projectIdentifier, ResourceGroupRequest resourceGroupRequest) {
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountIdentifier, orgIdentifier, projectIdentifier),
        Resource.of(RESOURCE_GROUP, null), EDIT_RESOURCEGROUP_PERMISSION);
    resourceGroupRequest.getResourceGroup().setAllowedScopeLevels(
        Sets.newHashSet(ScopeLevel.of(accountIdentifier, orgIdentifier, projectIdentifier).toString().toLowerCase()));
    verifySupportedResourceSelectorsAreUsed(resourceGroupRequest);
    validateResourceSelectors(resourceGroupRequest);
    ResourceGroupResponse resourceGroupResponse = ResourceGroupMapper.toV1Response(resourceGroupService.create(
        ResourceGroupMapper.fromV1DTO(resourceGroupRequest.getResourceGroup(), false), false));
    return ResponseDTO.newResponse(resourceGroupResponse);
  }

  public ResponseDTO<ResourceGroupResponse> update(String identifier, String accountIdentifier, String orgIdentifier,
      String projectIdentifier, ResourceGroupRequest resourceGroupRequest) {
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountIdentifier, orgIdentifier, projectIdentifier),
        Resource.of(RESOURCE_GROUP, identifier), EDIT_RESOURCEGROUP_PERMISSION);
    resourceGroupRequest.getResourceGroup().setAllowedScopeLevels(
        Sets.newHashSet(ScopeLevel.of(accountIdentifier, orgIdentifier, projectIdentifier).toString().toLowerCase()));
    verifySupportedResourceSelectorsAreUsed(resourceGroupRequest);
    validateResourceSelectors(resourceGroupRequest);
    Optional<ResourceGroupResponse> resourceGroupResponseOpt = Optional.ofNullable(ResourceGroupMapper.toV1Response(
        resourceGroupService
            .update(ResourceGroupMapper.fromV1DTO(resourceGroupRequest.getResourceGroup(), false), false)
            .orElse(null)));
    return ResponseDTO.newResponse(resourceGroupResponseOpt.orElse(null));
  }

  public ResponseDTO<Boolean> delete(
      String identifier, String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountIdentifier, orgIdentifier, projectIdentifier),
        Resource.of(RESOURCE_GROUP, identifier), DELETE_RESOURCEGROUP_PERMISSION);
    return ResponseDTO.newResponse(
        resourceGroupService.delete(Scope.of(accountIdentifier, orgIdentifier, projectIdentifier), identifier));
  }

  private void verifySupportedResourceSelectorsAreUsed(ResourceGroupRequest resourceGroupRequest) {
    if (resourceGroupRequest == null || resourceGroupRequest.getResourceGroup() == null) {
      return;
    }
    ResourceGroupDTO resourceGroupDTO = resourceGroupRequest.getResourceGroup();
    if (resourceGroupDTO.isFullScopeSelected()) {
      throw new InvalidRequestException("Full scope selected cannot be provided for custom resource groups.");
    }
    if (isNotEmpty(resourceGroupDTO.getResourceSelectors())) {
      resourceGroupDTO.getResourceSelectors().forEach(resourceSelector -> {
        if (resourceSelector instanceof ResourceSelectorByScope) {
          if (!resourceGroupDTO.getScope().equals(((ResourceSelectorByScope) resourceSelector).getScope())) {
            throw new InvalidRequestException(
                "Resource Selector by scope with different scope cannot be provided for custom resource groups.");
          }
        }
      });
    }
  }

  private void validateResourceSelectors(ResourceGroupRequest resourceGroupRequest) {
    if (resourceGroupRequest == null || resourceGroupRequest.getResourceGroup() == null) {
      return;
    }
    ResourceGroupDTO resourceGroupDTO = resourceGroupRequest.getResourceGroup();
    if (isNotEmpty(resourceGroupDTO.getResourceSelectors())) {
      AtomicBoolean selectorByScopePresent = new AtomicBoolean(false);
      AtomicBoolean dynamicSelectorIncludingChildScopesPresent = new AtomicBoolean(false);
      AtomicBoolean dynamicSelectorNotIncludingChildScopesPresent = new AtomicBoolean(false);
      AtomicBoolean staticSelectorPresent = new AtomicBoolean(false);

      resourceGroupDTO.getResourceSelectors().forEach(resourceSelector -> {
        if (resourceSelector instanceof ResourceSelectorByScope) {
          selectorByScopePresent.set(true);
        } else if (resourceSelector instanceof StaticResourceSelector) {
          staticSelectorPresent.set(true);
        } else if (resourceSelector instanceof DynamicResourceSelector) {
          if (Boolean.TRUE.equals(((DynamicResourceSelector) resourceSelector).getIncludeChildScopes())) {
            dynamicSelectorIncludingChildScopesPresent.set(true);
          } else {
            dynamicSelectorNotIncludingChildScopesPresent.set(false);
          }
        }
      });

      if (selectorByScopePresent.get()
          && (dynamicSelectorIncludingChildScopesPresent.get() || dynamicSelectorNotIncludingChildScopesPresent.get()
              || staticSelectorPresent.get())) {
        throw new InvalidRequestException(
            "Resource Selector by scope cannot be selected along with other resource selectors.");
      }

      if (dynamicSelectorIncludingChildScopesPresent.get() && dynamicSelectorNotIncludingChildScopesPresent.get()) {
        throw new InvalidRequestException(
            "Either the current scope or the current scope including the child scopes should be selected, and not both.");
      }
    }
  }
}
