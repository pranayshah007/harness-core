/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package software.wings.search.entities.application;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.mongo.MongoConfig.NO_LIMIT;

import io.harness.annotations.dev.OwnedBy;
import io.harness.persistence.HIterator;

import software.wings.audit.AuditHeader;
import software.wings.audit.AuditHeader.AuditHeaderKeys;
import software.wings.beans.Application;
import software.wings.beans.Application.ApplicationKeys;
import software.wings.beans.EntityType;
import software.wings.beans.Environment;
import software.wings.beans.Environment.EnvironmentKeys;
import software.wings.beans.Pipeline;
import software.wings.beans.Pipeline.PipelineKeys;
import software.wings.beans.Service;
import software.wings.beans.Service.ServiceKeys;
import software.wings.beans.Workflow;
import software.wings.beans.Workflow.WorkflowKeys;
import software.wings.dl.WingsPersistence;
import software.wings.search.entities.related.audit.RelatedAuditView;
import software.wings.search.entities.related.audit.RelatedAuditViewBuilder;
import software.wings.search.framework.EntityInfo;
import software.wings.search.framework.SearchEntityUtils;

import com.google.inject.Inject;
import dev.morphia.query.Sort;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.FieldNameConstants;

/**
 * Builder class to build Materialized View of
 * Application to be stored in ELK
 *
 * @author ujjawal
 */

@OwnedBy(PL)
@Data
@EqualsAndHashCode(callSuper = false)
@FieldNameConstants(innerTypeName = "ApplicationViewBuilderKeys")
class ApplicationViewBuilder {
  @Inject private WingsPersistence wingsPersistence;
  @Inject private RelatedAuditViewBuilder relatedAuditViewBuilder;
  private static final int DAYS_TO_RETAIN = 7;
  private static final int MAX_RELATED_ENTITIES_COUNT = 3;

  private ApplicationView createBaseView(Application application) {
    return new ApplicationView(application.getUuid(), application.getName(), application.getDescription(),
        application.getAccountId(), application.getCreatedAt(), application.getLastUpdatedAt(), EntityType.APPLICATION,
        application.getCreatedBy(), application.getLastUpdatedBy());
  }

  private void setServices(Application application, ApplicationView applicationView) {
    Set<EntityInfo> services = new HashSet<>();
    try (HIterator<Service> iterator = new HIterator<>(wingsPersistence.createQuery(Service.class)
                                                           .field(ServiceKeys.appId)
                                                           .equal(application.getUuid())
                                                           .fetch())) {
      while (iterator.hasNext()) {
        final Service service = iterator.next();
        EntityInfo entityInfo = new EntityInfo(service.getUuid(), service.getName());
        services.add(entityInfo);
      }
    }
    applicationView.setServices(services);
  }

  private void setWorkflows(Application application, ApplicationView applicationView) {
    Set<EntityInfo> workflows = new HashSet<>();
    try (HIterator<Workflow> iterator = new HIterator<>(wingsPersistence.createQuery(Workflow.class)
                                                            .field(WorkflowKeys.appId)
                                                            .equal(application.getUuid())
                                                            .fetch())) {
      while (iterator.hasNext()) {
        final Workflow workflow = iterator.next();
        EntityInfo entityInfo = new EntityInfo(workflow.getUuid(), workflow.getName());
        workflows.add(entityInfo);
      }
    }
    applicationView.setWorkflows(workflows);
  }

  private void setEnvironments(Application application, ApplicationView applicationView) {
    Set<EntityInfo> environments = new HashSet<>();
    try (HIterator<Environment> iterator = new HIterator<>(wingsPersistence.createQuery(Environment.class)
                                                               .field(EnvironmentKeys.appId)
                                                               .equal(application.getUuid())
                                                               .fetch())) {
      while (iterator.hasNext()) {
        final Environment environment = iterator.next();
        EntityInfo entityInfo = new EntityInfo(environment.getUuid(), environment.getName());
        environments.add(entityInfo);
      }
    }
    applicationView.setEnvironments(environments);
  }

  private void setPipelines(Application application, ApplicationView applicationView) {
    Set<EntityInfo> pipelines = new HashSet<>();
    try (HIterator<Pipeline> iterator = new HIterator<>(wingsPersistence.createQuery(Pipeline.class)
                                                            .field(PipelineKeys.appId)
                                                            .equal(application.getUuid())
                                                            .fetch())) {
      while (iterator.hasNext()) {
        final Pipeline pipeline = iterator.next();
        EntityInfo entityInfo = new EntityInfo(pipeline.getUuid(), pipeline.getName());
        pipelines.add(entityInfo);
      }
    }
    applicationView.setPipelines(pipelines);
  }

  private void setAuditsAndAuditTimestamps(Application application, ApplicationView applicationView) {
    long startTimestamp = SearchEntityUtils.getTimestampNdaysBackInMillis(DAYS_TO_RETAIN);
    List<RelatedAuditView> audits = new ArrayList<>();
    List<Long> auditTimestamps = new ArrayList<>();
    try (HIterator<AuditHeader> iterator = new HIterator<>(wingsPersistence.createQuery(AuditHeader.class)
                                                               .field(AuditHeaderKeys.accountId)
                                                               .equal(application.getAccountId())
                                                               .field("entityAuditRecords.appId")
                                                               .equal(application.getUuid())
                                                               .field(ApplicationKeys.createdAt)
                                                               .greaterThanOrEq(startTimestamp)
                                                               .order(Sort.descending(AuditHeaderKeys.createdAt))
                                                               .limit(NO_LIMIT)
                                                               .fetch())) {
      while (iterator.hasNext()) {
        final AuditHeader auditHeader = iterator.next();
        if (audits.size() < MAX_RELATED_ENTITIES_COUNT) {
          audits.add(relatedAuditViewBuilder.getAuditRelatedEntityView(auditHeader));
        }
        auditTimestamps.add(auditHeader.getCreatedAt());
      }
    }
    Collections.reverse(audits);
    Collections.reverse(auditTimestamps);
    applicationView.setAudits(audits);
    applicationView.setAuditTimestamps(auditTimestamps);
  }

  ApplicationView createApplicationView(Application application, boolean updateOnly) {
    ApplicationView applicationView = createBaseView(application);
    if (!updateOnly) {
      setWorkflows(application, applicationView);
      setEnvironments(application, applicationView);
      setPipelines(application, applicationView);
      setServices(application, applicationView);
      setAuditsAndAuditTimestamps(application, applicationView);
    }
    return applicationView;
  }
}
