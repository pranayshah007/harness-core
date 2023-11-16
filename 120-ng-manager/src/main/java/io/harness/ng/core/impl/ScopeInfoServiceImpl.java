/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ng.core.impl;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.data.structure.EmptyPredicate.isEmpty;

import static java.lang.String.format;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.ScopeInfo;
import io.harness.beans.ScopeLevel;
import io.harness.ng.core.AccountOrgProjectValidator;
import io.harness.ng.core.services.OrganizationService;
import io.harness.ng.core.services.ProjectService;
import io.harness.ng.core.services.ScopeInfoService;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(PL)
@Singleton
@Slf4j
public class ScopeInfoServiceImpl implements ScopeInfoService {
  private final AccountOrgProjectValidator accountOrgProjectValidator;
  private final OrganizationService organizationService;
  private final ProjectService projectService;
  private final ScopeInfoHelper scopeInfoHelper;
  private final String SCOPE_DETAIL_RESOLVER_LOG_CONST = "[ScopeInfoService]:";

  @Inject
  public ScopeInfoServiceImpl(AccountOrgProjectValidator accountOrgProjectValidator,
      OrganizationService organizationService, ProjectService projectService, ScopeInfoHelper scopeInfoHelper) {
    this.accountOrgProjectValidator = accountOrgProjectValidator;
    this.organizationService = organizationService;
    this.projectService = projectService;
    this.scopeInfoHelper = scopeInfoHelper;
  }

  @Override
  public Optional<ScopeInfo> getScopeInfo(String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    if (isEmpty(orgIdentifier) && isEmpty(projectIdentifier)) {
      if (!accountOrgProjectValidator.isPresent(accountIdentifier, null, null)) {
        log.warn(format(
            "%s Account with identifier [%s] does not exist", SCOPE_DETAIL_RESOLVER_LOG_CONST, accountIdentifier));
        return Optional.empty();
      }
      return Optional.of(
          scopeInfoHelper.populateScopeInfo(ScopeLevel.ACCOUNT, accountIdentifier, accountIdentifier, null, null));
    }

    if (isEmpty(projectIdentifier)) {
      return organizationService.getScopeInfo(accountIdentifier, orgIdentifier, projectIdentifier);
    }

    return projectService.getScopeInfo(accountIdentifier, orgIdentifier, projectIdentifier);
  }
}
