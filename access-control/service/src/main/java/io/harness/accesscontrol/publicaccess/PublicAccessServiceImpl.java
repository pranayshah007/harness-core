/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.accesscontrol.publicaccess;
import static io.harness.exception.WingsException.USER;

import io.harness.accesscontrol.principals.PrincipalType;
import io.harness.accesscontrol.resources.resourcegroups.ResourceGroupFactory;
import io.harness.accesscontrol.resources.resourcegroups.ResourceGroupService;
import io.harness.accesscontrol.resources.resourcetypes.ResourceType;
import io.harness.accesscontrol.roleassignments.RoleAssignment;
import io.harness.accesscontrol.roleassignments.RoleAssignmentService;
import io.harness.accesscontrol.roles.persistence.RoleDBO;
import io.harness.accesscontrol.scopes.core.Scope;
import io.harness.accesscontrol.scopes.core.ScopeService;
import io.harness.accesscontrol.scopes.harness.HarnessScopeParams;
import io.harness.accesscontrol.scopes.harness.ScopeMapper;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.ScopeLevel;
import io.harness.exception.InvalidRequestException;
import io.harness.remote.client.NGRestUtils;
import io.harness.resourcegroup.v2.model.ResourceFilter;
import io.harness.resourcegroup.v2.model.ResourceSelector;
import io.harness.resourcegroup.v2.remote.dto.ResourceGroupDTO;
import io.harness.resourcegroup.v2.remote.dto.ResourceGroupRequest;
import io.harness.resourcegroup.v2.remote.dto.ResourceGroupResponse;
import io.harness.resourcegroupclient.remote.ResourceGroupClient;
import io.harness.spec.server.accesscontrol.v1.model.ResourceScope;
import io.harness.utils.CryptoUtils;

import com.google.common.collect.Sets;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.util.*;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

@OwnedBy(HarnessTeam.PL)
@Slf4j
public class PublicAccessServiceImpl implements PublicAccessService {
  private final RoleAssignmentService roleAssignmentService;
  private final ResourceGroupClient resourceGroupClient;
  private final ResourceGroupService resourceGroupService;
  private final ResourceGroupFactory resourceGroupFactory;

  private final MongoTemplate mongoTemplate;

  private final ScopeService scopeService;

  private final PublicAccessUtil publicAccessUtil;

  private static final String PUBLIC_RESOURCE_GROUP_IDENTIFIER = "_public_resources";
  private static final String PUBLIC_RESOURCE_GROUP_NAME = "Public Resource Group";
  List<PublicAccessRoleAssignmentMapping> publicAccessRoleAssignmentMappings;

  @Inject
  public PublicAccessServiceImpl(RoleAssignmentService roleAssignmentService,
      @Named("PRIVILEGED") ResourceGroupClient resourceGroupClient, ResourceGroupService resourceGroupService,
      ResourceGroupFactory resourceGroupFactory, MongoTemplate mongoTemplate, ScopeService scopeService,
      PublicAccessUtil publicAccessUtil) {
    this.roleAssignmentService = roleAssignmentService;
    this.resourceGroupClient = resourceGroupClient;
    this.resourceGroupService = resourceGroupService;
    this.resourceGroupFactory = resourceGroupFactory;
    this.mongoTemplate = mongoTemplate;
    this.scopeService = scopeService;
    this.publicAccessUtil = publicAccessUtil;
    this.publicAccessRoleAssignmentMappings = publicAccessUtil.getPublicAccessRoleAssignmentMapping();
  }

  @Override
  public void enable(String resourceIdentifier, ResourceType resourceType, ResourceScope resourceScope) {
    Optional<ResourceGroupResponse> existingResourceGroupOptional = Optional.ofNullable(NGRestUtils.getResponse(
        resourceGroupClient.getResourceGroup(PUBLIC_RESOURCE_GROUP_IDENTIFIER, resourceScope.getAccountIdentifier(),
            resourceScope.getOrgIdentifier(), resourceScope.getProjectIdentifier())));
    if (existingResourceGroupOptional.isPresent()) {
      log.info("Public resource group already present in given scope");
      updatePublicResourceGroup(existingResourceGroupOptional.get(), resourceIdentifier, resourceType, resourceScope);
    } else {
      createPublicResourceGroup(resourceIdentifier, resourceType, resourceScope);
      createRoleAssignment(resourceType, resourceScope);
    }
  }

  private void createRoleAssignment(ResourceType resourceType, ResourceScope resourceScope) {
    HarnessScopeParams harnessScopeParams = HarnessScopeParams.builder()
                                                .accountIdentifier(resourceScope.getAccountIdentifier())
                                                .orgIdentifier(resourceScope.getOrgIdentifier())
                                                .projectIdentifier(resourceScope.getProjectIdentifier())
                                                .build();
    Scope scope = scopeService.getOrCreate(ScopeMapper.fromParams(harnessScopeParams));
    List<String> publicRoles = getPublicRoles();
    final List<PublicAccessRoleAssignmentMapping> filteredMappings =
        publicAccessRoleAssignmentMappings.stream()
            .filter(x -> resourceType.getIdentifier().equals(x.getResourceType()))
            .collect(Collectors.toList());
    List<String> mappedRoles = filteredMappings.stream()
                                   .map(PublicAccessRoleAssignmentMapping::getRoleIdentifier)
                                   .collect(Collectors.toList());
    if (!publicRoles.containsAll(mappedRoles)) {
      throw new InvalidRequestException("Unable to update public resource group", USER);
    }
    for (PublicAccessRoleAssignmentMapping publicAccessRoleAssignmentMapping : publicAccessRoleAssignmentMappings) {
      RoleAssignment roleAssignment = buildRoleAssignment(scope.getLevel().toString(), scope.toString(),
          publicAccessRoleAssignmentMapping.getRoleIdentifier(),
          publicAccessRoleAssignmentMapping.getPrincipalIdentifier());
      roleAssignmentService.create(roleAssignment);
    }
  }

  private List<String> getPublicRoles() {
    Criteria criteria = Criteria.where(RoleDBO.RoleDBOKeys.isPublic).is(true);
    Query query = new Query(criteria);
    return mongoTemplate.find(query, RoleDBO.class).stream().map(RoleDBO::getIdentifier).collect(Collectors.toList());
  }

  private RoleAssignment buildRoleAssignment(
      String scopeLevel, String scopeIdentifier, String roleIdentifier, String principalIdentifier) {
    return RoleAssignment.builder()
        .identifier("role_assignment_".concat(CryptoUtils.secureRandAlphaNumString(20)))
        .scopeIdentifier(scopeIdentifier)
        .scopeLevel(scopeLevel)
        .managed(true)
        .internal(true)
        .roleIdentifier(roleIdentifier)
        .resourceGroupIdentifier(PUBLIC_RESOURCE_GROUP_IDENTIFIER)
        .principalIdentifier(principalIdentifier)
        .principalType(PrincipalType.USER)
        .build();
  }

  private ResourceGroupResponse updatePublicResourceGroup(ResourceGroupResponse existingResourceGroup,
      String resourceIdentifier, ResourceType resourceType, ResourceScope resourceScope) {
    final ResourceGroupDTO resourceGroup = existingResourceGroup.getResourceGroup();
    ResourceFilter resourceFilter = resourceGroup.getResourceFilter();
    List<ResourceSelector> resourceSelectors = new ArrayList<>();
    if (resourceFilter != null) {
      resourceSelectors = resourceFilter.getResources();
    }
    ResourceGroupDTO updatedResourceGroup =
        ResourceGroupDTO.builder()
            .identifier(PUBLIC_RESOURCE_GROUP_IDENTIFIER)
            .name(PUBLIC_RESOURCE_GROUP_NAME)
            .projectIdentifier(resourceScope.getProjectIdentifier())
            .orgIdentifier(resourceScope.getOrgIdentifier())
            .accountIdentifier(resourceScope.getAccountIdentifier())
            .includedScopes(resourceGroup.getIncludedScopes())
            .allowedScopeLevels(resourceGroup.getAllowedScopeLevels())

            .resourceFilter(
                ResourceFilter.builder()
                    .resources(buildResourceGroupSelector(resourceSelectors, resourceIdentifier, resourceType))
                    .build())
            .build();
    Optional<ResourceGroupResponse> resourceGroupResponse = Optional.ofNullable(NGRestUtils.getResponse(
        resourceGroupClient.updateResourceGroup(PUBLIC_RESOURCE_GROUP_IDENTIFIER, resourceScope.getAccountIdentifier(),
            resourceScope.getOrgIdentifier(), resourceScope.getProjectIdentifier(),
            ResourceGroupRequest.builder().resourceGroup(updatedResourceGroup).build())));
    if (resourceGroupResponse.isEmpty()) {
      throw new InvalidRequestException("Unable to update public resource group", USER);
    }
    return resourceGroupResponse.get();
  }

  private ResourceGroupResponse createPublicResourceGroup(
      String resourceIdentifier, ResourceType resourceType, ResourceScope resourceScope) {
    List<ResourceSelector> resourceSelectors = new ArrayList<>();
    ResourceGroupDTO publicResourceGroup =
        ResourceGroupDTO.builder()
            .identifier(PUBLIC_RESOURCE_GROUP_IDENTIFIER)
            .name(PUBLIC_RESOURCE_GROUP_NAME)
            .projectIdentifier(resourceScope.getProjectIdentifier())
            .orgIdentifier(resourceScope.getOrgIdentifier())
            .accountIdentifier(resourceScope.getAccountIdentifier())
            .allowedScopeLevels(
                Sets.newHashSet(ScopeLevel
                                    .of(resourceScope.getAccountIdentifier(), resourceScope.getOrgIdentifier(),
                                        resourceScope.getProjectIdentifier())
                                    .toString()
                                    .toLowerCase()))
            .resourceFilter(
                ResourceFilter.builder()
                    .resources(buildResourceGroupSelector(resourceSelectors, resourceIdentifier, resourceType))
                    .build())
            .build();

    Optional<ResourceGroupResponse> resourceGroupResponse = Optional.ofNullable(
        NGRestUtils.getResponse(resourceGroupClient.createResourceGroup(resourceScope.getAccountIdentifier(),
            resourceScope.getOrgIdentifier(), resourceScope.getProjectIdentifier(),
            ResourceGroupRequest.builder().resourceGroup(publicResourceGroup).build())));

    if (resourceGroupResponse.isEmpty()) {
      throw new InvalidRequestException("Unable to create public resource group", USER);
    }
    try {
      resourceGroupService.upsert(resourceGroupFactory.buildResourceGroup(resourceGroupResponse.get()));
    } catch (Exception e) {
      throw new InvalidRequestException("Unable to sync public resource group.", USER);
    }
    return resourceGroupResponse.get();
  }

  private List<ResourceSelector> buildResourceGroupSelector(
      List<ResourceSelector> resourceSelectors, String resourceIdentifier, ResourceType resourceType) {
    for (Iterator<ResourceSelector> iterator = resourceSelectors.iterator(); iterator.hasNext();) {
      ResourceSelector resourceSelector = iterator.next();
      if (resourceSelector != null && resourceSelector.getResourceType().equals(resourceType.getIdentifier())) {
        resourceSelector.getIdentifiers().add(resourceIdentifier);
        return resourceSelectors;
      }
    }
    resourceSelectors.add(ResourceSelector.builder()
                              .resourceType(resourceType.getIdentifier())
                              .identifiers(List.of(resourceIdentifier))
                              .build());
    return resourceSelectors;
  }
}
