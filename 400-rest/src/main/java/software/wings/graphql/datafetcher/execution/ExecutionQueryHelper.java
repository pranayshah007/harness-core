/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.graphql.datafetcher.execution;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.data.structure.EmptyPredicate.isEmpty;

import static software.wings.beans.WorkflowExecution.WFE_EXECUTIONS_SEARCH_ENVIDS;
import static software.wings.beans.WorkflowExecution.WFE_EXECUTIONS_SEARCH_SERVICEIDS;
import static software.wings.beans.WorkflowExecution.WFE_EXECUTIONS_SEARCH_WORKFLOWID;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.FeatureName;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.ff.FeatureFlagService;
import io.harness.mongo.index.BasicDBUtils;
import io.harness.mongo.index.MongoIndex;

import software.wings.beans.Application;
import software.wings.beans.EntityType;
import software.wings.beans.Environment;
import software.wings.beans.Environment.EnvironmentKeys;
import software.wings.beans.Pipeline;
import software.wings.beans.Service;
import software.wings.beans.Service.ServiceKeys;
import software.wings.beans.Workflow;
import software.wings.beans.Workflow.WorkflowKeys;
import software.wings.beans.WorkflowExecution;
import software.wings.beans.WorkflowExecution.WorkflowExecutionKeys;
import software.wings.beans.trigger.Trigger;
import software.wings.beans.trigger.Trigger.TriggerKeys;
import software.wings.dl.WingsMongoPersistence;
import software.wings.graphql.datafetcher.DataFetcherUtils;
import software.wings.graphql.datafetcher.tag.TagHelper;
import software.wings.graphql.schema.type.aggregation.QLIdFilter;
import software.wings.graphql.schema.type.aggregation.QLIdOperator;
import software.wings.graphql.schema.type.aggregation.QLNumberFilter;
import software.wings.graphql.schema.type.aggregation.QLTimeFilter;
import software.wings.graphql.schema.type.aggregation.deployment.QLDeploymentTagFilter;
import software.wings.graphql.schema.type.aggregation.deployment.QLDeploymentTagType;
import software.wings.graphql.schema.type.aggregation.environment.QLEnvironmentTypeFilter;
import software.wings.graphql.schema.type.aggregation.tag.QLTagInput;

import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.mongodb.BasicDBObject;
import dev.morphia.query.FieldEnd;
import dev.morphia.query.Query;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(CDC)
@Singleton
@Slf4j
@TargetModule(HarnessModule._380_CG_GRAPHQL)
public class ExecutionQueryHelper {
  @Inject protected DataFetcherUtils utils;
  @Inject protected TagHelper tagHelper;
  @Inject protected WingsMongoPersistence wingsMongoPersistence;

  @Inject protected FeatureFlagService featureFlagService;

  private static final List<QLIdOperator> optimizableOperators = ImmutableList.of(QLIdOperator.EQUALS, QLIdOperator.IN);

  public static List<String> nonRequiredFields = ImmutableList.of(WorkflowExecutionKeys.pipelineResumeId,
      WorkflowExecutionKeys.message, WorkflowExecutionKeys.name, WorkflowExecutionKeys.appName,
      WorkflowExecutionKeys.stateMachine, WorkflowExecutionKeys.isBaseline, WorkflowExecutionKeys.stageName,
      WorkflowExecutionKeys.cdPageCandidate, WorkflowExecutionKeys.rollbackStartTs, WorkflowExecutionKeys.tags_name,
      WorkflowExecutionKeys.awsLambdaExecutionSummaries, WorkflowExecutionKeys.breakdown,
      WorkflowExecutionKeys.buildExecutionSummaries, WorkflowExecutionKeys.deployedCloudProviders,
      WorkflowExecutionKeys.environments, WorkflowExecutionKeys.helmExecutionSummary,
      WorkflowExecutionKeys.originalExecution, WorkflowExecutionKeys.keywords,
      WorkflowExecutionKeys.statusInstanceBreakdownMap);

  public void setBaseQuery(List<QLBaseExecutionFilter> filters, Query query, String accountId) {
    if (isEmpty(filters)) {
      return;
    }
    Map<Class, QLIdFilter> entityMap = new HashMap<>();

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
        entityMap.put(Application.class, idFilter);
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
        field = query.field(WorkflowExecutionKeys.envIds);
        utils.setIdFilter(field, idFilter);
        entityMap.put(Environment.class, idFilter);
      }

      if (filter.getPipeline() != null) {
        field = query.field(WorkflowExecutionKeys.workflowId);
        QLIdFilter idFilter = filter.getPipeline();
        utils.setIdFilter(field, idFilter);
        entityMap.put(Pipeline.class, idFilter);
      }

      if (filter.getWorkflow() != null) {
        field = query.field(WorkflowExecutionKeys.workflowId);
        QLIdFilter idFilter = filter.getWorkflow();
        utils.setIdFilter(field, idFilter);
        entityMap.put(Workflow.class, idFilter);
      }

      if (filter.getService() != null) {
        field = query.field(WorkflowExecutionKeys.deployedServices);
        QLIdFilter idFilter = filter.getService();
        utils.setIdFilter(field, idFilter);
        field = query.field(WorkflowExecutionKeys.serviceIds);
        utils.setIdFilter(field, idFilter);
        entityMap.put(Service.class, idFilter);
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
        entityMap.put(Trigger.class, idFilter);
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
        entityMap.put(Application.class,
            QLIdFilter.builder()
                .values(new String[] {filter.getApplicationId()})
                .operator(QLIdOperator.EQUALS)
                .build());
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

      if (filter.getArtifactBuildNo() != null) {
        field = query.field(WorkflowExecutionKeys.executionArgs_artifacts_buildNo);
        QLIdFilter artifactBuildNoFilter = filter.getArtifactBuildNo();
        utils.setIdFilter(field, artifactBuildNoFilter);
      }

      if (filter.getHelmChartVersion() != null) {
        field = query.field(WorkflowExecutionKeys.executionArgs_helmCharts_displayName);
        QLIdFilter helmChartVersionFilter = filter.getHelmChartVersion();
        utils.setIdFilter(field, helmChartVersionFilter);
      }
    });

    optimizeQuery(query, entityMap);
  }

  private void optimizeQuery(Query<WorkflowExecution> query, Map<Class, QLIdFilter> entityMap) {
    QLIdFilter appIdFilter = entityMap.get(Application.class);

    if (appIdFilter == null || appIdFilter.getOperator() != QLIdOperator.EQUALS) {
      entityMap.forEach((klass, filter) -> {
        if (!optimizableOperators.contains(filter.getOperator())) {
          return;
        }

        if (klass == Environment.class) {
          optimizeForEnvironment(query, filter);
        } else if (klass == Service.class) {
          optimizeForService(query, filter);
        } else if (klass == Pipeline.class) {
          optimizeForPipeline(query, filter);
        } else if (klass == Workflow.class) {
          optimizeForWorkflow(query, filter);
        } else if (klass == Trigger.class) {
          optimizeForTrigger(query, filter);
        }
      });
    }
  }

  private void optimizeForTrigger(Query<WorkflowExecution> query, QLIdFilter filter) {
    final FieldEnd<? extends Query<WorkflowExecution>> appField = query.field(WorkflowExecutionKeys.appId);
    Set<String> appIds = new HashSet<>();
    Query<Trigger> triggerQuery =
        wingsMongoPersistence.createAnalyticsQuery(Trigger.class).project(TriggerKeys.appId, true);
    final FieldEnd<? extends Query<?>> field = triggerQuery.field(TriggerKeys.uuid);
    utils.setIdFilter(field, filter);
    for (Trigger trigger : triggerQuery.asList()) {
      appIds.add(trigger.getAppId());
    }
    final QLIdFilter newAppFilter =
        QLIdFilter.builder().values(appIds.toArray(new String[0])).operator(filter.getOperator()).build();
    utils.setIdFilter(appField, newAppFilter);
  }

  private void optimizeForPipeline(Query<WorkflowExecution> query, QLIdFilter filter) {
    final FieldEnd<? extends Query<WorkflowExecution>> appField = query.field(WorkflowExecutionKeys.appId);
    Set<String> appIds = new HashSet<>();
    Query<Pipeline> pipelineQuery =
        wingsMongoPersistence.createAnalyticsQuery(Pipeline.class).project(WorkflowKeys.appId, true);
    final FieldEnd<? extends Query<?>> field = pipelineQuery.field(WorkflowKeys.uuid);
    utils.setIdFilter(field, filter);
    for (Pipeline pipeline : pipelineQuery.asList()) {
      appIds.add(pipeline.getAppId());
    }
    final QLIdFilter newAppFilter =
        QLIdFilter.builder().values(appIds.toArray(new String[0])).operator(filter.getOperator()).build();
    utils.setIdFilter(appField, newAppFilter);
  }

  private void optimizeForWorkflow(Query<WorkflowExecution> query, QLIdFilter filter) {
    final FieldEnd<? extends Query<WorkflowExecution>> appField = query.field(WorkflowExecutionKeys.appId);
    Set<String> appIds = new HashSet<>();
    Query<Workflow> workflowQuery =
        wingsMongoPersistence.createAnalyticsQuery(Workflow.class).project(WorkflowKeys.appId, true);
    final FieldEnd<? extends Query<?>> field = workflowQuery.field(WorkflowKeys.uuid);
    utils.setIdFilter(field, filter);
    for (Workflow workflow : workflowQuery.asList()) {
      appIds.add(workflow.getAppId());
    }
    final QLIdFilter newAppFilter =
        QLIdFilter.builder().values(appIds.toArray(new String[0])).operator(filter.getOperator()).build();
    utils.setIdFilter(appField, newAppFilter);
  }

  private void optimizeForEnvironment(Query<WorkflowExecution> query, QLIdFilter filter) {
    final FieldEnd<? extends Query<WorkflowExecution>> appField = query.field(WorkflowExecutionKeys.appId);
    Set<String> appIds = new HashSet<>();
    Query<Environment> envQuery =
        wingsMongoPersistence.createAnalyticsQuery(Environment.class).project(EnvironmentKeys.appId, true);
    final FieldEnd<? extends Query<?>> field = envQuery.field(EnvironmentKeys.uuid);
    utils.setIdFilter(field, filter);
    for (Environment environment : envQuery.asList()) {
      appIds.add(environment.getAppId());
    }
    final QLIdFilter newAppFilter =
        QLIdFilter.builder().values(appIds.toArray(new String[0])).operator(filter.getOperator()).build();
    utils.setIdFilter(appField, newAppFilter);
  }

  private void optimizeForService(Query<WorkflowExecution> query, QLIdFilter filter) {
    final FieldEnd<? extends Query<WorkflowExecution>> appField = query.field(WorkflowExecutionKeys.appId);
    Set<String> appIds = new HashSet<>();
    Query<Service> serviceQuery =
        wingsMongoPersistence.createAnalyticsQuery(Service.class).project(ServiceKeys.appId, true);
    final FieldEnd<? extends Query<?>> field = serviceQuery.field(EnvironmentKeys.uuid);
    utils.setIdFilter(field, filter);
    for (Service service : serviceQuery.asList()) {
      appIds.add(service.getAppId());
    }
    final QLIdFilter newAppFilter =
        QLIdFilter.builder().values(appIds.toArray(new String[0])).operator(filter.getOperator()).build();
    utils.setIdFilter(appField, newAppFilter);
  }

  public BasicDBObject getIndexHint(List<QLExecutionFilter> filters) {
    Optional<QLExecutionFilter> executionIdFilter = filters.stream().filter(f -> f.getExecution() != null).findFirst();
    if (executionIdFilter.isPresent()) {
      return null;
    }
    final List<MongoIndex> wfIndexes = WorkflowExecution.mongoIndexes();
    for (QLBaseExecutionFilter filter : filters) {
      if (filter.getEnvironment() != null) {
        return BasicDBUtils.getIndexObject(wfIndexes, WFE_EXECUTIONS_SEARCH_ENVIDS);
      }

      if (filter.getPipeline() != null || filter.getWorkflow() != null) {
        return BasicDBUtils.getIndexObject(wfIndexes, WFE_EXECUTIONS_SEARCH_WORKFLOWID);
      }

      if (filter.getService() != null) {
        return BasicDBUtils.getIndexObject(wfIndexes, WFE_EXECUTIONS_SEARCH_SERVICEIDS);
      }
    }
    return null;
  }

  public void setQuery(List<QLExecutionFilter> filters, Query query, String accountId) {
    if (isEmpty(filters)) {
      return;
    }
    setBaseQuery(
        filters.stream().map(filter -> (QLBaseExecutionFilter) filter).collect(Collectors.toList()), query, accountId);

    if (featureFlagService.isEnabled(FeatureName.SPG_WFE_PROJECTIONS_GRAPHQL_DEPLOYMENTS_PAGE, accountId)) {
      for (String nonRequiredField : nonRequiredFields) {
        query.project(nonRequiredField, false);
      }
    }

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
