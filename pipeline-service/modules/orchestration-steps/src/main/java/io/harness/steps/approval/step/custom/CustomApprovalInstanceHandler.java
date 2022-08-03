/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.approval.step.custom;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import static java.time.Duration.ofSeconds;

import io.harness.annotations.dev.OwnedBy;
import io.harness.iterator.PersistenceIterator;
import io.harness.iterator.PersistenceIterator.ProcessMode;
import io.harness.iterator.PersistenceIteratorFactory;
import io.harness.mongo.iterator.IteratorConfig;
import io.harness.mongo.iterator.MongoPersistenceIterator;
import io.harness.mongo.iterator.MongoPersistenceIterator.SchedulingType;
import io.harness.mongo.iterator.filter.SpringFilterExpander;
import io.harness.mongo.iterator.provider.SpringPersistenceRequiredProvider;
import io.harness.steps.approval.step.beans.ApprovalStatus;
import io.harness.steps.approval.step.beans.ApprovalType;
import io.harness.steps.approval.step.custom.entities.CustomApprovalInstance;
import io.harness.steps.approval.step.custom.entities.CustomApprovalInstance.CustomApprovalInstanceKeys;
import io.harness.steps.approval.step.entities.ApprovalInstance;
import io.harness.steps.approval.step.entities.ApprovalInstance.ApprovalInstanceKeys;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;

@OwnedBy(CDC)
@Singleton
@Slf4j
public class CustomApprovalInstanceHandler implements MongoPersistenceIterator.Handler<ApprovalInstance> {
  private static final IteratorConfig DEFAULT_ITERATOR_CONFIG =
      IteratorConfig.builder().enabled(true).targetIntervalInSeconds(60).build();
  PersistenceIterator<ApprovalInstance> iterator;

  private CustomApprovalHelperService customApprovalHelperService;
  private final MongoTemplate mongoTemplate;
  private final PersistenceIteratorFactory persistenceIteratorFactory;

  @Inject
  public CustomApprovalInstanceHandler(CustomApprovalHelperService customApprovalHelperService,
      MongoTemplate mongoTemplate, PersistenceIteratorFactory persistenceIteratorFactory) {
    this.customApprovalHelperService = customApprovalHelperService;
    this.mongoTemplate = mongoTemplate;
    this.persistenceIteratorFactory = persistenceIteratorFactory;
  }

  public void registerIterators(IteratorConfig approvalIteratorConfig) {
    IteratorConfig iteratorConfig = approvalIteratorConfig;
    if (iteratorConfig == null) {
      iteratorConfig = DEFAULT_ITERATOR_CONFIG;
    }
    if (!iteratorConfig.isEnabled()) {
      return;
    }

    iterator = persistenceIteratorFactory.createLoopIteratorWithDedicatedThreadPool(
        PersistenceIteratorFactory.PumpExecutorOptions.builder()
            .name("CustomApprovalInstanceHandler")
            .poolSize(iteratorConfig.getThreadPoolCount())
            .interval(ofSeconds(iteratorConfig.getTargetIntervalInSeconds()))
            .build(),
        CustomApprovalInstanceHandler.class,
        MongoPersistenceIterator.<ApprovalInstance, SpringFilterExpander>builder()
            .mode(ProcessMode.PUMP)
            .clazz(ApprovalInstance.class)
            .fieldName(CustomApprovalInstanceKeys.nextIterations)
            .targetInterval(ofSeconds(iteratorConfig.getTargetIntervalInSeconds()))
            .acceptableNoAlertDelay(ofSeconds(iteratorConfig.getTargetIntervalInSeconds() * 2))
            .handler(this)
            .filterExpander(query
                -> query.addCriteria(Criteria.where(ApprovalInstanceKeys.status)
                                         .is(ApprovalStatus.WAITING)
                                         .and(ApprovalInstanceKeys.type)
                                         .is(ApprovalType.CUSTOM_APPROVAL)))
            .schedulingType(SchedulingType.IRREGULAR)
            .persistenceProvider(new SpringPersistenceRequiredProvider<>(mongoTemplate))
            .redistribute(true));
  }

  public void wakeup() {
    if (iterator != null) {
      iterator.wakeup();
    }
  }

  @Override
  public void handle(ApprovalInstance entity) {
    CustomApprovalInstance customApprovalInstance = (CustomApprovalInstance) entity;
    customApprovalHelperService.handlePollingEvent(iterator, customApprovalInstance);
  }
}
