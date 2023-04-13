/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package software.wings.search.entities.service;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.mongo.MongoConfig.NO_LIMIT;

import io.harness.annotations.dev.OwnedBy;
import io.harness.persistence.HIterator;

import software.wings.audit.AuditHeader;
import software.wings.audit.AuditHeader.AuditHeaderKeys;
import software.wings.audit.EntityAuditRecord;
import software.wings.beans.Application;
import software.wings.beans.EntityType;
import software.wings.beans.Pipeline;
import software.wings.beans.Pipeline.PipelineKeys;
import software.wings.beans.PipelineStage;
import software.wings.beans.PipelineStage.PipelineStageElement;
import software.wings.beans.Service;
import software.wings.beans.Service.ServiceKeys;
import software.wings.beans.Workflow;
import software.wings.beans.Workflow.WorkflowKeys;
import software.wings.beans.WorkflowExecution;
import software.wings.beans.WorkflowExecution.WorkflowExecutionKeys;
import software.wings.dl.WingsPersistence;
import software.wings.search.entities.related.audit.RelatedAuditView;
import software.wings.search.entities.related.audit.RelatedAuditViewBuilder;
import software.wings.search.entities.related.deployment.RelatedDeploymentView;
import software.wings.search.entities.service.ServiceView.ServiceViewKeys;
import software.wings.search.framework.EntityInfo;
import software.wings.search.framework.SearchEntityUtils;

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
 * Service to be stored in ELK
 *
 * @author ujjawal
 */

@OwnedBy(PL)
@Singleton
class ServiceViewBuilder {
  @Inject private WingsPersistence wingsPersistence;
  @Inject private RelatedAuditViewBuilder relatedAuditViewBuilder;
  private static final int DAYS_TO_RETAIN = 7;
  private static final int MAX_RELATED_ENTITIES_COUNT = 3;

  public List<String> getServiceIds(Workflow workflow) {
    List<String> serviceIds = new ArrayList<>();
    if (workflow.getOrchestration() != null && workflow.getOrchestration().getServiceIds() != null) {
      serviceIds.addAll(workflow.getOrchestration().getServiceIds());
    }
    return serviceIds;
  }

  public List<String> getServiceIds(Pipeline pipeline) {
    Set<String> uniqueServiceIds = new HashSet<>();
    if (pipeline.getPipelineStages() != null) {
      for (PipelineStage pipelineStage : pipeline.getPipelineStages()) {
        if (pipelineStage.getPipelineStageElements() != null) {
          for (PipelineStageElement pipelineStageElement : pipelineStage.getPipelineStageElements()) {
            if (pipelineStageElement.getProperties().containsKey("workflowId")) {
              Workflow workflow = wingsPersistence.get(
                  Workflow.class, pipelineStageElement.getProperties().get("workflowId").toString());
              uniqueServiceIds.addAll(getServiceIds(workflow));
            }
          }
        }
      }
    }
    return new ArrayList<>(uniqueServiceIds);
  }

  private ServiceView createBaseView(Service service) {
    return new ServiceView(service.getUuid(), service.getName(), service.getDescription(), service.getAccountId(),
        service.getCreatedAt(), service.getLastUpdatedAt(), EntityType.SERVICE, service.getCreatedBy(),
        service.getLastUpdatedBy(), service.getAppId(), service.getArtifactType(), service.getDeploymentType());
  }

  private void setAuditsAndAuditTimestamps(Service service, ServiceView serviceView) {
    long startTimestamp = SearchEntityUtils.getTimestampNdaysBackInMillis(DAYS_TO_RETAIN);
    List<RelatedAuditView> audits = new ArrayList<>();
    List<Long> auditTimestamps = new ArrayList<>();
    try (HIterator<AuditHeader> iterator = new HIterator<>(wingsPersistence.createQuery(AuditHeader.class)
                                                               .field(AuditHeaderKeys.accountId)
                                                               .equal(service.getAccountId())
                                                               .field("entityAuditRecords.entityId")
                                                               .equal(service.getUuid())
                                                               .field(ServiceKeys.createdAt)
                                                               .greaterThanOrEq(startTimestamp)
                                                               .order(Sort.descending(AuditHeaderKeys.createdAt))
                                                               .limit(NO_LIMIT)
                                                               .fetch())) {
      while (iterator.hasNext()) {
        final AuditHeader auditHeader = iterator.next();
        Map<String, Boolean> isAffectedResourceHandled = new HashMap<>();
        for (EntityAuditRecord entityAuditRecord : auditHeader.getEntityAuditRecords()) {
          if (entityAuditRecord.getAffectedResourceType() != null
              && entityAuditRecord.getAffectedResourceType().equals(EntityType.SERVICE.name())
              && entityAuditRecord.getAffectedResourceId() != null
              && entityAuditRecord.getAffectedResourceId().equals(service.getUuid())
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
    serviceView.setAuditTimestamps(auditTimestamps);
    serviceView.setAudits(audits);
  }

  private void setDeploymentsAndDeploymentTimestamps(Service service, ServiceView serviceView) {
    long startTimestamp = SearchEntityUtils.getTimestampNdaysBackInMillis(DAYS_TO_RETAIN);
    List<Long> deploymentTimestamps = new ArrayList<>();
    List<RelatedDeploymentView> deployments = new ArrayList<>();
    try (HIterator<WorkflowExecution> iterator =
             new HIterator<>(wingsPersistence.createQuery(WorkflowExecution.class)
                                 .field(WorkflowExecutionKeys.appId)
                                 .equal(service.getAppId())
                                 .field(WorkflowExecutionKeys.serviceIds)
                                 .equal(service.getUuid())
                                 .field(ServiceKeys.createdAt)
                                 .greaterThanOrEq(startTimestamp)
                                 .order(Sort.descending(WorkflowExecutionKeys.createdAt))
                                 .fetch())) {
      while (iterator.hasNext()) {
        final WorkflowExecution workflowExecution = iterator.next();
        if (deployments.size() < MAX_RELATED_ENTITIES_COUNT) {
          deployments.add(new RelatedDeploymentView(workflowExecution));
        }
        deploymentTimestamps.add(workflowExecution.getCreatedAt());
      }
    }
    Collections.reverse(deployments);
    Collections.reverse(deploymentTimestamps);
    serviceView.setDeployments(deployments);
    serviceView.setDeploymentTimestamps(deploymentTimestamps);
  }

  private Set<Pipeline> populatePipelines(Workflow workflow) {
    Set<Pipeline> pipelines = new HashSet<>();
    try (HIterator<Pipeline> iterator =
             new HIterator<>(wingsPersistence.createQuery(Pipeline.class)
                                 .field(PipelineKeys.appId)
                                 .equal(workflow.getAppId())
                                 .field("pipelineStages.pipelineStageElements.properties.workflowId")
                                 .equal(workflow.getUuid())
                                 .fetch())) {
      while (iterator.hasNext()) {
        final Pipeline pipeline = iterator.next();
        pipelines.add(pipeline);
      }
    }

    return pipelines;
  }

  private void setWorkflowsAndPipelines(Service service, ServiceView serviceView) {
    Set<EntityInfo> workflows = new HashSet<>();
    Set<EntityInfo> pipelines = new HashSet<>();
    try (
        HIterator<Workflow> iterator = new HIterator<>(
            wingsPersistence.createQuery(Workflow.class).field(WorkflowKeys.appId).equal(service.getAppId()).fetch())) {
      while (iterator.hasNext()) {
        final Workflow workflow = iterator.next();
        if (workflow.getOrchestration() != null && workflow.getOrchestration().getServiceIds() != null
            && workflow.getOrchestration().getServiceIds().stream().parallel().anyMatch(service.getUuid()::equals)) {
          workflows.add(new EntityInfo(workflow.getUuid(), workflow.getName()));
          for (Pipeline pipeline : populatePipelines(workflow)) {
            pipelines.add(new EntityInfo(pipeline.getUuid(), pipeline.getName()));
          }
        }
      }
    }
    serviceView.setWorkflows(workflows);
    serviceView.setPipelines(pipelines);
  }

  private void setApplicationName(Application application, Service service, ServiceView serviceView) {
    if (service.getAppId() != null) {
      if (application.getName() != null) {
        serviceView.setAppName(application.getName());
      }
    }
  }

  ServiceView createServiceView(Service service) {
    Application application = wingsPersistence.get(Application.class, service.getAppId());
    if (application != null) {
      ServiceView serviceView = createBaseView(service);
      setAuditsAndAuditTimestamps(service, serviceView);
      setDeploymentsAndDeploymentTimestamps(service, serviceView);
      setApplicationName(application, service, serviceView);
      setWorkflowsAndPipelines(service, serviceView);
      return serviceView;
    }
    return null;
  }

  ServiceView createServiceView(Service service, DBObject changeDocument) {
    Application application = wingsPersistence.get(Application.class, service.getAppId());
    if (application != null) {
      ServiceView serviceView = createBaseView(service);
      if (changeDocument.containsField(ServiceViewKeys.appId)) {
        setApplicationName(application, service, serviceView);
      }
      return serviceView;
    }
    return null;
  }
}
