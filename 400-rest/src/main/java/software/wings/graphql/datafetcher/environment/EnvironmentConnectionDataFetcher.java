/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.graphql.datafetcher.environment;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import static software.wings.graphql.utils.nameservice.NameService.application;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.exception.WingsException;

import software.wings.beans.Environment;
import software.wings.beans.Environment.EnvironmentKeys;
import software.wings.graphql.datafetcher.AbstractConnectionV2DataFetcher;
import software.wings.graphql.schema.query.QLPageQueryParameters;
import software.wings.graphql.schema.type.QLEnvironment;
import software.wings.graphql.schema.type.QLEnvironment.QLEnvironmentBuilder;
import software.wings.graphql.schema.type.QLEnvironmentConnection;
import software.wings.graphql.schema.type.QLEnvironmentConnection.QLEnvironmentConnectionBuilder;
import software.wings.graphql.schema.type.aggregation.QLIdFilter;
import software.wings.graphql.schema.type.aggregation.QLIdOperator;
import software.wings.graphql.schema.type.aggregation.QLNoOpSortCriteria;
import software.wings.graphql.schema.type.aggregation.environment.QLEnvironmentFilter;
import software.wings.graphql.utils.nameservice.NameService;
import software.wings.security.PermissionAttribute.Action;
import software.wings.security.PermissionAttribute.PermissionType;
import software.wings.security.annotations.AuthRule;

import com.google.inject.Inject;
import dev.morphia.query.Query;
import dev.morphia.query.Sort;
import graphql.schema.DataFetchingEnvironment;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(CDC)
@TargetModule(HarnessModule._380_CG_GRAPHQL)
public class EnvironmentConnectionDataFetcher
    extends AbstractConnectionV2DataFetcher<QLEnvironmentFilter, QLNoOpSortCriteria, QLEnvironmentConnection> {
  @Inject EnvironmentQueryHelper environmentQueryHelper;

  @Override
  @AuthRule(permissionType = PermissionType.ENV, action = Action.READ)
  public QLEnvironmentConnection fetchConnection(List<QLEnvironmentFilter> filters,
      QLPageQueryParameters pageQueryParameters, List<QLNoOpSortCriteria> sortCriteria) {
    Query<Environment> query = populateFilters(wingsPersistence, filters, Environment.class, true)
                                   .order(Sort.descending(EnvironmentKeys.createdAt));

    QLEnvironmentConnectionBuilder connectionBuilder = QLEnvironmentConnection.builder();
    connectionBuilder.pageInfo(utils.populate(pageQueryParameters, query, environment -> {
      QLEnvironmentBuilder builder = QLEnvironment.builder();
      EnvironmentController.populateEnvironment(environment, builder);
      connectionBuilder.node(builder.build());
    }));
    return connectionBuilder.build();
  }

  @Override
  protected void populateFilters(List<QLEnvironmentFilter> filters, Query query) {
    environmentQueryHelper.setQuery(filters, query, getAccountId());
  }

  @Override
  protected QLEnvironmentFilter generateFilter(DataFetchingEnvironment environment, String key, String value) {
    QLIdFilter idFilter = QLIdFilter.builder()
                              .operator(QLIdOperator.EQUALS)
                              .values(new String[] {(String) utils.getFieldValue(environment.getSource(), value)})
                              .build();
    if (application.equals(key)) {
      return QLEnvironmentFilter.builder().application(idFilter).build();
    } else if (NameService.environment.equals(key)) {
      return QLEnvironmentFilter.builder().environment(idFilter).build();
    }
    throw new WingsException("Unsupported field " + key + " while generating filter");
  }
}
