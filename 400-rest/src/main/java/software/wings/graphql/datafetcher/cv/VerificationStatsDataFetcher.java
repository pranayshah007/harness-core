/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.graphql.datafetcher.cv;

import static io.harness.annotations.dev.HarnessTeam.CV;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.cv.WorkflowVerificationResult;
import io.harness.cv.WorkflowVerificationResult.WorkflowVerificationResultKeys;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;

import software.wings.beans.EntityType;
import software.wings.graphql.datafetcher.RealTimeStatsDataFetcherWithTags;
import software.wings.graphql.datafetcher.tag.TagHelper;
import software.wings.graphql.schema.type.aggregation.QLData;
import software.wings.graphql.schema.type.aggregation.QLIdFilter;
import software.wings.graphql.schema.type.aggregation.QLNoOpAggregateFunction;
import software.wings.graphql.schema.type.aggregation.QLNoOpSortCriteria;
import software.wings.graphql.schema.type.aggregation.QLTimeFilter;
import software.wings.graphql.schema.type.aggregation.cv.QLCVEntityAggregation;
import software.wings.graphql.schema.type.aggregation.cv.QLCVTagAggregation;
import software.wings.graphql.schema.type.aggregation.cv.QLCVWorkflowTagFilter;
import software.wings.graphql.schema.type.aggregation.cv.QLCVWorkflowTagType;
import software.wings.graphql.schema.type.aggregation.cv.QLVerificationAggregation;
import software.wings.graphql.schema.type.aggregation.cv.QLVerificationResultFilter;
import software.wings.graphql.schema.type.aggregation.tag.QLTagInput;

import com.google.inject.Inject;
import dev.morphia.query.FieldEnd;
import dev.morphia.query.Query;
import graphql.schema.DataFetchingEnvironment;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(CV)
@TargetModule(HarnessModule._380_CG_GRAPHQL)
public class VerificationStatsDataFetcher
    extends RealTimeStatsDataFetcherWithTags<QLNoOpAggregateFunction, QLVerificationResultFilter,
        QLVerificationAggregation, QLNoOpSortCriteria, QLCVWorkflowTagType, QLCVTagAggregation, QLCVEntityAggregation> {
  @Inject protected TagHelper tagHelper;

  @Override
  protected QLCVTagAggregation getTagAggregation(QLVerificationAggregation groupBy) {
    return groupBy.getTagAggregation();
  }

  @Override
  protected QLCVEntityAggregation getEntityAggregation(QLVerificationAggregation groupBy) {
    return groupBy.getEntityAggregation();
  }

  @Override
  public String getEntityType() {
    return null;
  }

  @Override
  protected QLCVEntityAggregation getGroupByEntityFromTag(QLCVTagAggregation groupByTag) {
    switch (groupByTag.getEntityType()) {
      case SERVICE:
        return QLCVEntityAggregation.Service;
      case APPLICATION:
        return QLCVEntityAggregation.Application;
      default:
        log.warn("Unsupported tag entity type {}", groupByTag.getEntityType());
        throw new InvalidRequestException(GENERIC_EXCEPTION_MSG);
    }
  }

  @Override
  protected QLData fetch(String accountId, QLNoOpAggregateFunction aggregateFunction,
      List<QLVerificationResultFilter> filters, List<QLVerificationAggregation> groupByList,
      List<QLNoOpSortCriteria> sort, DataFetchingEnvironment dataFetchingEnvironment) {
    final Class entityClass = io.harness.cv.WorkflowVerificationResult.class;
    final List<String> groupByEntityList = new ArrayList<>();
    if (isNotEmpty(groupByList)) {
      groupByList.forEach(groupBy -> {
        if (groupBy.getEntityAggregation() != null) {
          groupByEntityList.add(groupBy.getEntityAggregation().name());
        }

        if (groupBy.getTagAggregation() != null) {
          QLCVEntityAggregation groupByEntityFromTag = getGroupByEntityFromTag(groupBy.getTagAggregation());
          if (groupByEntityFromTag != null) {
            groupByEntityList.add(groupByEntityFromTag.name());
          }
        }
      });
    }
    return getQLData(accountId, filters, entityClass, groupByEntityList);
  }

  @Override
  public void populateFilters(String accountId, List<QLVerificationResultFilter> filters, Query query) {
    if (isEmpty(filters)) {
      return;
    }

    filters.forEach(filter -> {
      FieldEnd<? extends Query<WorkflowVerificationResult>> field;
      if (filter.getApplication() != null) {
        field = query.field(WorkflowVerificationResultKeys.appId);
        QLIdFilter applicationFilter = filter.getApplication();
        utils.setIdFilter(field, applicationFilter);
      }

      if (filter.getService() != null) {
        field = query.field(WorkflowVerificationResultKeys.serviceId);
        QLIdFilter serviceFilter = filter.getService();
        utils.setIdFilter(field, serviceFilter);
      }

      if (filter.getEnvironment() != null) {
        field = query.field(WorkflowVerificationResultKeys.envId);
        QLIdFilter envFilter = filter.getEnvironment();
        utils.setIdFilter(field, envFilter);
      }

      if (filter.getEndTime() != null) {
        field = query.field(WorkflowVerificationResultKeys.lastUpdatedAt);
        QLTimeFilter timeFilter = filter.getEndTime();
        utils.setTimeFilter(field, timeFilter);
      }

      if (filter.getStartTime() != null) {
        field = query.field(WorkflowVerificationResultKeys.createdAt);
        QLTimeFilter timeFilter = filter.getStartTime();
        utils.setTimeFilter(field, timeFilter);
      }

      if (filter.getRollback() != null && filter.getRollback()) {
        query.filter(WorkflowVerificationResultKeys.rollback, true);
      }

      if (filter.getTag() != null) {
        QLCVWorkflowTagFilter tagFilter = filter.getTag();
        List<QLTagInput> tags = tagFilter.getTags();
        Set<String> entityIds =
            tagHelper.getEntityIdsFromTags(accountId, tags, getEntityType(tagFilter.getEntityType()));
        switch (tagFilter.getEntityType()) {
          case APPLICATION:
            query.field("appId").in(entityIds);
            break;
          case SERVICE:
            query.field("serviceId").in(entityIds);
            break;
          default:
            log.error("EntityType {} not supported in query", tagFilter.getEntityType());
            throw new InvalidRequestException("Error while compiling query", WingsException.USER);
        }
      }

      if (filter.getType() != null) {
        field = query.field(WorkflowVerificationResultKeys.stateType);
        utils.setEnumFilter(field, filter.getType());
      }
    });
  }

  @Override
  public String getAggregationFieldName(String aggregation) {
    QLCVEntityAggregation qlcvEntityAggregation = QLCVEntityAggregation.valueOf(aggregation);
    switch (qlcvEntityAggregation) {
      case Application:
        return "appId";
      case Service:
        return "serviceId";
      default:
        throw new InvalidRequestException("Unknown aggregation type" + aggregation);
    }
  }

  @Override
  public EntityType getEntityType(QLCVWorkflowTagType cvWorkflowTagType) {
    switch (cvWorkflowTagType) {
      case APPLICATION:
        return EntityType.APPLICATION;
      case SERVICE:
        return EntityType.SERVICE;
      case ENVIRONMENT:
        return EntityType.ENVIRONMENT;
      default:
        log.error("Unsupported entity type {} for tag ", cvWorkflowTagType);
        throw new InvalidRequestException("Unsupported entity type " + cvWorkflowTagType);
    }
  }
}
