/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.graphql.datafetcher.instance;

import static io.harness.annotations.dev.HarnessTeam.DX;
import static io.harness.data.structure.EmptyPredicate.isEmpty;

import static software.wings.security.PermissionAttribute.PermissionType.LOGGED_IN;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.exception.WingsException;

import software.wings.beans.infrastructure.instance.Instance;
import software.wings.beans.infrastructure.instance.Instance.InstanceKeys;
import software.wings.graphql.datafetcher.AbstractConnectionV2DataFetcher;
import software.wings.graphql.schema.query.QLPageQueryParameters;
import software.wings.graphql.schema.type.QLInstanceConnection;
import software.wings.graphql.schema.type.QLInstanceConnection.QLInstanceConnectionBuilder;
import software.wings.graphql.schema.type.aggregation.QLIdFilter;
import software.wings.graphql.schema.type.aggregation.QLIdOperator;
import software.wings.graphql.schema.type.aggregation.QLNoOpSortCriteria;
import software.wings.graphql.schema.type.aggregation.instance.QLInstanceFilter;
import software.wings.graphql.schema.type.instance.QLInstance;
import software.wings.graphql.utils.nameservice.NameService;
import software.wings.security.annotations.AuthRule;

import com.google.inject.Inject;
import dev.morphia.query.Query;
import dev.morphia.query.Sort;
import graphql.schema.DataFetchingEnvironment;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(DX)
@Slf4j
@TargetModule(HarnessModule._380_CG_GRAPHQL)
public class InstanceConnectionDataFetcher
    extends AbstractConnectionV2DataFetcher<QLInstanceFilter, QLNoOpSortCriteria, QLInstanceConnection> {
  @Inject private InstanceControllerManager instanceControllerManager;
  @Inject private InstanceQueryHelper instanceMongoHelper;

  @Override
  @AuthRule(permissionType = LOGGED_IN)
  protected QLInstanceConnection fetchConnection(List<QLInstanceFilter> filters,
      QLPageQueryParameters pageQueryParameters, List<QLNoOpSortCriteria> sortCriteria) {
    Query<Instance> query = populateFilters(wingsPersistence, filters, Instance.class, true)
                                .filter(InstanceKeys.isDeleted, false)
                                .order(Sort.descending(InstanceKeys.lastDeployedAt));

    QLInstanceConnectionBuilder connectionBuilder = QLInstanceConnection.builder();
    connectionBuilder.pageInfo(utils.populate(pageQueryParameters, query, instance -> {
      QLInstance qlInstance = instanceControllerManager.getQLInstance(instance);
      connectionBuilder.node(qlInstance);
    }));

    return connectionBuilder.build();
  }

  @Override
  protected void populateFilters(List<QLInstanceFilter> filters, Query query) {
    if (isEmpty(filters)) {
      return;
    }
    instanceMongoHelper.setQuery(getAccountId(), filters, query);
  }

  @Override
  protected QLInstanceFilter generateFilter(DataFetchingEnvironment environment, String key, String value) {
    QLIdFilter idFilter = QLIdFilter.builder()
                              .operator(QLIdOperator.EQUALS)
                              .values(new String[] {(String) utils.getFieldValue(environment.getSource(), value)})
                              .build();
    if (NameService.application.equals(key)) {
      return QLInstanceFilter.builder().application(idFilter).build();
    } else if (NameService.service.equals(key)) {
      return QLInstanceFilter.builder().service(idFilter).build();
    } else if (NameService.environment.equals(key)) {
      return QLInstanceFilter.builder().environment(idFilter).build();
    } else if (NameService.cloudProvider.equals(key)) {
      return QLInstanceFilter.builder().cloudProvider(idFilter).build();
    }
    throw new WingsException("Unsupported field " + key + " while generating filter");
  }
}
