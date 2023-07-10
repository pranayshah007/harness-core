/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.resourcegroup.framework.v2.service.impl;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.exception.WingsException.USER_SRE;
import static io.harness.outbox.TransactionOutboxModule.OUTBOX_TRANSACTION_TEMPLATE;
import static io.harness.resourcegroup.ResourceGroupPermissions.VIEW_RESOURCEGROUP_PERMISSION;
import static io.harness.resourcegroup.ResourceGroupResourceTypes.RESOURCE_GROUP;
import static io.harness.springdata.PersistenceUtils.DEFAULT_RETRY_POLICY;
import static io.harness.utils.PageUtils.getPageRequest;

import static java.lang.Boolean.TRUE;
import static org.apache.commons.lang3.StringUtils.isBlank;

import io.harness.accesscontrol.acl.api.AccessCheckResponseDTO;
import io.harness.accesscontrol.acl.api.AccessControlDTO;
import io.harness.accesscontrol.acl.api.PermissionCheckDTO;
import io.harness.accesscontrol.acl.api.Resource;
import io.harness.accesscontrol.acl.api.ResourceScope;
import io.harness.accesscontrol.clients.AccessControlClient;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.Scope;
import io.harness.beans.ScopeLevel;
import io.harness.beans.SortOrder;
import io.harness.exception.DuplicateFieldException;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.beans.PageRequest;
import io.harness.ng.core.common.beans.NGTag.NGTagKeys;
import io.harness.ng.core.dto.EntityScopeInfo;
import io.harness.outbox.api.OutboxService;
import io.harness.resourcegroup.framework.v1.events.ResourceGroupCreateEvent;
import io.harness.resourcegroup.framework.v1.events.ResourceGroupDeleteEvent;
import io.harness.resourcegroup.framework.v1.events.ResourceGroupUpdateEvent;
import io.harness.resourcegroup.framework.v2.remote.mapper.ResourceGroupMapper;
import io.harness.resourcegroup.framework.v2.repositories.spring.ResourceGroupV2Repository;
import io.harness.resourcegroup.framework.v2.service.ResourceGroupService;
import io.harness.resourcegroup.v1.remote.dto.ManagedFilter;
import io.harness.resourcegroup.v1.remote.dto.ResourceGroupFilterDTO;
import io.harness.resourcegroup.v2.model.ResourceFilter.ResourceFilterKeys;
import io.harness.resourcegroup.v2.model.ResourceGroup;
import io.harness.resourcegroup.v2.model.ResourceGroup.ResourceGroupKeys;
import io.harness.resourcegroup.v2.model.ResourceSelector.ResourceSelectorKeys;
import io.harness.resourcegroup.v2.remote.dto.ResourceGroupDTO;
import io.harness.resourcegroup.v2.remote.dto.ResourceGroupResponse;
import io.harness.utils.PageUtils;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import io.serializer.HObjectMapper;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.validation.executable.ValidateOnExecution;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import net.jodah.failsafe.Failsafe;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.transaction.support.TransactionTemplate;

@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
@OwnedBy(PL)
@ValidateOnExecution
public class ResourceGroupServiceImpl implements ResourceGroupService {
  ResourceGroupV2Repository resourceGroupV2Repository;
  ResourceGroupValidatorImpl resourceGroupValidatorImpl;
  OutboxService outboxService;
  TransactionTemplate transactionTemplate;
  AccessControlClient accessControlClient;

  @Inject
  public ResourceGroupServiceImpl(ResourceGroupV2Repository resourceGroupV2Repository,
      ResourceGroupValidatorImpl resourceGroupValidatorImpl, OutboxService outboxService,
      @Named(OUTBOX_TRANSACTION_TEMPLATE) TransactionTemplate transactionTemplate,
      AccessControlClient accessControlClient) {
    this.resourceGroupV2Repository = resourceGroupV2Repository;
    this.resourceGroupValidatorImpl = resourceGroupValidatorImpl;
    this.outboxService = outboxService;
    this.transactionTemplate = transactionTemplate;
    this.accessControlClient = accessControlClient;
  }

  private ResourceGroup createInternal(ResourceGroup resourceGroup, boolean pushEvent) {
    return Failsafe.with(DEFAULT_RETRY_POLICY).get(() -> transactionTemplate.execute(status -> {
      ResourceGroup savedResourceGroup = resourceGroupV2Repository.save(resourceGroup);
      if (pushEvent) {
        outboxService.save(new ResourceGroupCreateEvent(
            savedResourceGroup.getAccountIdentifier(), null, ResourceGroupMapper.toDTO(savedResourceGroup)));
      }
      return savedResourceGroup;
    }));
  }

  private ResourceGroup create(ResourceGroup resourceGroup, boolean pushEvent) {
    validateHarnessManagedRGDoesNotExistsWithGivenId(resourceGroup.getIdentifier());
    try {
      return createInternal(resourceGroup, pushEvent);
    } catch (DuplicateKeyException ex) {
      throw new DuplicateFieldException(
          String.format("A resource group with identifier %s already exists at the specified scope",
              resourceGroup.getIdentifier()),
          USER_SRE, ex);
    }
  }

  private void validateHarnessManagedRGDoesNotExistsWithGivenId(String identifier) {
    Criteria criteria = new Criteria();
    criteria.and(ResourceGroupKeys.identifier).is(identifier);
    criteria.and(ResourceGroupKeys.harnessManaged).is(true);
    Optional<ResourceGroup> resourceGroupOptional = resourceGroupV2Repository.find(criteria);
    if (resourceGroupOptional.isPresent()) {
      throw new InvalidRequestException(
          String.format("Another Harness managed resource group already exists with identifier %s", identifier));
    }
  }

  @Override
  public ResourceGroupResponse create(ResourceGroupDTO resourceGroupDTO, boolean harnessManaged) {
    ResourceGroup resourceGroup = ResourceGroupMapper.fromDTO(resourceGroupDTO);
    resourceGroup.setHarnessManaged(harnessManaged);

    return ResourceGroupMapper.toResponseWrapper(create(resourceGroup, true));
  }

  @Override
  public Optional<ResourceGroupResponse> upsert(ResourceGroup resourceGroup, boolean isInternal) {
    Optional<ResourceGroup> resourceGroupOpt =
        getResourceGroup(Scope.of(resourceGroup.getAccountIdentifier(), resourceGroup.getOrgIdentifier(),
                             resourceGroup.getProjectIdentifier()),
            resourceGroup.getIdentifier(),
            Boolean.TRUE.equals(resourceGroup.getHarnessManaged()) ? ManagedFilter.ONLY_MANAGED
                                                                   : ManagedFilter.ONLY_CUSTOM);
    if (!resourceGroupOpt.isPresent()) {
      return Optional.ofNullable(ResourceGroupMapper.toResponseWrapper(
          isInternal ? create(resourceGroup, false) : create(resourceGroup, true)));
    } else {
      return updateV2(ResourceGroupMapper.toDTO(resourceGroup), resourceGroup.getHarnessManaged(), isInternal);
    }
  }

  @VisibleForTesting
  protected Criteria getResourceGroupFilterCriteria(ResourceGroupFilterDTO resourceGroupFilterDTO) {
    Criteria criteria = new Criteria();
    if (isNotEmpty(resourceGroupFilterDTO.getIdentifierFilter())) {
      criteria.and(ResourceGroupKeys.identifier).in(resourceGroupFilterDTO.getIdentifierFilter());
    }
    Criteria scopeCriteria = getBaseScopeCriteria(resourceGroupFilterDTO.getAccountIdentifier(),
        resourceGroupFilterDTO.getOrgIdentifier(), resourceGroupFilterDTO.getProjectIdentifier())
                                 .and(ResourceGroupKeys.harnessManaged)
                                 .ne(true);
    Criteria managedCriteria = getBaseScopeCriteria(null, null, null).and(ResourceGroupKeys.harnessManaged).is(true);

    if (isNotEmpty(resourceGroupFilterDTO.getAccountIdentifier())) {
      managedCriteria.and(ResourceGroupKeys.allowedScopeLevels)
          .is(ScopeLevel
                  .of(resourceGroupFilterDTO.getAccountIdentifier(), resourceGroupFilterDTO.getOrgIdentifier(),
                      resourceGroupFilterDTO.getProjectIdentifier())
                  .toString()
                  .toLowerCase());
    } else if (isNotEmpty(resourceGroupFilterDTO.getScopeLevelFilter())) {
      criteria.and(ResourceGroupKeys.allowedScopeLevels).in(resourceGroupFilterDTO.getScopeLevelFilter());
    }

    List<Criteria> andOperatorCriteriaList = new ArrayList<>();

    if (ManagedFilter.ONLY_MANAGED.equals(resourceGroupFilterDTO.getManagedFilter())) {
      andOperatorCriteriaList.add(managedCriteria);
    } else if (ManagedFilter.ONLY_CUSTOM.equals(resourceGroupFilterDTO.getManagedFilter())) {
      andOperatorCriteriaList.add(scopeCriteria);
    } else {
      andOperatorCriteriaList.add(new Criteria().orOperator(scopeCriteria, managedCriteria));
    }

    if (isNotEmpty(resourceGroupFilterDTO.getSearchTerm())) {
      andOperatorCriteriaList.add(new Criteria().orOperator(
          Criteria.where(ResourceGroupKeys.name).regex(resourceGroupFilterDTO.getSearchTerm(), "i"),
          Criteria.where(ResourceGroupKeys.identifier).regex(resourceGroupFilterDTO.getSearchTerm(), "i"),
          Criteria.where(ResourceGroupKeys.tags + "." + NGTagKeys.key)
              .regex(resourceGroupFilterDTO.getSearchTerm(), "i"),
          Criteria.where(ResourceGroupKeys.tags + "." + NGTagKeys.value)
              .regex(resourceGroupFilterDTO.getSearchTerm(), "i")));
    }

    if (isNotEmpty(resourceGroupFilterDTO.getResourceSelectorFilterList())) {
      List<Criteria> resourceSelectorCriteria = new ArrayList<>();
      resourceGroupFilterDTO.getResourceSelectorFilterList().forEach(resourceSelectorFilter
          -> resourceSelectorCriteria.add(
              Criteria.where(ResourceGroupKeys.resourceFilter + "." + ResourceFilterKeys.resources)
                  .elemMatch(Criteria.where(ResourceSelectorKeys.resourceType)
                                 .is(resourceSelectorFilter.getResourceType())
                                 .and(ResourceSelectorKeys.identifiers)
                                 .is(resourceSelectorFilter.getResourceIdentifier()))));
      andOperatorCriteriaList.add(new Criteria().orOperator(resourceSelectorCriteria.toArray(new Criteria[0])));
    }

    criteria.andOperator(andOperatorCriteriaList.toArray(new Criteria[0]));

    return criteria;
  }

  @Override
  public Page<ResourceGroupResponse> list(ResourceGroupFilterDTO resourceGroupFilterDTO, PageRequest pageRequest) {
    Page<ResourceGroup> resourceGroupPageResponse;
    Criteria criteria = getResourceGroupFilterCriteria(resourceGroupFilterDTO);
    if (!accessControlClient.hasAccess(
            ResourceScope.of(resourceGroupFilterDTO.getAccountIdentifier(), resourceGroupFilterDTO.getOrgIdentifier(),
                resourceGroupFilterDTO.getProjectIdentifier()),
            Resource.of(RESOURCE_GROUP, null), VIEW_RESOURCEGROUP_PERMISSION)) {
      List<ResourceGroup> resourceGroups = resourceGroupV2Repository.findAll(criteria, Pageable.unpaged()).getContent();

      resourceGroups = getPermittedResourceGroups(resourceGroups);
      resourceGroupPageResponse =
          getPaginatedResult(resourceGroups, pageRequest.getPageIndex(), pageRequest.getPageSize());
    } else {
      resourceGroupPageResponse = resourceGroupV2Repository.findAll(criteria, getPageRequest(pageRequest));
    }
    return resourceGroupPageResponse.map(ResourceGroupMapper::toResponseWrapper);
  }

  private Page<ResourceGroup> getPaginatedResult(List<ResourceGroup> unpagedResourceGroup, int page, int size) {
    if (unpagedResourceGroup.isEmpty()) {
      return Page.empty();
    }
    List<ResourceGroup> resourceGroups = new ArrayList<>(unpagedResourceGroup);
    resourceGroups.sort(Comparator.comparing(ResourceGroup::getCreatedAt).reversed());
    return PageUtils.getPage(resourceGroups, page, size);
  }

  @Override
  public Page<ResourceGroupResponse> list(Scope scope, PageRequest pageRequest, String searchTerm) {
    Page<ResourceGroup> resourceGroupPageResponse;
    if (isEmpty(pageRequest.getSortOrders())) {
      SortOrder harnessManagedOrder =
          SortOrder.Builder.aSortOrder().withField(ResourceGroupKeys.harnessManaged, SortOrder.OrderType.DESC).build();
      SortOrder lastModifiedOrder =
          SortOrder.Builder.aSortOrder().withField(ResourceGroupKeys.lastModifiedAt, SortOrder.OrderType.DESC).build();
      pageRequest.setSortOrders(ImmutableList.of(harnessManagedOrder, lastModifiedOrder));
    }
    Pageable page = getPageRequest(pageRequest);
    ResourceGroupFilterDTO resourceGroupFilterDTO = ResourceGroupFilterDTO.builder()
                                                        .accountIdentifier(scope.getAccountIdentifier())
                                                        .orgIdentifier(scope.getOrgIdentifier())
                                                        .projectIdentifier(scope.getProjectIdentifier())
                                                        .searchTerm(searchTerm)
                                                        .build();
    Criteria criteria = getResourceGroupFilterCriteria(resourceGroupFilterDTO);

    if (!accessControlClient.hasAccess(
            ResourceScope.of(scope.getAccountIdentifier(), scope.getOrgIdentifier(), scope.getProjectIdentifier()),
            Resource.of(RESOURCE_GROUP, null), VIEW_RESOURCEGROUP_PERMISSION)) {
      List<ResourceGroup> resourceGroups = resourceGroupV2Repository.findAll(criteria, Pageable.unpaged()).getContent();
      resourceGroups = getPermittedResourceGroups(resourceGroups);
      resourceGroupPageResponse =
          getPaginatedResult(resourceGroups, pageRequest.getPageIndex(), pageRequest.getPageSize());
    } else {
      resourceGroupPageResponse = resourceGroupV2Repository.findAll(criteria, page);
    }
    return resourceGroupPageResponse.map(ResourceGroupMapper::toResponseWrapper);
  }

  private List<ResourceGroup> getPermittedResourceGroups(List<ResourceGroup> resourceGroups) {
    if (isEmpty(resourceGroups)) {
      return Collections.emptyList();
    }

    Map<EntityScopeInfo, List<ResourceGroup>> entityScopeInfoListMap = resourceGroups.stream().collect(
        Collectors.groupingBy(ResourceGroupServiceImpl::getEntityScopeInfoFromResourceGroup));

    List<PermissionCheckDTO> permissionChecks =
        resourceGroups.stream()
            .map(resourceGroup
                -> PermissionCheckDTO.builder()
                       .permission(VIEW_RESOURCEGROUP_PERMISSION)
                       .resourceIdentifier(resourceGroup.getIdentifier())
                       .resourceScope(ResourceScope.of(resourceGroup.getAccountIdentifier(),
                           resourceGroup.getOrgIdentifier(), resourceGroup.getProjectIdentifier()))
                       .resourceType(RESOURCE_GROUP)
                       .build())
            .collect(Collectors.toList());
    AccessCheckResponseDTO accessCheckResponse = accessControlClient.checkForAccessOrThrow(permissionChecks);

    List<ResourceGroup> permittedResourceGroup = new ArrayList<>();
    for (AccessControlDTO accessControlDTO : accessCheckResponse.getAccessControlList()) {
      if (accessControlDTO.isPermitted()) {
        permittedResourceGroup.add(
            entityScopeInfoListMap.get(getEntityScopeInfoFromAccessControlDTO(accessControlDTO)).get(0));
      }
    }
    return permittedResourceGroup;
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

  private static EntityScopeInfo getEntityScopeInfoFromResourceGroup(ResourceGroup resourceGroup) {
    return EntityScopeInfo.builder()
        .accountIdentifier(resourceGroup.getAccountIdentifier())
        .orgIdentifier(isBlank(resourceGroup.getOrgIdentifier()) ? null : resourceGroup.getOrgIdentifier())
        .projectIdentifier(isBlank(resourceGroup.getProjectIdentifier()) ? null : resourceGroup.getProjectIdentifier())
        .identifier(resourceGroup.getIdentifier())
        .build();
  }

  @Override
  public Optional<ResourceGroupResponse> get(Scope scope, String identifier, ManagedFilter managedFilter) {
    Optional<ResourceGroup> resourceGroupOpt = getResourceGroup(scope, identifier, managedFilter);
    return Optional.ofNullable(ResourceGroupMapper.toResponseWrapper(resourceGroupOpt.orElse(null)));
  }

  private Optional<ResourceGroup> getResourceGroup(Scope scope, String identifier, ManagedFilter managedFilter) {
    Criteria criteria = new Criteria();
    criteria.and(ResourceGroupKeys.identifier).is(identifier);
    if ((scope == null || isEmpty(scope.getAccountIdentifier())) && !ManagedFilter.ONLY_MANAGED.equals(managedFilter)) {
      throw new InvalidRequestException(
          "Either managed filter should be set to only managed, or scope filter should be non-empty");
    }

    Criteria managedCriteria = getBaseScopeCriteria(null, null, null).and(ResourceGroupKeys.harnessManaged).is(true);

    if (ManagedFilter.ONLY_MANAGED.equals(managedFilter)) {
      if (scope != null && isNotEmpty(scope.getAccountIdentifier())) {
        managedCriteria.and(ResourceGroupKeys.allowedScopeLevels).is(ScopeLevel.of(scope).toString().toLowerCase());
      }
      criteria.andOperator(managedCriteria);
    } else if (ManagedFilter.ONLY_CUSTOM.equals(managedFilter)) {
      criteria.andOperator(
          getBaseScopeCriteria(scope.getAccountIdentifier(), scope.getOrgIdentifier(), scope.getProjectIdentifier())
              .and(ResourceGroupKeys.harnessManaged)
              .ne(true));
    } else {
      managedCriteria.and(ResourceGroupKeys.allowedScopeLevels).is(ScopeLevel.of(scope).toString().toLowerCase());
      criteria.orOperator(
          getBaseScopeCriteria(scope.getAccountIdentifier(), scope.getOrgIdentifier(), scope.getProjectIdentifier())
              .and(ResourceGroupKeys.harnessManaged)
              .ne(true),
          managedCriteria);
    }

    return resourceGroupV2Repository.find(criteria);
  }

  private Criteria getBaseScopeCriteria(String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    return Criteria.where(ResourceGroupKeys.accountIdentifier)
        .is(accountIdentifier)
        .and(ResourceGroupKeys.orgIdentifier)
        .is(orgIdentifier)
        .and(ResourceGroupKeys.projectIdentifier)
        .is(projectIdentifier);
  }

  @Override
  public Optional<ResourceGroupResponse> update(ResourceGroupDTO resourceGroupDTO, boolean harnessManaged) {
    return updateV2(resourceGroupDTO, harnessManaged, false);
  }

  private Optional<ResourceGroupResponse> updateV2(
      ResourceGroupDTO resourceGroupDTO, boolean harnessManaged, boolean isInternal) {
    ManagedFilter managedFilter = harnessManaged ? ManagedFilter.ONLY_MANAGED : ManagedFilter.ONLY_CUSTOM;
    Optional<ResourceGroup> resourceGroupOpt =
        getResourceGroup(Scope.of(resourceGroupDTO.getAccountIdentifier(), resourceGroupDTO.getOrgIdentifier(),
                             resourceGroupDTO.getProjectIdentifier()),
            resourceGroupDTO.getIdentifier(), managedFilter);
    if (!resourceGroupOpt.isPresent()) {
      throw new InvalidRequestException(
          String.format("Resource group with Identifier [{%s}] does not exist", resourceGroupDTO.getIdentifier()));
    }
    ResourceGroup updatedResourceGroup = ResourceGroupMapper.fromDTO(resourceGroupDTO);
    resourceGroupValidatorImpl.sanitizeResourceSelectors(updatedResourceGroup);

    ResourceGroup savedResourceGroup = resourceGroupOpt.get();
    if (savedResourceGroup.getHarnessManaged().equals(TRUE) && !harnessManaged) {
      throw new InvalidRequestException("Can't update managed resource group");
    }

    ResourceGroupDTO oldResourceGroup =
        (ResourceGroupDTO) HObjectMapper.clone(ResourceGroupMapper.toDTO(savedResourceGroup));
    savedResourceGroup.setName(updatedResourceGroup.getName());
    savedResourceGroup.setColor(updatedResourceGroup.getColor());
    savedResourceGroup.setTags(updatedResourceGroup.getTags());
    savedResourceGroup.setDescription(updatedResourceGroup.getDescription());
    savedResourceGroup.setResourceFilter(updatedResourceGroup.getResourceFilter());
    savedResourceGroup.setIncludedScopes(updatedResourceGroup.getIncludedScopes());
    if (areScopeLevelsUpdated(savedResourceGroup, updatedResourceGroup) && !harnessManaged) {
      throw new InvalidRequestException("Cannot change the scopes at which this resource group can be used.");
    }
    savedResourceGroup.setAllowedScopeLevels(updatedResourceGroup.getAllowedScopeLevels());

    if (isInternal) {
      updatedResourceGroup = resourceGroupV2Repository.save(savedResourceGroup);
    } else {
      updatedResourceGroup = Failsafe.with(DEFAULT_RETRY_POLICY).get(() -> transactionTemplate.execute(status -> {
        ResourceGroup resourceGroup = resourceGroupV2Repository.save(savedResourceGroup);
        outboxService.save(new ResourceGroupUpdateEvent(savedResourceGroup.getAccountIdentifier(), null, null,
            ResourceGroupMapper.toDTO(savedResourceGroup), oldResourceGroup));
        return resourceGroup;
      }));
    }
    return Optional.ofNullable(ResourceGroupMapper.toResponseWrapper(updatedResourceGroup));
  }

  @Override
  public void deleteManaged(String identifier) {
    Optional<ResourceGroup> resourceGroupOpt = getResourceGroup(null, identifier, ManagedFilter.ONLY_MANAGED);
    if (!resourceGroupOpt.isPresent()) {
      return;
    }
    ResourceGroup resourceGroup = resourceGroupOpt.get();

    Failsafe.with(DEFAULT_RETRY_POLICY).get(() -> transactionTemplate.execute(status -> {
      resourceGroupV2Repository.delete(resourceGroup);
      outboxService.save(new ResourceGroupDeleteEvent(null, null, ResourceGroupMapper.toDTO(resourceGroup)));
      return true;
    }));
  }

  @Override
  public void deleteByScope(Scope scope) {
    if (scope == null || isEmpty(scope.getAccountIdentifier())) {
      throw new InvalidRequestException("Invalid scope. Cannot proceed with deletion.");
    }
    Failsafe.with(DEFAULT_RETRY_POLICY).get(() -> transactionTemplate.execute(status -> {
      List<ResourceGroup> deletedResourceGroups =
          resourceGroupV2Repository.deleteByAccountIdentifierAndOrgIdentifierAndProjectIdentifier(
              scope.getAccountIdentifier(), scope.getOrgIdentifier(), scope.getProjectIdentifier());
      if (isNotEmpty(deletedResourceGroups)) {
        deletedResourceGroups.forEach(rg
            -> outboxService.save(
                new ResourceGroupDeleteEvent(rg.getAccountIdentifier(), null, ResourceGroupMapper.toDTO(rg))));
      }
      return true;
    }));
  }

  @Override
  public boolean delete(Scope scope, String identifier) {
    Optional<ResourceGroup> resourceGroupOpt = getResourceGroup(scope, identifier, ManagedFilter.ONLY_CUSTOM);
    if (!resourceGroupOpt.isPresent()) {
      return false;
    }

    ResourceGroup resourceGroup = resourceGroupOpt.get();
    if (Boolean.TRUE.equals(resourceGroup.getHarnessManaged())) {
      throw new InvalidRequestException("Managed resource group cannot be deleted");
    }

    return Failsafe.with(DEFAULT_RETRY_POLICY).get(() -> transactionTemplate.execute(status -> {
      resourceGroupV2Repository.delete(resourceGroup);
      outboxService.save(
          new ResourceGroupDeleteEvent(scope.getAccountIdentifier(), null, ResourceGroupMapper.toDTO(resourceGroup)));
      return true;
    }));
  }

  private boolean areScopeLevelsUpdated(ResourceGroup currentResourceGroup, ResourceGroup resourceGroupUpdate) {
    if (isEmpty(currentResourceGroup.getAllowedScopeLevels())) {
      return false;
    }
    return !currentResourceGroup.getAllowedScopeLevels().equals(resourceGroupUpdate.getAllowedScopeLevels());
  }
}
