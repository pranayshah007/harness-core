/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.graphql.datafetcher.application;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.exception.InvalidRequestException;

import software.wings.beans.Application;
import software.wings.beans.EntityType;
import software.wings.graphql.datafetcher.RealTimeStatsDataFetcherWithTags;
import software.wings.graphql.schema.type.aggregation.QLData;
import software.wings.graphql.schema.type.aggregation.QLNoOpAggregateFunction;
import software.wings.graphql.schema.type.aggregation.QLNoOpSortCriteria;
import software.wings.graphql.schema.type.aggregation.application.QLApplicationAggregation;
import software.wings.graphql.schema.type.aggregation.application.QLApplicationEntityAggregation;
import software.wings.graphql.schema.type.aggregation.application.QLApplicationFilter;
import software.wings.graphql.schema.type.aggregation.application.QLApplicationTagAggregation;
import software.wings.graphql.schema.type.aggregation.application.QLApplicationTagType;
import software.wings.graphql.utils.nameservice.NameService;

import com.google.inject.Inject;
import dev.morphia.query.Query;
import graphql.schema.DataFetchingEnvironment;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(CDC)
@TargetModule(HarnessModule._380_CG_GRAPHQL)
public class ApplicationStatsDataFetcher
    extends RealTimeStatsDataFetcherWithTags<QLNoOpAggregateFunction, QLApplicationFilter, QLApplicationAggregation,
        QLNoOpSortCriteria, QLApplicationTagType, QLApplicationTagAggregation, QLApplicationEntityAggregation> {
  @Inject ApplicationQueryHelper applicationQueryHelper;

  @Override
  protected QLData fetch(String accountId, QLNoOpAggregateFunction aggregateFunction, List<QLApplicationFilter> filters,
      List<QLApplicationAggregation> groupByList, List<QLNoOpSortCriteria> sortCriteria,
      DataFetchingEnvironment dataFetchingEnvironment) {
    final Class entityClass = Application.class;
    final List<String> groupByEntityList = new ArrayList<>();
    if (isNotEmpty(groupByList)) {
      groupByList.forEach(groupBy -> {
        if (groupBy.getEntityAggregation() != null) {
          groupByEntityList.add(groupBy.getEntityAggregation().name());
        }

        if (groupBy.getTagAggregation() != null) {
          QLApplicationEntityAggregation groupByEntityFromTag = getGroupByEntityFromTag(groupBy.getTagAggregation());
          if (groupByEntityFromTag != null) {
            groupByEntityList.add(groupByEntityFromTag.name());
          }
        }
      });
    }
    return getQLData(accountId, filters, entityClass, groupByEntityList);
  }

  @Override
  public String getAggregationFieldName(String aggregation) {
    QLApplicationEntityAggregation qlApplicationEntityAggregation = QLApplicationEntityAggregation.valueOf(aggregation);
    switch (qlApplicationEntityAggregation) {
      case Application:
        return "appId";
      default:
        log.warn("Unknown aggregation type" + aggregation);
        throw new InvalidRequestException(GENERIC_EXCEPTION_MSG);
    }
  }

  @Override
  public void populateFilters(String accountId, List<QLApplicationFilter> filters, Query query) {
    // do nothing
  }

  @Override
  public String getEntityType() {
    return NameService.application;
  }

  @Override
  protected QLApplicationTagAggregation getTagAggregation(QLApplicationAggregation groupBy) {
    return groupBy.getTagAggregation();
  }

  @Override
  protected EntityType getEntityType(QLApplicationTagType entityType) {
    return applicationQueryHelper.getEntityType(entityType);
  }

  @Override
  protected QLApplicationEntityAggregation getEntityAggregation(QLApplicationAggregation groupBy) {
    return groupBy.getEntityAggregation();
  }

  @Override
  protected QLApplicationEntityAggregation getGroupByEntityFromTag(QLApplicationTagAggregation groupByTag) {
    switch (groupByTag.getEntityType()) {
      case APPLICATION:
        return QLApplicationEntityAggregation.Application;
      default:
        log.warn("Unsupported tag entity type {}", groupByTag.getEntityType());
        throw new InvalidRequestException(GENERIC_EXCEPTION_MSG);
    }
  }
}
