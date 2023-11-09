/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.accesscontrol.resources.resourcegroups;

import static io.harness.accesscontrol.common.filter.ManagedFilter.ONLY_CUSTOM;
import static io.harness.accesscontrol.common.filter.ManagedFilter.ONLY_MANAGED;
import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.outbox.TransactionOutboxModule.OUTBOX_TRANSACTION_TEMPLATE;

import static java.util.Collections.singleton;

import io.harness.accesscontrol.common.filter.ManagedFilter;
import io.harness.accesscontrol.resources.resourcegroups.events.ResourceGroupDeleteEvent;
import io.harness.accesscontrol.resources.resourcegroups.events.ResourceGroupUpdateEvent;
import io.harness.accesscontrol.resources.resourcegroups.persistence.ResourceGroupDao;
import io.harness.accesscontrol.roleassignments.RoleAssignmentFilter;
import io.harness.accesscontrol.roleassignments.RoleAssignmentService;
import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.UnexpectedException;
import io.harness.ng.beans.PageRequest;
import io.harness.ng.beans.PageResponse;
import io.harness.outbox.api.OutboxService;
import io.harness.springdata.PersistenceUtils;

import com.google.common.collect.Sets;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import javax.validation.executable.ValidateOnExecution;
import lombok.extern.slf4j.Slf4j;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;
import org.springframework.transaction.support.TransactionTemplate;

@OwnedBy(PL)
@Slf4j
@Singleton
@ValidateOnExecution
public class ResourceGroupServiceImpl implements ResourceGroupService {
  private final ResourceGroupDao resourceGroupDao;
  private final RoleAssignmentService roleAssignmentService;
  private final TransactionTemplate outboxTransactionTemplate;
  private final OutboxService outboxService;

  private static final RetryPolicy<Object> deleteResourceGroupTransactionPolicy = PersistenceUtils.getRetryPolicy(
      "[Retrying]: Failed to delete resource group and corresponding role assignments; attempt: {}",
      "[Failed]: Failed to delete resource group and corresponding role assignments; attempt: {}");

  private static final RetryPolicy<Object> upsertResourceGroupTransactionPolicy = PersistenceUtils.getRetryPolicy(
      "[Retrying]: Failed to upsert resource group and corresponding role assignments; attempt: {}",
      "[Failed]: Failed to upsert resource group and corresponding role assignments; attempt: {}");

  @Inject
  public ResourceGroupServiceImpl(ResourceGroupDao resourceGroupDao, RoleAssignmentService roleAssignmentService,
      @Named(OUTBOX_TRANSACTION_TEMPLATE) TransactionTemplate outboxTransactionTemplate, OutboxService outboxService) {
    this.resourceGroupDao = resourceGroupDao;
    this.roleAssignmentService = roleAssignmentService;
    this.outboxTransactionTemplate = outboxTransactionTemplate;
    this.outboxService = outboxService;
  }

  @Override
  public ResourceGroup upsert(ResourceGroup resourceGroup) {
    ManagedFilter managedFilter = resourceGroup.isManaged() ? ONLY_MANAGED : ONLY_CUSTOM;
    Optional<ResourceGroup> currentResourceGroupOptional =
        get(resourceGroup.getIdentifier(), resourceGroup.getScopeIdentifier(), managedFilter);
    if (currentResourceGroupOptional.isPresent()) {
      ResourceGroup currentResourceGroup = currentResourceGroupOptional.get();
      if (areScopeLevelsUpdated(currentResourceGroup, resourceGroup) && !resourceGroup.isManaged()) {
        throw new InvalidRequestException("Cannot change the the scopes at which this resource group can be used.");
      }
      if (areScopeLevelsUpdated(currentResourceGroup, resourceGroup) && resourceGroup.isManaged()) {
        return Failsafe.with(upsertResourceGroupTransactionPolicy)
            .get(() -> outboxTransactionTemplate.execute(status -> {
              Set<String> removedScopeLevels =
                  Sets.difference(currentResourceGroup.getAllowedScopeLevels(), resourceGroup.getAllowedScopeLevels());
              roleAssignmentService.deleteMulti(RoleAssignmentFilter.builder()
                                                    .resourceGroupFilter(singleton(resourceGroup.getIdentifier()))
                                                    .scopeFilter("/")
                                                    .includeChildScopes(true)
                                                    .scopeLevelFilter(removedScopeLevels)
                                                    .build());
              ResourceGroup updatedResourceGroup = resourceGroupDao.upsert(resourceGroup);
              outboxService.save(new ResourceGroupUpdateEvent(
                  currentResourceGroup, resourceGroup, currentResourceGroup.getScopeIdentifier()));
              return updatedResourceGroup;
            }));
      } else {
        return Failsafe.with(upsertResourceGroupTransactionPolicy)
            .get(() -> outboxTransactionTemplate.execute(status -> {
              ResourceGroup updatedResourceGroup = resourceGroupDao.upsert(resourceGroup);
              outboxService.save(new ResourceGroupUpdateEvent(
                  currentResourceGroup, resourceGroup, currentResourceGroup.getScopeIdentifier()));
              return updatedResourceGroup;
            }));
      }
    }
    return resourceGroupDao.upsert(resourceGroup);
  }

  private boolean areScopeLevelsUpdated(ResourceGroup currentResourceGroup, ResourceGroup resourceGroupUpdate) {
    if (isEmpty(currentResourceGroup.getAllowedScopeLevels())) {
      return false;
    }
    return !currentResourceGroup.getAllowedScopeLevels().equals(resourceGroupUpdate.getAllowedScopeLevels());
  }

  @Override
  public PageResponse<ResourceGroup> list(PageRequest pageRequest, String scopeIdentifier) {
    return resourceGroupDao.list(pageRequest, scopeIdentifier);
  }

  @Override
  public List<ResourceGroup> list(
      List<String> resourceGroupIdentifiers, String scopeIdentifier, ManagedFilter managedFilter) {
    return resourceGroupDao.list(resourceGroupIdentifiers, scopeIdentifier, managedFilter);
  }

  @Override
  public Optional<ResourceGroup> get(String identifier, String scopeIdentifier, ManagedFilter managedFilter) {
    return resourceGroupDao.get(identifier, scopeIdentifier, managedFilter);
  }

  @Override
  public void deleteIfPresent(String identifier, String scopeIdentifier) {
    Optional<ResourceGroup> currentResourceGroupOptional = get(identifier, scopeIdentifier, ManagedFilter.ONLY_CUSTOM);
    if (currentResourceGroupOptional.isPresent()) {
      deleteCustom(identifier, scopeIdentifier);
    }
  }

  @Override
  public void deleteManagedIfPresent(String identifier) {
    Optional<ResourceGroup> currentResourceGroupOptional = get(identifier, null, ManagedFilter.ONLY_MANAGED);
    if (currentResourceGroupOptional.isPresent()) {
      deleteManaged(identifier);
    }
  }

  private ResourceGroup deleteManaged(String identifier) {
    return Failsafe.with(deleteResourceGroupTransactionPolicy).get(() -> outboxTransactionTemplate.execute(status -> {
      long deleteCount = roleAssignmentService.deleteMulti(RoleAssignmentFilter.builder()
                                                               .scopeFilter("/")
                                                               .includeChildScopes(true)
                                                               .resourceGroupFilter(Sets.newHashSet(identifier))
                                                               .build());

      ResourceGroup resourceGroup =
          resourceGroupDao.delete(identifier, null)
              .orElseThrow(()
                               -> new UnexpectedException(String.format(
                                   "Failed to delete the resource group %s in the scope %s", identifier, null)));
      outboxService.save(new ResourceGroupDeleteEvent(resourceGroup, null));
      return resourceGroup;
    }));
  }

  private ResourceGroup deleteCustom(String identifier, String scopeIdentifier) {
    return Failsafe.with(deleteResourceGroupTransactionPolicy).get(() -> outboxTransactionTemplate.execute(status -> {
      long deleteCount = roleAssignmentService.deleteMulti(RoleAssignmentFilter.builder()
                                                               .scopeFilter(scopeIdentifier)
                                                               .resourceGroupFilter(Sets.newHashSet(identifier))
                                                               .build());

      ResourceGroup resourceGroup =
          resourceGroupDao.delete(identifier, scopeIdentifier)
              .orElseThrow(
                  ()
                      -> new UnexpectedException(String.format(
                          "Failed to delete the resource group %s in the scope %s", identifier, scopeIdentifier)));
      outboxService.save(new ResourceGroupDeleteEvent(resourceGroup, scopeIdentifier));
      return resourceGroup;
    }));
  }
}
