/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.approval;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.mongo.iterator.MongoPersistenceIterator.SchedulingType.REGULAR;

import static java.time.Duration.ofSeconds;

import io.harness.PipelineServiceIteratorsConfig;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.iterator.PersistenceIteratorFactory;
import io.harness.mongo.iterator.IteratorConfig;
import io.harness.mongo.iterator.MongoPersistenceIterator;
import io.harness.mongo.iterator.filter.SpringFilterExpander;
import io.harness.mongo.iterator.provider.SpringPersistenceRequiredProvider;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.yaml.ParameterField;
import io.harness.steps.approval.step.beans.ApprovalStatus;
import io.harness.steps.approval.step.beans.ApprovalType;
import io.harness.steps.approval.step.entities.ApprovalInstance;
import io.harness.steps.approval.step.entities.ApprovalInstance.ApprovalInstanceKeys;
import io.harness.steps.approval.step.jira.JiraApprovalHelperService;
import io.harness.steps.approval.step.jira.entities.JiraApprovalInstance;
import io.harness.steps.approval.step.servicenow.ServiceNowApprovalHelperService;
import io.harness.steps.approval.step.servicenow.entities.ServiceNowApprovalInstance;
import io.harness.utils.PmsFeatureFlagHelper;

import com.google.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;

@OwnedBy(CDC)
@Slf4j
public class ApprovalInstanceHandler implements MongoPersistenceIterator.Handler<ApprovalInstance> {
  private static final IteratorConfig DEFAULT_ITERATOR_CONFIG =
      IteratorConfig.builder().enabled(true).targetIntervalInSeconds(60).build();

  private final JiraApprovalHelperService jiraApprovalHelperService;
  private final MongoTemplate mongoTemplate;
  private final PersistenceIteratorFactory persistenceIteratorFactory;
  private final PipelineServiceIteratorsConfig iteratorsConfig;
  private final ServiceNowApprovalHelperService serviceNowApprovalHelperService;
  private final PmsFeatureFlagHelper pmsFeatureFlagHelper;

  @Inject
  public ApprovalInstanceHandler(JiraApprovalHelperService jiraApprovalHelperService, MongoTemplate mongoTemplate,
      PersistenceIteratorFactory persistenceIteratorFactory, PipelineServiceIteratorsConfig iteratorsConfig,
      ServiceNowApprovalHelperService serviceNowApprovalHelperService, PmsFeatureFlagHelper pmsFeatureFlagHelper) {
    this.jiraApprovalHelperService = jiraApprovalHelperService;
    this.mongoTemplate = mongoTemplate;
    this.persistenceIteratorFactory = persistenceIteratorFactory;
    this.iteratorsConfig = iteratorsConfig;
    this.serviceNowApprovalHelperService = serviceNowApprovalHelperService;
    this.pmsFeatureFlagHelper = pmsFeatureFlagHelper;
  }

  public void registerIterators() {
    IteratorConfig iteratorConfig = iteratorsConfig.getApprovalInstanceConfig();
    if (iteratorConfig == null) {
      iteratorConfig = DEFAULT_ITERATOR_CONFIG;
    }
    if (!iteratorConfig.isEnabled()) {
      return;
    }

    persistenceIteratorFactory.createPumpIteratorWithDedicatedThreadPool(
        PersistenceIteratorFactory.PumpExecutorOptions.builder()
            .name("ApprovalInstanceHandler")
            .poolSize(iteratorConfig.getThreadPoolCount())
            .interval(ofSeconds(iteratorConfig.getTargetIntervalInSeconds()))
            .build(),
        ApprovalInstanceHandler.class,
        MongoPersistenceIterator.<ApprovalInstance, SpringFilterExpander>builder()
            .clazz(ApprovalInstance.class)
            .fieldName(ApprovalInstanceKeys.nextIteration)
            .targetInterval(ofSeconds(iteratorConfig.getTargetIntervalInSeconds()))
            .acceptableNoAlertDelay(ofSeconds(iteratorConfig.getTargetIntervalInSeconds() * 2))
            .handler(this)
            .filterExpander(query
                -> query.addCriteria(Criteria.where(ApprovalInstanceKeys.status)
                                         .is(ApprovalStatus.WAITING)
                                         .and(ApprovalInstanceKeys.type)
                                         .in(ApprovalType.JIRA_APPROVAL, ApprovalType.SERVICENOW_APPROVAL)))
            .schedulingType(REGULAR)
            .persistenceProvider(new SpringPersistenceRequiredProvider<>(mongoTemplate))
            .redistribute(true));
  }

  @Override
  public void handle(ApprovalInstance entity) {
    switch (entity.getType()) {
      case JIRA_APPROVAL:
        JiraApprovalInstance jiraApprovalInstance = (JiraApprovalInstance) entity;
        if (!pmsFeatureFlagHelper.isEnabled(AmbianceUtils.getAccountId(entity.getAmbiance()),
                FeatureName.CDS_DISABLE_JIRA_SERVICENOW_RETRY_INTERVAL)
            && ParameterField.isNotNull(jiraApprovalInstance.getRetryInterval())) {
          return;
        }
        log.info("Executing Jira approval instance with id: {}", jiraApprovalInstance.getId());
        jiraApprovalHelperService.handlePollingEvent(null, jiraApprovalInstance);
        break;
      case SERVICENOW_APPROVAL:
        ServiceNowApprovalInstance serviceNowApprovalInstance = (ServiceNowApprovalInstance) entity;
        if (!pmsFeatureFlagHelper.isEnabled(AmbianceUtils.getAccountId(entity.getAmbiance()),
                FeatureName.CDS_DISABLE_JIRA_SERVICENOW_RETRY_INTERVAL)
            && ParameterField.isNotNull(serviceNowApprovalInstance.getRetryInterval())) {
          return;
        }
        log.info("Executing ServiceNow approval instance with id: {}", serviceNowApprovalInstance.getId());
        serviceNowApprovalHelperService.handlePollingEvent(null, serviceNowApprovalInstance);
        break;
      default:
        log.warn("ApprovalInstance without registered handler encountered. Id: {}", entity.getId());
    }
  }
}
