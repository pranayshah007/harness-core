/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ng.core.impl;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.remote.client.CGRestUtils.getResponse;

import static java.lang.String.format;

import io.harness.account.AccountClient;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.ScopeLevel;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.DefaultOrganization;
import io.harness.ng.core.OrgIdentifier;
import io.harness.ng.core.ProjectIdentifier;
import io.harness.ng.core.beans.ScopeInfo;
import io.harness.ng.core.entities.Organization;
import io.harness.ng.core.entities.Project;
import io.harness.ng.core.services.OrganizationService;
import io.harness.ng.core.services.ProjectService;
import io.harness.ng.core.services.ScopeInfoService;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.util.Optional;
import javax.cache.Cache;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(PL)
@Singleton
@Slf4j
public class ScopeInfoServiceImpl implements ScopeInfoService {
  private final AccountClient accountClient;
  private final OrganizationService organizationService;
  private final ProjectService projectService;
  private final Cache<String, ScopeInfo> scopeInfoCache;
  private final String SCOPE_DETAIL_RESOLVER_LOG_CONST = "[ScopeInfoService]:";

  private static final String SCOPE_INFO_CACHE_KEY_DELIMITER = "/";

  @Inject
  public ScopeInfoServiceImpl(AccountClient accountClient, OrganizationService organizationService,
      ProjectService projectService, @Named(SCOPE_INFO_DATA_CACHE_KEY) Cache<String, ScopeInfo> scopeInfoCache) {
    this.accountClient = accountClient;
    this.organizationService = organizationService;
    this.projectService = projectService;
    this.scopeInfoCache = scopeInfoCache;
  }

  @Override
  public Optional<ScopeInfo> getScopeInfo(String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    final String cacheKey = getScopeInfoCacheKey(accountIdentifier, orgIdentifier, projectIdentifier);
    if (scopeInfoCache.containsKey(cacheKey)) {
      return Optional.of(scopeInfoCache.get(cacheKey));
    }

    if (isEmpty(orgIdentifier) && isEmpty(projectIdentifier)) {
      if (!isPresent(accountIdentifier, null, null)) {
        log.warn(format(
            "%s Account with identifier [%s] does not exist", SCOPE_DETAIL_RESOLVER_LOG_CONST, accountIdentifier));
        return Optional.empty();
      }
      Optional<ScopeInfo> optionalAccountScopeInfo =
          populateScopeInfo(ScopeLevel.ACCOUNT, accountIdentifier, accountIdentifier, null, null);
      scopeInfoCache.put(cacheKey, optionalAccountScopeInfo.get());
      return optionalAccountScopeInfo;
    }

    if (isEmpty(projectIdentifier)) {
      Optional<Organization> org = organizationService.get(accountIdentifier, orgIdentifier);
      if (org.isPresent()) {
        Optional<ScopeInfo> optionalOrgScopeInfo =
            populateScopeInfo(ScopeLevel.ORGANIZATION, org.get().getUniqueId(), accountIdentifier, orgIdentifier, null);
        scopeInfoCache.put(cacheKey, optionalOrgScopeInfo.get());
        return optionalOrgScopeInfo;
      } else {
        log.warn(format("%s Org with identifier [%s] in Account: [%s] does not exist", SCOPE_DETAIL_RESOLVER_LOG_CONST,
            orgIdentifier, accountIdentifier));
        return Optional.empty();
      }
    }

    Optional<Project> proj = projectService.get(accountIdentifier, orgIdentifier, projectIdentifier);
    if (proj.isPresent()) {
      Optional<ScopeInfo> optionalProjectScopeInfo = populateScopeInfo(
          ScopeLevel.PROJECT, proj.get().getUniqueId(), accountIdentifier, orgIdentifier, projectIdentifier);
      scopeInfoCache.put(cacheKey, optionalProjectScopeInfo.get());
      return optionalProjectScopeInfo;
    } else {
      log.warn(format("%s Project with identifier [%s] in Org: [%s] and Account: [%s] does not exist",
          SCOPE_DETAIL_RESOLVER_LOG_CONST, projectIdentifier, orgIdentifier, accountIdentifier));
      return Optional.empty();
    }
  }

  public void addScopeInfoToCache(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, ScopeLevel scopeType, String uniqueId) {
    scopeInfoCache.put(getScopeInfoCacheKey(accountIdentifier, orgIdentifier, projectIdentifier),
        populateScopeInfo(ScopeLevel.PROJECT, uniqueId, accountIdentifier, orgIdentifier, projectIdentifier).get());
  }

  public boolean removeScopeInfoFromCache(String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    return scopeInfoCache.remove(getScopeInfoCacheKey(accountIdentifier, orgIdentifier, projectIdentifier));
  }

  private Optional<ScopeInfo> populateScopeInfo(final ScopeLevel scopeType, final String uniqueId,
      final String accountIdentifier, final String orgIdentifier, final String projIdentifier) {
    ScopeInfo builtScope =
        ScopeInfo.builder().scopeType(scopeType).uniqueId(uniqueId).accountIdentifier(accountIdentifier).build();
    if (isNotEmpty(orgIdentifier)) {
      builtScope.setOrgIdentifier(orgIdentifier);
    }
    if (isNotEmpty(projIdentifier)) {
      builtScope.setProjectIdentifier(projIdentifier);
    }
    return Optional.of(builtScope);
  }

  private String getScopeInfoCacheKey(
      final String accountIdentifier, final String orgIdentifier, final String projectIdentifier) {
    // key-format: ACCOUNT/<accountIdentifier>/ORGANIZATION/orgIdentifier/PROJECT/projectIdentifier
    // append ACCOUNT
    StringBuilder sb = new StringBuilder()
                           .append(ScopeLevel.ACCOUNT.name())
                           .append(SCOPE_INFO_CACHE_KEY_DELIMITER)
                           .append(accountIdentifier);
    // append ORG
    if (isNotEmpty(orgIdentifier)) {
      sb.append(SCOPE_INFO_CACHE_KEY_DELIMITER)
          .append(ScopeLevel.ORGANIZATION.name())
          .append(SCOPE_INFO_CACHE_KEY_DELIMITER)
          .append(orgIdentifier);
    }
    // append PROJECT
    if (isNotEmpty(orgIdentifier) && isNotEmpty(projectIdentifier)) {
      sb.append(SCOPE_INFO_CACHE_KEY_DELIMITER)
          .append(ScopeLevel.PROJECT.name())
          .append(SCOPE_INFO_CACHE_KEY_DELIMITER)
          .append(projectIdentifier);
    }
    return sb.toString();
  }

  @DefaultOrganization
  public boolean isPresent(
      String accountIdentifier, @OrgIdentifier String orgIdentifier, @ProjectIdentifier String projectIdentifier) {
    if (isEmpty(accountIdentifier)) {
      return true;
    } else if (isEmpty(orgIdentifier)) {
      try {
        return getResponse(accountClient.getAccountDTO(accountIdentifier)) != null;
      } catch (InvalidRequestException exception) {
        log.error(String.format("Account with accountIdentifier %s not found", accountIdentifier));
        return false;
      }
    } else if (isEmpty(projectIdentifier)) {
      return organizationService.get(accountIdentifier, orgIdentifier).isPresent();
    } else {
      return projectService.get(accountIdentifier, orgIdentifier, projectIdentifier).isPresent();
    }
  }
}
