/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package software.wings.search.entities.pipeline;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.mongo.MongoConfig.NO_LIMIT;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.WorkflowType;
import io.harness.persistence.HIterator;

import software.wings.audit.AuditHeader;
import software.wings.audit.AuditHeader.AuditHeaderKeys;
import software.wings.audit.EntityAuditRecord;
import software.wings.beans.Application;
import software.wings.beans.EntityType;
import software.wings.beans.OrchestrationWorkflow;
import software.wings.beans.Pipeline;
import software.wings.beans.Pipeline.PipelineKeys;
import software.wings.beans.PipelineStage;
import software.wings.beans.PipelineStage.PipelineStageElement;
import software.wings.beans.Workflow;
import software.wings.beans.WorkflowExecution;
import software.wings.beans.WorkflowExecution.WorkflowExecutionKeys;
import software.wings.dl.WingsPersistence;
import software.wings.search.entities.related.audit.RelatedAuditView;
import software.wings.search.entities.related.audit.RelatedAuditViewBuilder;
import software.wings.search.entities.related.deployment.RelatedDeploymentView;
import software.wings.search.framework.EntityInfo;
import software.wings.search.framework.SearchEntityUtils;
import software.wings.service.intfc.ServiceResourceService;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.mongodb.DBObject;
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
 * Pipeline to be stored in ELK
 *
 * @author ujjawal
 */

@OwnedBy(PL)
@Singleton
class PipelineViewBuilder {
  @Inject private ServiceResourceService serviceResourceService;
  @Inject private WingsPersistence wingsPersistence;
  @Inject private RelatedAuditViewBuilder relatedAuditViewBuilder;
  private static final int MAX_RELATED_ENTITIES_COUNT = 3;
  private static final int DAYS_TO_RETAIN = 7;

  private void populateServices(Workflow workflow) {
    OrchestrationWorkflow orchestrationWorkflow = workflow.getOrchestration();
    workflow.setServices(
        serviceResourceService.fetchServicesByUuids(workflow.getAppId(), orchestrationWorkflow.getServiceIds()));
  }

  private PipelineView createBaseView(Pipeline pipeline) {
    return new PipelineView(pipeline.getUuid(), pipeline.getName(), pipeline.getDescription(), pipeline.getAccountId(),
        pipeline.getCreatedAt(), pipeline.getLastUpdatedAt(), EntityType.PIPELINE, pipeline.getCreatedBy(),
        pipeline.getLastUpdatedBy(), pipeline.getAppId());
  }

  private void setAuditsAndAuditTimestamps(Pipeline pipeline, PipelineView pipelineView) {
    long startTimestamp = SearchEntityUtils.getTimestampNdaysBackInMillis(DAYS_TO_RETAIN);
    List<RelatedAuditView> audits = new ArrayList<>();
    List<Long> auditTimestamps = new ArrayList<>();
    try (HIterator<AuditHeader> iterator = new HIterator<>(wingsPersistence.createQuery(AuditHeader.class)
                                                               .field(AuditHeaderKeys.accountId)
                                                               .equal(pipeline.getAccountId())
                                                               .field("entityAuditRecords.entityId")
                                                               .equal(pipeline.getUuid())
                                                               .field(PipelineKeys.createdAt)
                                                               .greaterThanOrEq(startTimestamp)
                                                               .order(Sort.descending(AuditHeaderKeys.createdAt))
                                                               .limit(NO_LIMIT)
                                                               .fetch())) {
      while (iterator.hasNext()) {
        final AuditHeader auditHeader = iterator.next();
        Map<String, Boolean> isAffectedResourceHandled = new HashMap<>();
        for (EntityAuditRecord entityAuditRecord : auditHeader.getEntityAuditRecords()) {
          if (entityAuditRecord.getAffectedResourceType() != null
              && entityAuditRecord.getAffectedResourceType().equals(EntityType.PIPELINE.name())
              && entityAuditRecord.getAffectedResourceId() != null
              && entityAuditRecord.getAffectedResourceId().equals(pipeline.getUuid())
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
    pipelineView.setAudits(audits);
    pipelineView.setAuditTimestamps(auditTimestamps);
  }

  private void setDeploymentsAndDeploymentTimestamps(Pipeline pipeline, PipelineView pipelineView) {
    long startTimestamp = SearchEntityUtils.getTimestampNdaysBackInMillis(DAYS_TO_RETAIN);
    List<Long> deploymentTimestamps = new ArrayList<>();
    List<RelatedDeploymentView> deployments = new ArrayList<>();
    try (HIterator<WorkflowExecution> iterator =
             new HIterator<>(wingsPersistence.createQuery(WorkflowExecution.class)
                                 .field(WorkflowExecutionKeys.appId)
                                 .equal(pipeline.getAppId())
                                 .filter(WorkflowExecutionKeys.workflowId, pipeline.getUuid())
                                 .field(PipelineKeys.createdAt)
                                 .greaterThanOrEq(startTimestamp)
                                 .order(Sort.descending(WorkflowExecutionKeys.createdAt))
                                 .fetch())) {
      while (iterator.hasNext()) {
        final WorkflowExecution workflowExecution = iterator.next();
        if (workflowExecution.getWorkflowType() == WorkflowType.PIPELINE) {
          if (deployments.size() < MAX_RELATED_ENTITIES_COUNT) {
            deployments.add(new RelatedDeploymentView(workflowExecution));
          }
          deploymentTimestamps.add(workflowExecution.getCreatedAt());
        }
      }
    }
    Collections.reverse(deployments);
    Collections.reverse(deploymentTimestamps);
    pipelineView.setDeploymentTimestamps(deploymentTimestamps);
    pipelineView.setDeployments(deployments);
  }

  private void setApplicationName(Application application, Pipeline pipeline, PipelineView pipelineView) {
    if (pipeline.getAppId() != null) {
      if (application.getName() != null) {
        pipelineView.setAppName(application.getName());
      }
    }
  }

  private void setServicesAndWorkflows(Pipeline pipeline, PipelineView pipelineView) {
    String worklowIdKey = "workflowId";
    if (pipeline.getPipelineStages() != null) {
      Set<EntityInfo> workflows = new HashSet<>();
      Set<EntityInfo> services = new HashSet<>();
      for (PipelineStage pipelineStage : pipeline.getPipelineStages()) {
        for (PipelineStageElement pipelineStageElement : pipelineStage.getPipelineStageElements()) {
          if (pipelineStageElement != null && pipelineStageElement.getProperties() != null
              && pipelineStageElement.getProperties().get(worklowIdKey) != null) {
            Workflow workflow =
                wingsPersistence.get(Workflow.class, pipelineStageElement.getProperties().get(worklowIdKey).toString());
            if (workflow != null) {
              EntityInfo workflowInfo =
                  new EntityInfo(pipelineStageElement.getProperties().get(worklowIdKey).toString(), workflow.getName());
              workflows.add(workflowInfo);
              if (workflow.getOrchestration() != null) {
                populateServices(workflow);
                workflow.getServices().forEach(
                    service -> services.add(new EntityInfo(service.getUuid(), service.getName())));
              }
            }
          }
        }
      }
      pipelineView.setServices(services);
      pipelineView.setWorkflows(workflows);
    }
  }

  PipelineView createPipelineView(Pipeline pipeline) {
    Application application = wingsPersistence.get(Application.class, pipeline.getAppId());
    if (application != null) {
      PipelineView pipelineView = createBaseView(pipeline);
      setApplicationName(application, pipeline, pipelineView);
      setServicesAndWorkflows(pipeline, pipelineView);
      setDeploymentsAndDeploymentTimestamps(pipeline, pipelineView);
      setAuditsAndAuditTimestamps(pipeline, pipelineView);
      return pipelineView;
    }
    return null;
  }

  PipelineView createPipelineView(Pipeline pipeline, DBObject changeDocument) {
    Application application = wingsPersistence.get(Application.class, pipeline.getAppId());
    if (application != null) {
      PipelineView pipelineView = createBaseView(pipeline);
      if (changeDocument.containsField(PipelineKeys.appId)) {
        setApplicationName(application, pipeline, pipelineView);
      }
      if (changeDocument.containsField(PipelineKeys.pipelineStages)) {
        setServicesAndWorkflows(pipeline, pipelineView);
      }
      return pipelineView;
    }
    return null;
  }
}
