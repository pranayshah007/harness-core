/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package software.wings.graphql.datafetcher.trigger;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.InvalidRequestException;

import software.wings.beans.EntityType;
import software.wings.beans.trigger.Trigger;
import software.wings.beans.trigger.Trigger.TriggerKeys;
import software.wings.dl.WingsPersistence;
import software.wings.graphql.datafetcher.DataFetcherUtils;
import software.wings.graphql.datafetcher.RealTimeStatsDataFetcherWithTags;
import software.wings.graphql.schema.type.aggregation.QLData;
import software.wings.graphql.schema.type.aggregation.QLNoOpAggregateFunction;
import software.wings.graphql.schema.type.aggregation.QLNoOpSortCriteria;
import software.wings.graphql.schema.type.aggregation.trigger.QLTriggerAggregation;
import software.wings.graphql.schema.type.aggregation.trigger.QLTriggerEntityAggregation;
import software.wings.graphql.schema.type.aggregation.trigger.QLTriggerFilter;
import software.wings.graphql.schema.type.aggregation.trigger.QLTriggerTagAggregation;
import software.wings.graphql.schema.type.aggregation.trigger.QLTriggerTagType;
import software.wings.graphql.utils.nameservice.NameService;
import software.wings.service.intfc.AppService;

import com.google.inject.Inject;
import dev.morphia.query.Query;
import graphql.schema.DataFetchingEnvironment;
import java.util.ArrayList;
import java.util.List;
import javax.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(CDC)
public class TriggerStatsDataFetcher extends RealTimeStatsDataFetcherWithTags<QLNoOpAggregateFunction, QLTriggerFilter,
    QLTriggerAggregation, QLNoOpSortCriteria, QLTriggerTagType, QLTriggerTagAggregation, QLTriggerEntityAggregation> {
  @Inject private AppService appService;
  @Inject TriggerQueryHelper triggerQueryHelper;

  @Override
  protected QLTriggerTagAggregation getTagAggregation(QLTriggerAggregation groupBy) {
    return groupBy.getTagAggregation();
  }

  @Override
  protected EntityType getEntityType(QLTriggerTagType entityType) {
    return triggerQueryHelper.getEntityType(entityType);
  }

  @Override
  protected QLData fetch(String accountId, QLNoOpAggregateFunction aggregateFunction, List<QLTriggerFilter> filters,
      List<QLTriggerAggregation> groupByList, List<QLNoOpSortCriteria> sortCriteria,
      DataFetchingEnvironment dataFetchingEnvironment) {
    final Class entityClass = Trigger.class;
    final List<String> groupByEntityList = new ArrayList<>();
    if (isNotEmpty(groupByList)) {
      groupByList.forEach(groupBy -> {
        if (groupBy.getEntityAggregation() != null) {
          groupByEntityList.add(groupBy.getEntityAggregation().name());
        }

        if (groupBy.getTagAggregation() != null) {
          QLTriggerEntityAggregation groupByEntityFromTag = getGroupByEntityFromTag(groupBy.getTagAggregation());
          if (groupByEntityFromTag != null) {
            groupByEntityList.add(groupByEntityFromTag.name());
          }
        }
      });
    }
    return getQLData(accountId, filters, entityClass, groupByEntityList);
  }

  private void populateAppIdFilter(String accountId, Query query) {
    List<String> appIds = appService.getAppIdsByAccountId(accountId);
    query.field(TriggerKeys.appId).in(appIds);
  }

  @Override
  @NotNull
  public Query populateFilters(DataFetcherUtils utils, WingsPersistence wingsPersistence, String accountId,
      List<QLTriggerFilter> filters, Class entityClass) {
    Query query = wingsPersistence.createQuery(entityClass);
    populateFilters(accountId, filters, query);
    populateAppIdFilter(accountId, query);
    return query;
  }

  @Override
  public String getAggregationFieldName(String aggregation) {
    QLTriggerEntityAggregation triggerAggregation = QLTriggerEntityAggregation.valueOf(aggregation);
    switch (triggerAggregation) {
      case Application:
        return "appId";
      default:
        log.warn("Unknown aggregation type" + aggregation);
        throw new InvalidRequestException(GENERIC_EXCEPTION_MSG);
    }
  }

  @Override
  public void populateFilters(String accountId, List<QLTriggerFilter> filters, Query query) {
    triggerQueryHelper.setQuery(filters, query, accountId);
  }

  @Override
  public String getEntityType() {
    return NameService.trigger;
  }

  @Override
  protected QLTriggerEntityAggregation getEntityAggregation(QLTriggerAggregation groupBy) {
    return groupBy.getEntityAggregation();
  }

  @Override
  protected QLTriggerEntityAggregation getGroupByEntityFromTag(QLTriggerTagAggregation groupByTag) {
    switch (groupByTag.getEntityType()) {
      case APPLICATION:
        return QLTriggerEntityAggregation.Application;
      default:
        log.warn("Unsupported tag entity type {}", groupByTag.getEntityType());
        throw new InvalidRequestException(GENERIC_EXCEPTION_MSG);
    }
  }
}
