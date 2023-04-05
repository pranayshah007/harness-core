/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package software.wings.search.entities.environment;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.beans.FeatureName.SPG_OPTIMIZE_ENVIRONMENT_VIEW_BUILDER;
import static io.harness.mongo.MongoConfig.NO_LIMIT;

import static software.wings.beans.WorkflowExecution.WFE_EXECUTIONS_SEARCH_ENVIDS;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.WorkflowType;
import io.harness.ff.FeatureFlagService;
import io.harness.mongo.index.BasicDBUtils;
import io.harness.persistence.HIterator;

import software.wings.audit.AuditHeader;
import software.wings.audit.AuditHeader.AuditHeaderKeys;
import software.wings.audit.EntityAuditRecord;
import software.wings.beans.Application;
import software.wings.beans.EntityType;
import software.wings.beans.Environment;
import software.wings.beans.Environment.EnvironmentKeys;
import software.wings.beans.Pipeline;
import software.wings.beans.Pipeline.PipelineKeys;
import software.wings.beans.PipelineStage;
import software.wings.beans.PipelineStage.PipelineStageElement;
import software.wings.beans.Workflow;
import software.wings.beans.Workflow.WorkflowKeys;
import software.wings.beans.WorkflowExecution;
import software.wings.beans.WorkflowExecution.WorkflowExecutionKeys;
import software.wings.dl.WingsPersistence;
import software.wings.search.entities.related.audit.RelatedAuditView;
import software.wings.search.entities.related.audit.RelatedAuditViewBuilder;
import software.wings.search.entities.related.deployment.RelatedDeploymentView;
import software.wings.search.framework.EntityInfo;
import software.wings.search.framework.SearchEntityUtils;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.mongodb.DBObject;
import dev.morphia.query.FindOptions;
import dev.morphia.query.Query;
import dev.morphia.query.Sort;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Builder class to build Materialized View of
 * Environment to be stored in ELK
 *
 * @author ujjawal
 */

@OwnedBy(PL)
@Singleton
class EnvironmentViewBuilder {
  @Inject private WingsPersistence wingsPersistence;
  @Inject private RelatedAuditViewBuilder relatedAuditViewBuilder;
  @Inject private FeatureFlagService featureFlagService;
  private static final String ENV_ID_KEY = "envId";
  private static final int DAYS_TO_RETAIN = 7;
  private static final int MAX_RELATED_ENTITIES_COUNT = 3;

  private EnvironmentView createBaseView(Environment environment) {
    return new EnvironmentView(environment.getUuid(), environment.getName(), environment.getDescription(),
        environment.getAccountId(), environment.getCreatedAt(), environment.getLastUpdatedAt(), EntityType.ENVIRONMENT,
        environment.getCreatedBy(), environment.getLastUpdatedBy(), environment.getAppId(),
        environment.getEnvironmentType());
  }

  private void setWorkflows(Environment environment, EnvironmentView environmentView) {
    Set<EntityInfo> workflows = new HashSet<>();
    try (HIterator<Workflow> iterator = new HIterator<>(wingsPersistence.createQuery(Workflow.class)
                                                            .field(WorkflowKeys.appId)
                                                            .equal(environment.getAppId())
                                                            .filter(WorkflowKeys.envId, environment.getUuid())
                                                            .fetch())) {
      while (iterator.hasNext()) {
        final Workflow workflow = iterator.next();
        EntityInfo entityInfo = new EntityInfo(workflow.getUuid(), workflow.getName());
        workflows.add(entityInfo);
      }
    }
    environmentView.setWorkflows(workflows);
  }

  private void setAuditsAndTimestamps(Environment environment, EnvironmentView environmentView) {
    long startTimestamp = SearchEntityUtils.getTimestampNdaysBackInMillis(DAYS_TO_RETAIN);
    List<RelatedAuditView> audits = new ArrayList<>();
    List<Long> auditTimestamps = new ArrayList<>();
    try (HIterator<AuditHeader> iterator = new HIterator<>(wingsPersistence.createQuery(AuditHeader.class)
                                                               .field(AuditHeaderKeys.accountId)
                                                               .equal(environment.getAccountId())
                                                               .field("entityAuditRecords.entityId")
                                                               .equal(environment.getUuid())
                                                               .field(EnvironmentKeys.createdAt)
                                                               .greaterThanOrEq(startTimestamp)
                                                               .order(Sort.descending(AuditHeaderKeys.createdAt))
                                                               .limit(NO_LIMIT)
                                                               .fetch())) {
      while (iterator.hasNext()) {
        final AuditHeader auditHeader = iterator.next();
        Map<String, Boolean> isAffectedResourceHandled = new HashMap<>();
        for (EntityAuditRecord entityAuditRecord : auditHeader.getEntityAuditRecords()) {
          if (entityAuditRecord.getAffectedResourceType() != null
              && entityAuditRecord.getAffectedResourceType().equals(EntityType.ENVIRONMENT.name())
              && entityAuditRecord.getAffectedResourceId() != null
              && entityAuditRecord.getAffectedResourceId().equals(environment.getUuid())
              && !isAffectedResourceHandled.containsKey(entityAuditRecord.getAffectedResourceId())) {
            if (audits.size() < MAX_RELATED_ENTITIES_COUNT) {
              audits.add(relatedAuditViewBuilder.getAuditRelatedEntityView(auditHeader, entityAuditRecord));
            }
            auditTimestamps.add(auditHeader.getCreatedAt());
            isAffectedResourceHandled.put(entityAuditRecord.getAffectedResourceId(), true);
          }
        }
      }
    }
    Collections.reverse(audits);
    Collections.reverse(auditTimestamps);
    environmentView.setAudits(audits);
    environmentView.setAuditTimestamps(auditTimestamps);
  }

  private void setDeploymentsAndDeploymentTimestamps(Environment environment, EnvironmentView environmentView) {
    long startTimestamp = SearchEntityUtils.getTimestampNdaysBackInMillis(DAYS_TO_RETAIN);
    List<Long> deploymentTimestamps = new ArrayList<>();
    List<RelatedDeploymentView> deployments = new ArrayList<>();
    Query<WorkflowExecution> query = wingsPersistence.createQuery(WorkflowExecution.class)
                                         .filter(WorkflowExecutionKeys.accountId, environment.getAccountId())
                                         .filter(WorkflowExecutionKeys.appId, environment.getAppId())
                                         .filter(WorkflowExecutionKeys.envId, environment.getUuid())
                                         .field(EnvironmentKeys.createdAt)
                                         .greaterThanOrEq(startTimestamp)
                                         .order(Sort.descending(WorkflowExecutionKeys.createdAt));
    FindOptions findOptions = new FindOptions();

    if (featureFlagService.isEnabled(SPG_OPTIMIZE_ENVIRONMENT_VIEW_BUILDER, environment.getAccountId())) {
      query.field(WorkflowExecutionKeys.envIds)
          .hasThisOne(environment.getUuid())
          .project(WorkflowExecutionKeys.createdAt, true)
          .project(WorkflowExecutionKeys.status, true)
          .project(WorkflowExecutionKeys.name, true)
          .project(WorkflowExecutionKeys.pipelineExecutionId, true)
          .project(WorkflowExecutionKeys.workflowType, true)
          .project(WorkflowExecutionKeys.envId, true)
          .project(WorkflowExecutionKeys.workflowId, true)
          .project(WorkflowExecutionKeys.envIds, true);
      findOptions.hint(BasicDBUtils.getIndexObject(WorkflowExecution.mongoIndexes(), WFE_EXECUTIONS_SEARCH_ENVIDS));
    }

    try (HIterator<WorkflowExecution> iterator = new HIterator<>(query.fetch(findOptions))) {
      while (iterator.hasNext()) {
        final WorkflowExecution workflowExecution = iterator.next();
        if (workflowExecution.getWorkflowType() == WorkflowType.ORCHESTRATION) {
          if (deployments.size() < MAX_RELATED_ENTITIES_COUNT) {
            deployments.add(new RelatedDeploymentView(workflowExecution));
          }
          deploymentTimestamps.add(workflowExecution.getCreatedAt());
        }
      }
    }
    Collections.reverse(deployments);
    Collections.reverse(deploymentTimestamps);
    environmentView.setDeploymentTimestamps(deploymentTimestamps);
    environmentView.setDeployments(deployments);
  }

  private void setPipelines(Environment environment, EnvironmentView environmentView) {
    Set<EntityInfo> pipelines = new HashSet<>();
    try (HIterator<Pipeline> iterator =
             new HIterator<>(wingsPersistence.createQuery(Pipeline.class)
                                 .field(PipelineKeys.appId)
                                 .equal(environment.getAppId())
                                 .field("pipelineStages.pipelineStageElements.properties.envId")
                                 .equal(environment.getUuid())
                                 .fetch())) {
      while (iterator.hasNext()) {
        final Pipeline pipeline = iterator.next();
        pipelines.add(new EntityInfo(pipeline.getUuid(), pipeline.getName()));
      }
    }
    environmentView.setPipelines(pipelines);
  }

  private void setApplicationName(Application application, Environment environment, EnvironmentView environmentView) {
    if (environment.getAppId() != null) {
      if (application.getName() != null) {
        environmentView.setAppName(application.getName());
      }
    }
  }

  List<String> populateEnvIds(Pipeline pipeline) {
    List<String> envIds = new ArrayList<>();
    if (pipeline.getPipelineStages() != null) {
      for (PipelineStage pipelineStage : pipeline.getPipelineStages()) {
        if (pipelineStage.getPipelineStageElements() != null) {
          for (PipelineStageElement pipelineStageElement : pipelineStage.getPipelineStageElements()) {
            if (pipelineStageElement.getProperties().containsKey(ENV_ID_KEY)
                && pipelineStageElement.getProperties().get(ENV_ID_KEY) != null) {
              envIds.add(pipelineStageElement.getProperties().get(ENV_ID_KEY).toString());
            }
          }
        }
      }
    }
    return envIds;
  }

  EnvironmentView createEnvironmentView(Environment environment) {
    Application application = wingsPersistence.get(Application.class, environment.getAppId());

    if (application != null) {
      EnvironmentView environmentView = createBaseView(environment);
      setAuditsAndTimestamps(environment, environmentView);
      setDeploymentsAndDeploymentTimestamps(environment, environmentView);
      setWorkflows(environment, environmentView);
      setPipelines(environment, environmentView);
      setApplicationName(application, environment, environmentView);
      return environmentView;
    }
    return null;
  }

  EnvironmentView createEnvironmentView(Environment environment, DBObject changeDocument) {
    Application application = wingsPersistence.get(Application.class, environment.getAppId());
    if (application != null) {
      EnvironmentView environmentView = createBaseView(environment);
      if (changeDocument.containsField(WorkflowKeys.appId)) {
        setApplicationName(application, environment, environmentView);
      }
      return environmentView;
    }
    return null;
  }
}
