/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.graphql.datafetcher.service;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import static software.wings.graphql.utils.nameservice.NameService.application;
import static software.wings.graphql.utils.nameservice.NameService.service;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.exception.WingsException;

import software.wings.beans.Service;
import software.wings.beans.Service.ServiceKeys;
import software.wings.graphql.datafetcher.AbstractConnectionV2DataFetcher;
import software.wings.graphql.schema.query.QLPageQueryParameters;
import software.wings.graphql.schema.type.QLService;
import software.wings.graphql.schema.type.QLService.QLServiceBuilder;
import software.wings.graphql.schema.type.QLServiceConnection;
import software.wings.graphql.schema.type.QLServiceConnection.QLServiceConnectionBuilder;
import software.wings.graphql.schema.type.aggregation.QLIdFilter;
import software.wings.graphql.schema.type.aggregation.QLIdOperator;
import software.wings.graphql.schema.type.aggregation.QLNoOpSortCriteria;
import software.wings.graphql.schema.type.aggregation.service.QLServiceFilter;
import software.wings.security.PermissionAttribute.Action;
import software.wings.security.PermissionAttribute.PermissionType;
import software.wings.security.annotations.AuthRule;

import com.google.inject.Inject;
import dev.morphia.query.Query;
import dev.morphia.query.Sort;
import graphql.schema.DataFetchingEnvironment;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(CDC)
@Slf4j
@TargetModule(HarnessModule._380_CG_GRAPHQL)
public class ServiceConnectionDataFetcher
    extends AbstractConnectionV2DataFetcher<QLServiceFilter, QLNoOpSortCriteria, QLServiceConnection> {
  @Inject ServiceQueryHelper serviceQueryHelper;

  @Override
  @AuthRule(permissionType = PermissionType.SERVICE, action = Action.READ)
  protected QLServiceConnection fetchConnection(List<QLServiceFilter> serviceFilters,
      QLPageQueryParameters pageQueryParameters, List<QLNoOpSortCriteria> sortCriteria) {
    Query<Service> query = populateFilters(wingsPersistence, serviceFilters, Service.class, true)
                               .order(Sort.descending(ServiceKeys.createdAt));

    QLServiceConnectionBuilder qlServiceConnectionBuilder = QLServiceConnection.builder();
    qlServiceConnectionBuilder.pageInfo(utils.populate(pageQueryParameters, query, service -> {
      QLServiceBuilder builder = QLService.builder();
      ServiceController.populateService(service, builder);
      qlServiceConnectionBuilder.node(builder.build());
    }));

    return qlServiceConnectionBuilder.build();
  }

  @Override
  protected void populateFilters(List<QLServiceFilter> filters, Query query) {
    serviceQueryHelper.setQuery(filters, query, getAccountId());
  }

  @Override
  protected QLServiceFilter generateFilter(DataFetchingEnvironment environment, String key, String value) {
    QLIdFilter idFilter = QLIdFilter.builder()
                              .operator(QLIdOperator.EQUALS)
                              .values(new String[] {(String) utils.getFieldValue(environment.getSource(), value)})
                              .build();
    if (application.equals(key)) {
      return QLServiceFilter.builder().application(idFilter).build();
    } else if (service.equals(key)) {
      return QLServiceFilter.builder().service(idFilter).build();
    }
    throw new WingsException("Unsupported field " + key + " while generating filter");
  }
}
