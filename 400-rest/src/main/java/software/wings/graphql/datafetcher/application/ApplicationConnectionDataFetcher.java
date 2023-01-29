/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.graphql.datafetcher.application;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import static software.wings.graphql.utils.nameservice.NameService.application;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.exception.InvalidRequestException;

import software.wings.beans.Application;
import software.wings.beans.Application.ApplicationKeys;
import software.wings.graphql.datafetcher.AbstractConnectionV2DataFetcher;
import software.wings.graphql.schema.query.QLPageQueryParameters;
import software.wings.graphql.schema.type.QLApplication;
import software.wings.graphql.schema.type.QLApplication.QLApplicationBuilder;
import software.wings.graphql.schema.type.QLApplicationConnection;
import software.wings.graphql.schema.type.QLApplicationConnection.QLApplicationConnectionBuilder;
import software.wings.graphql.schema.type.aggregation.QLIdFilter;
import software.wings.graphql.schema.type.aggregation.QLIdOperator;
import software.wings.graphql.schema.type.aggregation.QLNoOpSortCriteria;
import software.wings.graphql.schema.type.aggregation.application.QLApplicationFilter;
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
public class ApplicationConnectionDataFetcher
    extends AbstractConnectionV2DataFetcher<QLApplicationFilter, QLNoOpSortCriteria, QLApplicationConnection> {
  @Inject ApplicationQueryHelper applicationQueryHelper;

  @Override
  @AuthRule(permissionType = PermissionType.LOGGED_IN)
  public QLApplicationConnection fetchConnection(List<QLApplicationFilter> appFilters,
      QLPageQueryParameters pageQueryParameters, List<QLNoOpSortCriteria> sortCriteria) {
    Query<Application> query = populateFilters(wingsPersistence, appFilters, Application.class, true);
    query.order(Sort.descending(ApplicationKeys.createdAt));

    QLApplicationConnectionBuilder connectionBuilder = QLApplicationConnection.builder();
    connectionBuilder.pageInfo(utils.populate(pageQueryParameters, query, application -> {
      QLApplicationBuilder builder = QLApplication.builder();
      ApplicationController.populateQLApplication(application, builder);
      connectionBuilder.node(builder.build());
    }));
    return connectionBuilder.build();
  }

  @Override
  protected void populateFilters(List<QLApplicationFilter> filters, Query query) {
    applicationQueryHelper.setQuery(filters, query, getAccountId());
  }

  @Override
  protected QLApplicationFilter generateFilter(DataFetchingEnvironment environment, String key, String value) {
    QLIdFilter idFilter = QLIdFilter.builder()
                              .operator(QLIdOperator.EQUALS)
                              .values(new String[] {(String) utils.getFieldValue(environment.getSource(), value)})
                              .build();
    if (application.equals(key)) {
      return QLApplicationFilter.builder().application(idFilter).build();
    }
    throw new InvalidRequestException("Unsupported field " + key + " while generating filter");
  }
}
