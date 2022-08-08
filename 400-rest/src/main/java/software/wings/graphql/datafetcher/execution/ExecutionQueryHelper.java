/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.graphql.datafetcher.execution;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;

import software.wings.beans.EntityType;
import software.wings.beans.WorkflowExecution;
import software.wings.beans.WorkflowExecution.WorkflowExecutionKeys;
import software.wings.graphql.datafetcher.DataFetcherUtils;
import software.wings.graphql.datafetcher.tag.TagHelper;
import software.wings.graphql.schema.type.aggregation.QLIdFilter;
import software.wings.graphql.schema.type.aggregation.QLNumberFilter;
import software.wings.graphql.schema.type.aggregation.QLTimeFilter;
import software.wings.graphql.schema.type.aggregation.deployment.QLDeploymentTagFilter;
import software.wings.graphql.schema.type.aggregation.deployment.QLDeploymentTagType;
import software.wings.graphql.schema.type.aggregation.environment.QLEnvironmentTypeFilter;
import software.wings.graphql.schema.type.aggregation.tag.QLTagInput;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.mongodb.morphia.query.FieldEnd;
import org.mongodb.morphia.query.Query;

@OwnedBy(CDC)
@Singleton
@Slf4j
@TargetModule(HarnessModule._380_CG_GRAPHQL)
public class ExecutionQueryHelper {
  @Inject protected DataFetcherUtils utils;
  @Inject protected TagHelper tagHelper;

  public void setBaseQuery(List<QLBaseExecutionFilter> filters, Query query, String accountId) {
    if (isEmpty(filters)) {
      return;
    }

    filters.forEach(filter -> {
      FieldEnd<? extends Query<WorkflowExecution>> field;

      if (filter.getExecution() != null) {
        field = query.field(WorkflowExecutionKeys.uuid);
        QLIdFilter idFilter = filter.getExecution();
        utils.setIdFilter(field, idFilter);
      }

      if (filter.getApplication() != null) {
        field = query.field(WorkflowExecutionKeys.appId);
        QLIdFilter idFilter = filter.getApplication();
        utils.setIdFilter(field, idFilter);
      }

      if (filter.getCloudProvider() != null) {
        field = query.field(WorkflowExecutionKeys.deployedCloudProviders);
        QLIdFilter idFilter = filter.getCloudProvider();
        utils.setIdFilter(field, idFilter);
      }

      if (filter.getDuration() != null) {
        field = query.field(WorkflowExecutionKeys.duration);
        QLNumberFilter numberFilter = filter.getDuration();
        utils.setNumberFilter(field, numberFilter);
      }

      if (filter.getEndTime() != null) {
        field = query.field(WorkflowExecutionKeys.endTs);
        QLTimeFilter timeFilter = filter.getEndTime();
        utils.setTimeFilter(field, timeFilter);
      }

      if (filter.getEnvironment() != null) {
        field = query.field(WorkflowExecutionKeys.deployedEnvironments + ".uuid");
        QLIdFilter idFilter = filter.getEnvironment();
        utils.setIdFilter(field, idFilter);
      }

      if (filter.getPipeline() != null) {
        field = query.field(WorkflowExecutionKeys.workflowId);
        QLIdFilter idFilter = filter.getPipeline();
        utils.setIdFilter(field, idFilter);
      }

      if (filter.getWorkflow() != null) {
        field = query.field(WorkflowExecutionKeys.workflowId);
        QLIdFilter idFilter = filter.getWorkflow();
        utils.setIdFilter(field, idFilter);
      }

      if (filter.getService() != null) {
        field = query.field(WorkflowExecutionKeys.deployedServices);
        QLIdFilter idFilter = filter.getService();
        utils.setIdFilter(field, idFilter);
      }

      if (filter.getStartTime() != null) {
        field = query.field(WorkflowExecutionKeys.startTs);
        QLTimeFilter timeFilter = filter.getStartTime();
        utils.setTimeFilter(field, timeFilter);
      }

      if (filter.getStatus() != null) {
        field = query.field(WorkflowExecutionKeys.status);
        QLIdFilter idFilter = filter.getStatus();
        utils.setIdFilter(field, idFilter);
      }

      if (filter.getTrigger() != null) {
        field = query.field(WorkflowExecutionKeys.deploymentTriggerId);
        QLIdFilter idFilter = filter.getTrigger();
        utils.setIdFilter(field, idFilter);
      }

      if (filter.getTriggeredBy() != null) {
        field = query.field(WorkflowExecutionKeys.triggeredByID);
        QLIdFilter idFilter = filter.getTriggeredBy();
        utils.setIdFilter(field, idFilter);
      }

      if (filter.getCreationTime() != null) {
        field = query.field(WorkflowExecutionKeys.createdAt);
        QLTimeFilter timeFilter = filter.getCreationTime();
        utils.setTimeFilter(field, timeFilter);
      }

      if (filter.getApplicationId() != null) {
        field = query.field(WorkflowExecutionKeys.appId);
        field.equal(filter.getApplicationId());
      }

      if (filter.getTag() != null) {
        QLDeploymentTagFilter tagFilter = filter.getTag();
        List<QLTagInput> tags = tagFilter.getTags();
        Set<String> entityIds;
        if (QLDeploymentTagType.DEPLOYMENT == tagFilter.getEntityType()) {
          entityIds = tagHelper.getWorkExecutionsWithTags(accountId, tags);
        } else {
          entityIds = tagHelper.getEntityIdsFromTags(accountId, tags, getEntityType(tagFilter.getEntityType()));
        }
        switch (tagFilter.getEntityType()) {
          case APPLICATION:
            query.field(WorkflowExecutionKeys.appId).in(entityIds);
            break;
          case SERVICE:
            query.field(WorkflowExecutionKeys.serviceIds).in(entityIds);
            break;
          case ENVIRONMENT:
            query.field(WorkflowExecutionKeys.envIds).in(entityIds);
            break;
          case DEPLOYMENT:
            query.field(WorkflowExecutionKeys.uuid).in(entityIds);
            break;
          default:
            log.error("EntityType {} not supported in execution query", tagFilter.getEntityType());
            throw new InvalidRequestException("Error while compiling execution query", WingsException.USER);
        }
      }
    });
  }

  public void setQuery(List<QLExecutionFilter> filters, Query query, String accountId) {
    if (isEmpty(filters)) {
      return;
    }
    setBaseQuery(
        filters.stream().map(filter -> (QLBaseExecutionFilter) filter).collect(Collectors.toList()), query, accountId);

    filters.forEach(filter -> {
      FieldEnd<? extends Query<WorkflowExecution>> field;

      if (filter.getPipelineExecutionId() != null) {
        field = query.field(WorkflowExecutionKeys.pipelineExecutionId);
        QLIdFilter idFilter = filter.getPipelineExecutionId();
        utils.setIdFilter(field, idFilter);
      }

      if (filter.getEnvironmentType() != null) {
        field = query.field(WorkflowExecutionKeys.envType);
        QLEnvironmentTypeFilter envTypeFilter = filter.getEnvironmentType();
        utils.setEnumFilter(field, envTypeFilter);
      }
    });
  }

  public EntityType getEntityType(QLDeploymentTagType entityType) {
    if (entityType == null) {
      throw new InvalidRequestException("Please provide entity type");
    }
    switch (entityType) {
      case APPLICATION:
        return EntityType.APPLICATION;
      case SERVICE:
        return EntityType.SERVICE;
      case ENVIRONMENT:
        return EntityType.ENVIRONMENT;
      case DEPLOYMENT:
        return EntityType.DEPLOYMENT;
      default:
        log.error("Unsupported entity type {} for tag ", entityType);
        throw new InvalidRequestException("Unsupported entity type " + entityType);
    }
  }
}
