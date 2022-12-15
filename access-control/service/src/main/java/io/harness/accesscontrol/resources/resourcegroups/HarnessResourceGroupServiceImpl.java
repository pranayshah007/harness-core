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
import io.harness.remote.client.NGRestUtils;
import io.harness.resourcegroup.v2.remote.dto.ResourceGroupResponse;
import io.harness.resourcegroupclient.remote.ResourceGroupClient;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.util.Optional;
import javax.validation.executable.ValidateOnExecution;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(PL)
@Slf4j
@ValidateOnExecution
@Singleton
public class HarnessResourceGroupServiceImpl implements HarnessResourceGroupService {
  private final ResourceGroupClient resourceGroupClient;
  private final ResourceGroupFactory resourceGroupFactory;
  private final ResourceGroupService resourceGroupService;

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
      Optional<ResourceGroupResponse> resourceGroupResponse =
          Optional.ofNullable(NGRestUtils.getResponse(resourceGroupClient.getResourceGroup(identifier,
              scopeParams.getAccountIdentifier(), scopeParams.getOrgIdentifier(), scopeParams.getProjectIdentifier())));
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
