/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.graphql.datafetcher.environment;

import static io.harness.beans.PageRequest.PageRequestBuilder.aPageRequest;
import static io.harness.beans.SearchFilter.Operator.IN;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static software.wings.graphql.schema.type.QLEnvFilterType.PRODUCTION_ENVIRONMENTS;
import static software.wings.security.EnvFilter.FilterType.NON_PROD;
import static software.wings.security.EnvFilter.FilterType.PROD;
import static software.wings.security.EnvFilter.FilterType.SELECTED;

import static com.google.common.collect.Iterables.getFirst;
import static com.google.common.collect.Sets.difference;
import static java.lang.String.format;
import static java.lang.String.join;
import static org.apache.commons.collections4.SetUtils.emptyIfNull;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.PageRequest;
import io.harness.beans.PageResponse;
import io.harness.beans.SearchFilter;
import io.harness.exception.InvalidRequestException;

import software.wings.beans.Environment;
import software.wings.graphql.schema.type.QLEnvFilter;
import software.wings.graphql.schema.type.QLEnvFilterType;
import software.wings.graphql.schema.type.secrets.QLEnvScopeFilter;
import software.wings.security.EnvFilter;
import software.wings.service.intfc.EnvironmentService;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

@Singleton
@TargetModule(HarnessModule._380_CG_GRAPHQL)
public class EnvFilterController {
  @Inject EnvironmentService environmentService;
  public void validateEnvFilter(QLEnvFilter envFilter, String accountId) {
    if (envFilter == null) {
      throw new InvalidRequestException("The app filter cannot be null");
    }
    if (envFilter.getEnvIds() == null && envFilter.getFilterTypes() == null) {
      throw new InvalidRequestException("No envIds or filterTypes provided in the  env filter");
    }
    checkEnvExists(envFilter.getEnvIds(), accountId);
  }

  public void validateEnvFilter(QLEnvScopeFilter envFilter, String accountId, String appId) {
    if (envFilter == null) {
      throw new InvalidRequestException("The app filter cannot be null");
    }
    if (isEmpty(envFilter.getEnvId()) && envFilter.getFilterType() == null) {
      throw new InvalidRequestException("No envId or filterType provided in the env filter");
    }
    if (isNotEmpty(envFilter.getEnvId()) && envFilter.getFilterType() != null) {
      throw new InvalidRequestException("Cannot set both envId and filterType in the env filter");
    }
    if (envFilter.getEnvId() != null) {
      Environment env = environmentService.get(appId, envFilter.getEnvId());
      if (env == null || !env.getAccountId().equals(accountId)) {
        throw new InvalidRequestException(String.format(
            String.format("No env exists with id %s in application with id %s", appId, envFilter.getEnvId())));
      }
    }
  }

  private void checkEnvIdsAreValid(Set<String> idsInput, Set<String> idsPresent) {
    final Set<String> difference = difference(idsInput, idsPresent);
    if (!difference.isEmpty()) {
      throw new InvalidRequestException(format("Invalid env id/s %s provided in the request", join(",", difference)));
    }
  }

  public EnvFilter createEnvFilter(QLEnvScopeFilter envFilter) {
    if (isEmpty(envFilter.getEnvId())) {
      String filterType = PROD;
      if (envFilter.getFilterType() == QLEnvFilterType.NON_PRODUCTION_ENVIRONMENTS) {
        filterType = NON_PROD;
      }
      return EnvFilter.builder().filterTypes(Collections.singleton(filterType)).build();
    }
    return EnvFilter.builder()
        .ids(Collections.singleton(envFilter.getEnvId()))
        .filterTypes(Collections.singleton(SELECTED))
        .build();
  }

  public void checkEnvExists(Set<String> envIds, String accountId) {
    if (isEmpty(envIds)) {
      return;
    }
    PageRequest<Environment> req = aPageRequest()
                                       .addFieldsIncluded("_id")
                                       .addFilter("accountId", SearchFilter.Operator.EQ, accountId)
                                       .addFilter("_id", IN, envIds.toArray())
                                       .build();
    PageResponse<Environment> res = environmentService.list(req, false, null, false);
    // This Ids are wrong
    Set<String> idsPresent = res.stream().map(Environment::getUuid).collect(Collectors.toSet());
    checkEnvIdsAreValid(envIds, idsPresent);
  }

  public QLEnvFilter createEnvFilterOutput(EnvFilter envPermissions) {
    if (isEmpty(envPermissions.getIds())) {
      EnumSet<QLEnvFilterType> filterTypes = EnumSet.noneOf(QLEnvFilterType.class);
      if (envPermissions.getFilterTypes().contains(PROD)) {
        filterTypes.add(PRODUCTION_ENVIRONMENTS);
      }
      if (envPermissions.getFilterTypes().contains(NON_PROD)) {
        filterTypes.add(QLEnvFilterType.NON_PRODUCTION_ENVIRONMENTS);
      }
      return QLEnvFilter.builder().envIds(envPermissions.getIds()).filterTypes(filterTypes).build();
    }
    return QLEnvFilter.builder().envIds(envPermissions.getIds()).build();
  }

  public QLEnvScopeFilter createEnvScopeFilterOutput(EnvFilter envFilter) {
    if (!envFilter.getFilterTypes().contains(SELECTED)) {
      QLEnvFilterType filterType = PRODUCTION_ENVIRONMENTS;
      if (envFilter.getFilterTypes().contains(NON_PROD)) {
        filterType = QLEnvFilterType.NON_PRODUCTION_ENVIRONMENTS;
      }
      return QLEnvScopeFilter.builder().filterType(filterType).build();
    }
    return QLEnvScopeFilter.builder().envId(getFirst(emptyIfNull(envFilter.getIds()), null)).build();
  }
}
