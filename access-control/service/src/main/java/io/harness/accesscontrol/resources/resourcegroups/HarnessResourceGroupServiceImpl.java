/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.accesscontrol.resources.resourcegroups;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.accesscontrol.scopes.core.Scope;
import io.harness.accesscontrol.scopes.harness.HarnessScopeParams;
import io.harness.accesscontrol.scopes.harness.ScopeMapper;
import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.InvalidRequestException;
import io.harness.remote.client.NGRestUtils;
import io.harness.resourcegroup.v2.remote.dto.ResourceGroupResponse;
import io.harness.resourcegroupclient.remote.ResourceGroupClient;
import io.harness.utils.RetryUtils;

import com.google.common.collect.Lists;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.time.Duration;
import java.util.Optional;
import javax.validation.executable.ValidateOnExecution;
import lombok.extern.slf4j.Slf4j;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;

@OwnedBy(PL)
@Slf4j
@ValidateOnExecution
@Singleton
public class HarnessResourceGroupServiceImpl implements HarnessResourceGroupService {
  private final ResourceGroupClient resourceGroupClient;
  private final ResourceGroupFactory resourceGroupFactory;
  private final ResourceGroupService resourceGroupService;

  private static final RetryPolicy<Object> retryPolicy =
      RetryUtils.getRetryPolicy("Could not find the resource group with the given identifier on attempt %s",
          "Could not find the resource group with the given identifier",
          Lists.newArrayList(InvalidRequestException.class), Duration.ofSeconds(5), 3, log);

  @Inject
  public HarnessResourceGroupServiceImpl(@Named("PRIVILEGED") ResourceGroupClient resourceGroupClient,
      ResourceGroupFactory resourceGroupFactory, ResourceGroupService resourceGroupService) {
    this.resourceGroupClient = resourceGroupClient;
    this.resourceGroupFactory = resourceGroupFactory;
    this.resourceGroupService = resourceGroupService;
  }

  @Override
  public void sync(String identifier, Scope scope) {
    HarnessScopeParams scopeParams = ScopeMapper.toParams(scope);
    try {
      Optional<ResourceGroupResponse> resourceGroupResponse = Failsafe.with(retryPolicy).get(() -> {
        ResourceGroupResponse response = NGRestUtils.getResponse(resourceGroupClient.getResourceGroup(identifier,
            scopeParams.getAccountIdentifier(), scopeParams.getOrgIdentifier(), scopeParams.getProjectIdentifier()));
        return Optional.ofNullable(response);
      });
      if (resourceGroupResponse.isPresent()) {
        resourceGroupService.upsert(resourceGroupFactory.buildResourceGroup(resourceGroupResponse.get()));
      } else {
        deleteIfPresent(identifier, scope);
      }
    } catch (Exception e) {
      log.error("Exception while syncing resource group", e);
    }
  }

  @Override
  public void deleteIfPresent(String identifier, Scope scope) {
    String flatScope = scope == null ? null : scope.toString();
    log.warn("Removing resource group with identifier {} in scope {}", identifier, flatScope);
    if (flatScope == null) {
      resourceGroupService.deleteManagedIfPresent(identifier);
    } else {
      resourceGroupService.deleteIfPresent(identifier, flatScope);
    }
  }
}
