/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.accesscontrol.resources.resourcegroups.persistence;

import static io.harness.accesscontrol.common.filter.ManagedFilter.NO_FILTER;
import static io.harness.accesscontrol.common.filter.ManagedFilter.ONLY_CUSTOM;
import static io.harness.accesscontrol.common.filter.ManagedFilter.ONLY_MANAGED;
import static io.harness.accesscontrol.resources.resourcegroups.persistence.ResourceGroupDBOMapper.fromDBO;
import static io.harness.accesscontrol.resources.resourcegroups.persistence.ResourceGroupDBOMapper.toDBO;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static java.util.Collections.singletonList;

import io.harness.accesscontrol.common.filter.ManagedFilter;
import io.harness.accesscontrol.resources.resourcegroups.ResourceGroup;
import io.harness.accesscontrol.resources.resourcegroups.persistence.ResourceGroupDBO.ResourceGroupDBOKeys;
import io.harness.accesscontrol.scopes.core.Scope;
import io.harness.accesscontrol.scopes.core.ScopeService;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.beans.PageRequest;
import io.harness.ng.beans.PageResponse;
import io.harness.utils.PageUtils;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.validation.executable.ValidateOnExecution;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.query.Criteria;

@OwnedBy(HarnessTeam.PL)
@Singleton
@ValidateOnExecution
public class ResourceGroupDaoImpl implements ResourceGroupDao {
  private final ResourceGroupRepository resourceGroupRepository;
  private final ScopeService scopeService;

  @Inject
  public ResourceGroupDaoImpl(ResourceGroupRepository resourceGroupRepository, ScopeService scopeService) {
    this.resourceGroupRepository = resourceGroupRepository;
    this.scopeService = scopeService;
  }

  @Override
  public ResourceGroup upsert(ResourceGroup resourceGroupUpdate) {
    ResourceGroupDBO resourceGroupUpdateDBO = toDBO(resourceGroupUpdate);
    Optional<ResourceGroupDBO> resourceGroupOpt = resourceGroupRepository.findByIdentifierAndScopeIdentifier(
        resourceGroupUpdate.getIdentifier(), resourceGroupUpdate.getScopeIdentifier());
    if (resourceGroupOpt.isPresent()) {
      ResourceGroupDBO currentResourceGroupDBO = resourceGroupOpt.get();
      if (currentResourceGroupDBO.equals(resourceGroupUpdateDBO)) {
        return fromDBO(currentResourceGroupDBO);
      }
      resourceGroupUpdateDBO.setId(currentResourceGroupDBO.getId());
      resourceGroupUpdateDBO.setVersion(currentResourceGroupDBO.getVersion());
      resourceGroupUpdateDBO.setCreatedAt(currentResourceGroupDBO.getCreatedAt());
      resourceGroupUpdateDBO.setLastModifiedAt(currentResourceGroupDBO.getCreatedAt());
      resourceGroupUpdateDBO.setNextReconciliationIterationAt(
          currentResourceGroupDBO.getNextReconciliationIterationAt());
    }
    return fromDBO(resourceGroupRepository.save(resourceGroupUpdateDBO));
  }

  @Override
  public PageResponse<ResourceGroup> list(PageRequest pageRequest, String scopeIdentifier) {
    Pageable pageable = PageUtils.getPageRequest(pageRequest);
    Page<ResourceGroupDBO> resourceGroupPages =
        resourceGroupRepository.findByScopeIdentifier(scopeIdentifier, pageable);
    return PageUtils.getNGPageResponse(resourceGroupPages.map(ResourceGroupDBOMapper::fromDBO));
  }

  @Override
  public List<ResourceGroup> list(
      List<String> resourceGroupIdentifiers, String scopeIdentifier, ManagedFilter managedFilter) {
    Criteria criteria = getCriteria(resourceGroupIdentifiers, scopeIdentifier, managedFilter);
    List<ResourceGroupDBO> resourceGroupDBOs = resourceGroupRepository.findAllWithCriteria(criteria);
    return resourceGroupDBOs.stream().map(ResourceGroupDBOMapper::fromDBO).collect(Collectors.toList());
  }

  @Override
  public Optional<ResourceGroup> get(String identifier, String scopeIdentifier, ManagedFilter managedFilter) {
    Criteria criteria = getCriteria(singletonList(identifier), scopeIdentifier, managedFilter);
    return resourceGroupRepository.find(criteria).flatMap(r -> Optional.of(fromDBO(r)));
  }

  private Criteria getCriteria(
      List<String> resourceGroupIdentifiers, String scopeIdentifier, ManagedFilter managedFilter) {
    Criteria criteria = Criteria.where(ResourceGroupDBOKeys.identifier).in(resourceGroupIdentifiers);
    if (isEmpty(scopeIdentifier) && !ONLY_MANAGED.equals(managedFilter)) {
      throw new InvalidRequestException(
          "Either managed filter should be set to only managed, or scope filter should be non-empty");
    }
    if (ONLY_MANAGED.equals(managedFilter)) {
      criteria.and(ResourceGroupDBOKeys.scopeIdentifier).is(null);
      criteria.and(ResourceGroupDBOKeys.managed).is(true);
      if (isNotEmpty(scopeIdentifier)) {
        Scope scope = scopeService.buildScopeFromScopeIdentifier(scopeIdentifier);
        criteria.and(ResourceGroupDBOKeys.allowedScopeLevels).is(scope.getLevel().toString());
      }
    } else if (ONLY_CUSTOM.equals(managedFilter)) {
      criteria.and(ResourceGroupDBOKeys.scopeIdentifier).is(scopeIdentifier);
      criteria.and(ResourceGroupDBOKeys.managed).is(false);
    } else if (NO_FILTER.equals(managedFilter)) {
      Criteria managedCriteria =
          Criteria.where(ResourceGroupDBOKeys.scopeIdentifier).is(null).and(ResourceGroupDBOKeys.managed).is(true);
      if (isNotEmpty(scopeIdentifier)) {
        Scope scope = scopeService.buildScopeFromScopeIdentifier(scopeIdentifier);
        managedCriteria.and(ResourceGroupDBOKeys.allowedScopeLevels).is(scope.getLevel().toString());
      }
      Criteria customCriteria = Criteria.where(ResourceGroupDBOKeys.scopeIdentifier)
                                    .is(scopeIdentifier)
                                    .and(ResourceGroupDBOKeys.managed)
                                    .is(false);
      criteria.orOperator(managedCriteria, customCriteria);
    }

    return criteria;
  }

  @Override
  public Optional<ResourceGroup> delete(String identifier, String scopeIdentifier) {
    Optional<ResourceGroupDBO> optionalResourceGroupDBO =
        resourceGroupRepository.deleteByIdentifierAndScopeIdentifier(identifier, scopeIdentifier);
    return optionalResourceGroupDBO.map(ResourceGroupDBOMapper::fromDBO);
  }
}
