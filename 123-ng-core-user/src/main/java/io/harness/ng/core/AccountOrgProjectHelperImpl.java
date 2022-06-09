/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ng.core;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.account.AccountClient;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.Scope;
import io.harness.ng.core.dto.AccountDTO;
import io.harness.ng.core.entities.Organization;
import io.harness.ng.core.entities.Project;
import io.harness.ng.core.services.OrganizationService;
import io.harness.ng.core.services.ProjectService;
import io.harness.remote.client.RestClientUtils;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Optional;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(PL)
@Singleton
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
public class AccountOrgProjectHelperImpl implements AccountOrgProjectHelper {
  private final OrganizationService organizationService;
  private final ProjectService projectService;
  private final AccountClient accountClient;

  public String getBaseUrl(String accountIdentifier) {
    return RestClientUtils.getResponse(accountClient.getBaseUrl(accountIdentifier));
  }

  public String getGatewayBaseUrl(String accountIdentifier) {
    return RestClientUtils.getResponse(accountClient.getGatewayBaseUrl(accountIdentifier));
  }

  public String getAccountName(String accountIdentifier) {
    AccountDTO account = RestClientUtils.getResponse(accountClient.getAccountDTO(accountIdentifier));
    if (account == null) {
      throw new IllegalStateException(String.format("Account with identifier [%s] doesn't exists", accountIdentifier));
    }
    return account.getName();
  }

  public String getResourceScopeName(Scope scope) {
    if (isNotEmpty(scope.getProjectIdentifier())) {
      return getProjectName(scope.getAccountIdentifier(), scope.getOrgIdentifier(), scope.getProjectIdentifier());
    } else if (isNotEmpty(scope.getOrgIdentifier())) {
      return getOrgName(scope.getAccountIdentifier(), scope.getOrgIdentifier());
    }
    return getAccountName(scope.getAccountIdentifier());
  }

  public String getProjectName(String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    Optional<Project> projectOpt = projectService.get(accountIdentifier, orgIdentifier, projectIdentifier);
    if (!projectOpt.isPresent()) {
      throw new IllegalStateException(String.format("Project with identifier [%s] doesn't exists", projectIdentifier));
    }
    return projectOpt.get().getName();
  }

  public String getOrgName(String accountIdentifier, String orgIdentifier) {
    Optional<Organization> organizationOpt = organizationService.get(accountIdentifier, orgIdentifier);
    if (!organizationOpt.isPresent()) {
      throw new IllegalStateException(String.format("Organization with identifier [%s] doesn't exists", orgIdentifier));
    }
    return organizationOpt.get().getName();
  }

  public String getVanityUrl(String accountIdentifier) {
    return RestClientUtils.getResponse(accountClient.getVanityUrl(accountIdentifier));
  }
}
