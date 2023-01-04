/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.graphql.datafetcher.service;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.exception.InvalidRequestException;

import software.wings.beans.EntityType;
import software.wings.beans.Service;
import software.wings.graphql.datafetcher.RealTimeStatsDataFetcherWithTags;
import software.wings.graphql.schema.type.aggregation.QLData;
import software.wings.graphql.schema.type.aggregation.QLNoOpAggregateFunction;
import software.wings.graphql.schema.type.aggregation.QLNoOpSortCriteria;
import software.wings.graphql.schema.type.aggregation.service.QLServiceAggregation;
import software.wings.graphql.schema.type.aggregation.service.QLServiceEntityAggregation;
import software.wings.graphql.schema.type.aggregation.service.QLServiceFilter;
import software.wings.graphql.schema.type.aggregation.service.QLServiceTagAggregation;
import software.wings.graphql.schema.type.aggregation.service.QLServiceTagType;
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
public class ServiceStatsDataFetcher extends RealTimeStatsDataFetcherWithTags<QLNoOpAggregateFunction, QLServiceFilter,
    QLServiceAggregation, QLNoOpSortCriteria, QLServiceTagType, QLServiceTagAggregation, QLServiceEntityAggregation> {
  @Inject ServiceQueryHelper serviceQueryHelper;

  @Override
  protected QLData fetch(String accountId, QLNoOpAggregateFunction aggregateFunction, List<QLServiceFilter> filters,
      List<QLServiceAggregation> groupByList, List<QLNoOpSortCriteria> sortCriteria,
      DataFetchingEnvironment dataFetchingEnvironment) {
    final Class entityClass = Service.class;
    final List<String> groupByEntityList = new ArrayList<>();
    if (isNotEmpty(groupByList)) {
      groupByList.forEach(groupBy -> {
        if (groupBy.getEntityAggregation() != null) {
          groupByEntityList.add(groupBy.getEntityAggregation().name());
        }

        if (groupBy.getTagAggregation() != null) {
          QLServiceEntityAggregation groupByEntityFromTag = getGroupByEntityFromTag(groupBy.getTagAggregation());
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
    QLServiceEntityAggregation serviceAggregation = QLServiceEntityAggregation.valueOf(aggregation);
    switch (serviceAggregation) {
      case Application:
        return "appId";
      case ArtifactType:
        return "artifactType";
      case DeploymentType:
        return "deploymentType";
      case Service:
        return "_id";
      default:
        log.warn("Unknown aggregation type" + aggregation);
        throw new InvalidRequestException(GENERIC_EXCEPTION_MSG);
    }
  }

  @Override
  public void populateFilters(String accountId, List<QLServiceFilter> filters, Query query) {
    serviceQueryHelper.setQuery(filters, query, accountId);
  }

  @Override
  public String getEntityType() {
    return NameService.service;
  }

  @Override
  protected QLServiceTagAggregation getTagAggregation(QLServiceAggregation groupBy) {
    return groupBy.getTagAggregation();
  }

  @Override
  protected EntityType getEntityType(QLServiceTagType entityType) {
    return serviceQueryHelper.getEntityType(entityType);
  }

  @Override
  protected QLServiceEntityAggregation getEntityAggregation(QLServiceAggregation groupBy) {
    return groupBy.getEntityAggregation();
  }

  @Override
  protected QLServiceEntityAggregation getGroupByEntityFromTag(QLServiceTagAggregation groupByTag) {
    switch (groupByTag.getEntityType()) {
      case APPLICATION:
        return QLServiceEntityAggregation.Application;
      case SERVICE:
        return QLServiceEntityAggregation.Service;
      default:
        log.warn("Unsupported tag entity type {}", groupByTag.getEntityType());
        throw new InvalidRequestException(GENERIC_EXCEPTION_MSG);
    }
  }
}
