/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.graphql.datafetcher.application;

import static io.harness.beans.PageRequest.PageRequestBuilder.aPageRequest;
import static io.harness.beans.SearchFilter.Operator.IN;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static software.wings.security.GenericEntityFilter.FilterType.ALL;
import static software.wings.security.GenericEntityFilter.FilterType.SELECTED;

import static com.google.common.collect.Iterables.getFirst;
import static com.google.common.collect.Sets.difference;
import static java.lang.String.format;
import static java.lang.String.join;
import static org.apache.commons.collections4.SetUtils.emptyIfNull;
import static org.apache.commons.lang3.StringUtils.isBlank;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.PageRequest;
import io.harness.beans.PageResponse;
import io.harness.beans.SearchFilter;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.UnexpectedException;

import software.wings.beans.Application;
import software.wings.graphql.schema.type.QLAppFilter;
import software.wings.graphql.schema.type.QLAppFilterType;
import software.wings.graphql.schema.type.QLGenericFilterType;
import software.wings.graphql.schema.type.secrets.QLAppScopeFilter;
import software.wings.security.AppFilter;
import software.wings.security.GenericEntityFilter;
import software.wings.service.intfc.AppService;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

@Singleton
@TargetModule(HarnessModule._380_CG_GRAPHQL)
public class AppFilterController {
  @Inject private AppService appService;

  public void validateAppFilter(QLAppFilter appFilter, String accountId) {
    if (appFilter == null) {
      throw new InvalidRequestException("The app filter cannot be null");
    }
    if (isEmpty(appFilter.getAppIds()) && appFilter.getFilterType() == null) {
      throw new InvalidRequestException("No appIds or filterType provided in app filter");
    }
    if (isNotEmpty(appFilter.getAppIds()) && appFilter.getFilterType() == QLAppFilterType.ALL) {
      throw new InvalidRequestException("Cannot set both appIds and filterType ALL in app filter");
    }
    checkApplicationsExists(appFilter.getAppIds(), accountId);
  }

  public void validateAppScopeFilter(QLAppScopeFilter appFilter, String accountId) {
    if (appFilter == null) {
      throw new InvalidRequestException("The app filter cannot be null");
    }
    if (isEmpty(appFilter.getAppId()) && appFilter.getFilterType() == null) {
      throw new InvalidRequestException("No appId or filterType provided in the app filter");
    }
    if (isNotEmpty(appFilter.getAppId()) && appFilter.getFilterType() != null) {
      throw new InvalidRequestException("Cannot set both appId and filterType in the app filter");
    }
    if (!isBlank(appFilter.getAppId())) {
      Application app = appService.get(appFilter.getAppId());
      if (app == null || !app.getAccountId().equals(accountId)) {
        throw new InvalidRequestException(format("No application exists with the id %s", appFilter.getAppId()));
      }
    }
  }

  private void checkAppIdsAreValid(Set<String> idsInput, Set<String> idsPresent) {
    final Set<String> difference = difference(idsInput, idsPresent);
    if (!difference.isEmpty()) {
      throw new InvalidRequestException(format("Invalid app id/s %s provided in the request", join(",", difference)));
    }
  }

  // Creates Filter for Environment and Service Type filters
  public GenericEntityFilter createGenericEntityFilter(QLAppFilter appFilter) {
    String filterType = ALL;
    if (isNotEmpty(appFilter.getAppIds())) {
      filterType = SELECTED;
    }
    return GenericEntityFilter.builder().filterType(filterType).ids(appFilter.getAppIds()).build();
  }

  public AppFilter createAppFilter(QLAppFilter appFilter) {
    String filterType = ALL;
    if (appFilter.getFilterType() != null) {
      filterType = appFilter.getFilterType().toString();
    } else if (isNotEmpty(appFilter.getAppIds())) {
      filterType = SELECTED;
    }
    return AppFilter.builder().filterType(filterType).ids(appFilter.getAppIds()).build();
  }

  // Creates Filter for Environment and Service Type filters
  public GenericEntityFilter createGenericEntityFilter(QLAppScopeFilter appFilter) {
    if (isNotEmpty(appFilter.getAppId())) {
      return GenericEntityFilter.builder()
          .filterType(SELECTED)
          .ids(new HashSet<>(Arrays.asList(appFilter.getAppId())))
          .build();
    }
    return GenericEntityFilter.builder().filterType(ALL).build();
  }

  public void checkApplicationsExists(Set<String> appIds, String accountId) {
    if (isEmpty(appIds)) {
      return;
    }
    PageRequest<Application> req = aPageRequest()
                                       .addFieldsIncluded("_id")
                                       .addFilter("accountId", SearchFilter.Operator.EQ, accountId)
                                       .addFilter("_id", IN, appIds.toArray())
                                       .build();
    PageResponse<Application> res = appService.list(req);
    // This Ids are wrong
    Set<String> appIdsPresentSet = res.stream().map(Application::getUuid).collect(Collectors.toSet());
    checkAppIdsAreValid(appIds, appIdsPresentSet);
  }

  public QLAppFilter createAppFilterOutput(AppFilter appFilter) {
    if (appFilter == null) {
      return null;
    }
    if (isEmpty(appFilter.getIds())) {
      return QLAppFilter.builder().filterType(QLAppFilterType.ALL).build();
    } else if (appFilter.getFilterType() != null
        && AppFilter.FilterType.EXCLUDE_SELECTED.equals(appFilter.getFilterType())) {
      return QLAppFilter.builder().filterType(QLAppFilterType.EXCLUDE_SELECTED).appIds(appFilter.getIds()).build();
    }
    return QLAppFilter.builder().filterType(QLAppFilterType.SELECTED).appIds(appFilter.getIds()).build();
  }

  public QLAppScopeFilter createAppScopeFilterOutput(GenericEntityFilter appFilter) {
    if (appFilter == null) {
      return null;
    }

    if (ALL.equals(appFilter.getFilterType())) {
      return QLAppScopeFilter.builder().filterType(QLGenericFilterType.ALL).build();
    } else if (SELECTED.equals(appFilter.getFilterType())) {
      return QLAppScopeFilter.builder().appId(getFirst(emptyIfNull(appFilter.getIds()), null)).build();
    }

    throw new UnexpectedException("Unknown Filter Type : " + appFilter.getFilterType());
  }
}
