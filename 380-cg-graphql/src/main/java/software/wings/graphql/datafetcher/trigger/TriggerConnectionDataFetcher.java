/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package software.wings.graphql.datafetcher.trigger;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import static software.wings.graphql.utils.nameservice.NameService.application;
import static software.wings.graphql.utils.nameservice.NameService.trigger;
import static software.wings.security.PermissionAttribute.Action.READ;
import static software.wings.security.PermissionAttribute.PermissionType.LOGGED_IN;

import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.WingsException;

import software.wings.beans.trigger.Trigger;
import software.wings.beans.trigger.Trigger.TriggerKeys;
import software.wings.graphql.datafetcher.AbstractConnectionV2DataFetcher;
import software.wings.graphql.schema.query.QLPageQueryParameters;
import software.wings.graphql.schema.type.aggregation.QLIdFilter;
import software.wings.graphql.schema.type.aggregation.QLIdOperator;
import software.wings.graphql.schema.type.aggregation.QLNoOpSortCriteria;
import software.wings.graphql.schema.type.aggregation.trigger.QLTriggerFilter;
import software.wings.graphql.schema.type.trigger.QLTrigger;
import software.wings.graphql.schema.type.trigger.QLTrigger.QLTriggerBuilder;
import software.wings.graphql.schema.type.trigger.QLTriggerConnection;
import software.wings.graphql.schema.type.trigger.QLTriggerConnection.QLTriggerConnectionBuilder;
import software.wings.security.annotations.AuthRule;
import software.wings.service.intfc.AppService;

import com.google.inject.Inject;
import dev.morphia.query.Query;
import dev.morphia.query.Sort;
import graphql.schema.DataFetchingEnvironment;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(CDC)
@Slf4j
public class TriggerConnectionDataFetcher
    extends AbstractConnectionV2DataFetcher<QLTriggerFilter, QLNoOpSortCriteria, QLTriggerConnection> {
  @Inject TriggerQueryHelper triggerQueryHelper;
  @Inject AppService appService;
  @Inject TriggerController triggerController;

  @Override
  @AuthRule(permissionType = LOGGED_IN, action = READ)
  protected QLTriggerConnection fetchConnection(List<QLTriggerFilter> triggerFilters,
      QLPageQueryParameters pageQueryParameters, List<QLNoOpSortCriteria> sortCriteria) {
    Query<Trigger> query = populateFilters(wingsPersistence, triggerFilters, Trigger.class, false);
    query.order(Sort.descending(TriggerKeys.createdAt));

    QLTriggerConnectionBuilder qlTriggerConnectionBuilder = QLTriggerConnection.builder();
    qlTriggerConnectionBuilder.pageInfo(utils.populate(pageQueryParameters, query, trigger -> {
      QLTriggerBuilder builder = QLTrigger.builder();
      triggerController.populateTrigger(trigger, builder, appService.getAccountIdByAppId(trigger.getAppId()));
      qlTriggerConnectionBuilder.node(builder.build());
    }));

    return qlTriggerConnectionBuilder.build();
  }

  @Override
  protected void populateFilters(List<QLTriggerFilter> filters, Query query) {
    triggerQueryHelper.setQuery(filters, query, getAccountId());
  }

  @Override
  protected QLTriggerFilter generateFilter(DataFetchingEnvironment environment, String key, String value) {
    QLIdFilter idFilter = QLIdFilter.builder()
                              .operator(QLIdOperator.EQUALS)
                              .values(new String[] {(String) utils.getFieldValue(environment.getSource(), value)})
                              .build();
    if (application.equals(key)) {
      return QLTriggerFilter.builder().application(idFilter).build();
    } else if (trigger.equals(key)) {
      return QLTriggerFilter.builder().trigger(idFilter).build();
    }
    throw new WingsException("Unsupported field " + key + " while generating filter");
  }
}
