/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.resourcegroup.framework.v2.remote.resource;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.exception.WingsException.USER;
import static io.harness.resourcegroup.ResourceGroupPermissions.DELETE_RESOURCEGROUP_PERMISSION;
import static io.harness.resourcegroup.ResourceGroupPermissions.EDIT_RESOURCEGROUP_PERMISSION;
import static io.harness.resourcegroup.ResourceGroupPermissions.VIEW_RESOURCEGROUP_PERMISSION;
import static io.harness.resourcegroup.ResourceGroupResourceTypes.RESOURCE_GROUP;
import static io.harness.resourcegroup.beans.ScopeFilterType.EXCLUDING_CHILD_SCOPES;
import static io.harness.resourcegroup.v1.remote.dto.ManagedFilter.NO_FILTER;
import static io.harness.utils.PageUtils.getNGPageResponse;

import io.harness.accesscontrol.AccountIdentifier;
import io.harness.accesscontrol.NGAccessControlCheck;
import io.harness.accesscontrol.OrgIdentifier;
import io.harness.accesscontrol.ProjectIdentifier;
import io.harness.accesscontrol.ResourceIdentifier;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.Scope;
import io.harness.beans.ScopeLevel;
import io.harness.enforcement.client.annotation.FeatureRestrictionCheck;
import io.harness.enforcement.constants.FeatureRestrictionName;
import io.harness.eraro.ErrorCode;
import io.harness.eraro.Level;
import io.harness.exception.NoResultFoundException;
import io.harness.ng.beans.PageRequest;
import io.harness.ng.beans.PageResponse;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.resourcegroup.framework.v2.service.ResourceGroupService;
import io.harness.resourcegroup.framework.v2.service.impl.ResourceGroupValidatorImpl;
import io.harness.resourcegroup.v1.remote.dto.ManagedFilter;
import io.harness.resourcegroup.v1.remote.dto.ResourceGroupFilterDTO;
import io.harness.resourcegroup.v2.model.ScopeSelector;
import io.harness.resourcegroup.v2.remote.dto.ResourceGroupRequest;
import io.harness.resourcegroup.v2.remote.dto.ResourceGroupResponse;
import io.harness.resourcegroup.v2.remote.resource.HarnessResourceGroupResource;
import io.harness.security.annotations.InternalApi;
import io.harness.security.annotations.NextGenManagerAuth;

import com.google.common.collect.Sets;
import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;

@AllArgsConstructor(access = AccessLevel.PUBLIC, onConstructor = @__({ @Inject }))
@NextGenManagerAuth
@OwnedBy(HarnessTeam.PL)
public class HarnessResourceGroupResourceImpl implements HarnessResourceGroupResource {
  ResourceGroupService resourceGroupService;
  ResourceGroupValidatorImpl resourceGroupValidator;

  @NGAccessControlCheck(resourceType = RESOURCE_GROUP, permission = VIEW_RESOURCEGROUP_PERMISSION)
  public ResponseDTO<ResourceGroupResponse> get(@ResourceIdentifier String identifier,
      @AccountIdentifier String accountIdentifier, @OrgIdentifier String orgIdentifier,
      @ProjectIdentifier String projectIdentifier) {
    Optional<ResourceGroupResponse> optionalResourceGroupResponse =
        resourceGroupService.get(Scope.of(accountIdentifier, orgIdentifier, projectIdentifier), identifier, NO_FILTER);

    if (optionalResourceGroupResponse.isEmpty()) {
      String message = String.format("Resource group with identifier [%s] not found.", identifier);
      throw NoResultFoundException.newBuilder()
          .code(ErrorCode.RESOURCE_NOT_FOUND)
          .message(message)
          .level(Level.ERROR)
          .reportTargets(USER)
          .build();
    }

    return ResponseDTO.newResponse(optionalResourceGroupResponse.get());
  }

  @InternalApi
  @NGAccessControlCheck(resourceType = RESOURCE_GROUP, permission = VIEW_RESOURCEGROUP_PERMISSION)
  public ResponseDTO<ResourceGroupResponse> getInternal(@ResourceIdentifier String identifier,
      @AccountIdentifier String accountIdentifier, @OrgIdentifier String orgIdentifier,
      @ProjectIdentifier String projectIdentifier) {
    Optional<ResourceGroupResponse> resourceGroupResponseOpt =
        Optional.ofNullable(resourceGroupService
                                .get(Scope.of(accountIdentifier, orgIdentifier, projectIdentifier), identifier,
                                    isEmpty(accountIdentifier) ? ManagedFilter.ONLY_MANAGED : ManagedFilter.NO_FILTER)
                                .orElse(null));
    return ResponseDTO.newResponse(resourceGroupResponseOpt.orElse(null));
  }

  public ResponseDTO<PageResponse<ResourceGroupResponse>> list(@AccountIdentifier String accountIdentifier,
      @OrgIdentifier String orgIdentifier, @ProjectIdentifier String projectIdentifier, String searchTerm,
      PageRequest pageRequest) {
    return ResponseDTO.newResponse(getNGPageResponse(resourceGroupService.list(
        Scope.of(accountIdentifier, orgIdentifier, projectIdentifier), pageRequest, searchTerm)));
  }

  public ResponseDTO<PageResponse<ResourceGroupResponse>> list(
      ResourceGroupFilterDTO resourceGroupFilterDTO, String accountIdentifier, PageRequest pageRequest) {
    return ResponseDTO.newResponse(getNGPageResponse(resourceGroupService.list(resourceGroupFilterDTO, pageRequest)));
  }

  @NGAccessControlCheck(resourceType = RESOURCE_GROUP, permission = EDIT_RESOURCEGROUP_PERMISSION)
  @FeatureRestrictionCheck(FeatureRestrictionName.CUSTOM_RESOURCE_GROUPS)
  public ResponseDTO<ResourceGroupResponse> create(@AccountIdentifier String accountIdentifier,
      @OrgIdentifier String orgIdentifier, @ProjectIdentifier String projectIdentifier,
      ResourceGroupRequest resourceGroupRequest) {
    resourceGroupRequest.getResourceGroup().setAllowedScopeLevels(
        Sets.newHashSet(ScopeLevel.of(accountIdentifier, orgIdentifier, projectIdentifier).toString().toLowerCase()));
    if (isEmpty(resourceGroupRequest.getResourceGroup().getIncludedScopes())) {
      List<ScopeSelector> includedScopes = new ArrayList<>();
      ScopeSelector scopeSelector = ScopeSelector.builder()
                                        .accountIdentifier(accountIdentifier)
                                        .orgIdentifier(orgIdentifier)
                                        .projectIdentifier(projectIdentifier)
                                        .filter(EXCLUDING_CHILD_SCOPES)
                                        .build();
      includedScopes.add(scopeSelector);
      resourceGroupRequest.getResourceGroup().setIncludedScopes(includedScopes);
    }

    resourceGroupValidator.validateResourceGroup(resourceGroupRequest);
    ResourceGroupResponse resourceGroupResponse =
        resourceGroupService.create(resourceGroupRequest.getResourceGroup(), false);
    return ResponseDTO.newResponse(resourceGroupResponse);
  }

  @NGAccessControlCheck(resourceType = RESOURCE_GROUP, permission = EDIT_RESOURCEGROUP_PERMISSION)
  public ResponseDTO<ResourceGroupResponse> update(@ResourceIdentifier String identifier,
      @AccountIdentifier String accountIdentifier, @OrgIdentifier String orgIdentifier,
      @ProjectIdentifier String projectIdentifier, ResourceGroupRequest resourceGroupRequest) {
    resourceGroupRequest.getResourceGroup().setAllowedScopeLevels(
        Sets.newHashSet(ScopeLevel.of(accountIdentifier, orgIdentifier, projectIdentifier).toString().toLowerCase()));
    resourceGroupValidator.validateResourceGroup(resourceGroupRequest);
    return ResponseDTO.newResponse(
        (resourceGroupService.update(resourceGroupRequest.getResourceGroup(), false)).orElse(null));
  }

  @NGAccessControlCheck(resourceType = RESOURCE_GROUP, permission = DELETE_RESOURCEGROUP_PERMISSION)
  public ResponseDTO<Boolean> delete(@ResourceIdentifier String identifier, @AccountIdentifier String accountIdentifier,
      @OrgIdentifier String orgIdentifier, @ProjectIdentifier String projectIdentifier) {
    return ResponseDTO.newResponse(
        resourceGroupService.delete(Scope.of(accountIdentifier, orgIdentifier, projectIdentifier), identifier));
  }
}
