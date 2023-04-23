/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.triggers.scheduled;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.mongo.iterator.MongoPersistenceIterator.SchedulingType.IRREGULAR_SKIP_MISSED;

import static java.time.Duration.ofMinutes;
import static java.time.Duration.ofSeconds;

import io.harness.annotations.dev.OwnedBy;
import io.harness.authorization.AuthorizationServiceHeader;
import io.harness.data.structure.UUIDGenerator;
import io.harness.execution.PlanExecution;
import io.harness.iterator.PersistenceIteratorFactory;
import io.harness.logging.AutoLogContext;
import io.harness.logging.NgTriggerAutoLogContext;
import io.harness.mongo.iterator.IteratorConfig;
import io.harness.mongo.iterator.MongoPersistenceIterator;
import io.harness.mongo.iterator.MongoPersistenceIterator.Handler;
import io.harness.mongo.iterator.filter.SpringFilterExpander;
import io.harness.mongo.iterator.provider.SpringPersistenceProvider;
import io.harness.ngtriggers.beans.dto.TriggerDetails;
import io.harness.ngtriggers.beans.entity.NGTriggerEntity;
import io.harness.ngtriggers.beans.entity.NGTriggerEntity.NGTriggerEntityKeys;
import io.harness.ngtriggers.beans.entity.TriggerEventHistory;
import io.harness.ngtriggers.beans.entity.TriggerEventHistory.TriggerEventHistoryBuilder;
import io.harness.ngtriggers.beans.entity.TriggerWebhookEvent;
import io.harness.ngtriggers.beans.response.TargetExecutionSummary;
import io.harness.ngtriggers.beans.source.NGTriggerType;
import io.harness.ngtriggers.mapper.NGTriggerElementMapper;
import io.harness.pms.contracts.triggers.TriggerPayload;
import io.harness.pms.contracts.triggers.Type;
import io.harness.pms.triggers.TriggerExecutionHelper;
import io.harness.repositories.spring.TriggerEventHistoryRepository;
import io.harness.security.SecurityContextBuilder;
import io.harness.security.SourcePrincipalContextBuilder;
import io.harness.security.dto.ServicePrincipal;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;

@Slf4j
@Singleton
@OwnedBy(PIPELINE)
public class ScheduledTriggerHandler implements Handler<NGTriggerEntity> {
  @Inject private PersistenceIteratorFactory persistenceIteratorFactory;
  @Inject private MongoTemplate mongoTemplate;
  @Inject private TriggerExecutionHelper ngTriggerExecutionHelper;
  @Inject private TriggerEventHistoryRepository triggerEventHistoryRepository;
  @Inject private NGTriggerElementMapper ngTriggerElementMapper;
  @Inject private TriggerExecutionHelper triggerExecutionHelper;

  public void registerIterators(IteratorConfig iteratorConfig) {
    persistenceIteratorFactory.createLoopIteratorWithDedicatedThreadPool(
        PersistenceIteratorFactory.PumpExecutorOptions.builder()
            .name("ScheduledTriggerProcessor")
            .poolSize(iteratorConfig.getThreadPoolCount())
            .interval(ofSeconds(iteratorConfig.getTargetIntervalInSeconds()))
            .build(),
        ScheduledTriggerHandler.class,
        MongoPersistenceIterator.<NGTriggerEntity, SpringFilterExpander>builder()
            .clazz(NGTriggerEntity.class)
            .fieldName(NGTriggerEntityKeys.nextIterations)
            .targetInterval(ofMinutes(5))
            .acceptableExecutionTime(ofMinutes(1))
            .acceptableNoAlertDelay(ofSeconds(30))
            .maximumDelayForCheck(ofSeconds(30))
            .handler(this)
            .filterExpander(query
                -> query.addCriteria(new Criteria()
                                         .and(NGTriggerEntityKeys.nextIterations)
                                         .exists(true)
                                         .and(NGTriggerEntityKeys.type)
                                         .is(NGTriggerType.SCHEDULED)
                                         .and(NGTriggerEntityKeys.enabled)
                                         .is(true)
                                         .and(NGTriggerEntityKeys.deleted)
                                         .is(false)))
            .schedulingType(IRREGULAR_SKIP_MISSED)
            .persistenceProvider(new SpringPersistenceProvider<>(mongoTemplate))
            .redistribute(true));
  }

  @Override
  public void handle(NGTriggerEntity entity) {
    try (NgTriggerAutoLogContext ignore0 = new NgTriggerAutoLogContext("webhookId", entity.getWebhookId(),
             entity.getIdentifier(), entity.getTargetIdentifier(), entity.getProjectIdentifier(),
             entity.getOrgIdentifier(), entity.getAccountId(), AutoLogContext.OverrideBehavior.OVERRIDE_ERROR)) {
      String runtimeInputYaml = null;
      try {
        SecurityContextBuilder.setContext(
            new ServicePrincipal(AuthorizationServiceHeader.PIPELINE_SERVICE.getServiceId()));
        SourcePrincipalContextBuilder.setSourcePrincipal(
            new ServicePrincipal(AuthorizationServiceHeader.PIPELINE_SERVICE.getServiceId()));

        TriggerDetails triggerDetails = TriggerDetails.builder()
                                            .ngTriggerEntity(entity)
                                            .ngTriggerConfigV2(ngTriggerElementMapper.toTriggerConfigV2(entity))
                                            .build();
        TriggerWebhookEvent triggerWebhookEvent = TriggerWebhookEvent.builder()
                                                      .accountId(entity.getAccountId())
                                                      .orgIdentifier(entity.getOrgIdentifier())
                                                      .projectIdentifier(entity.getProjectIdentifier())
                                                      .triggerIdentifier(entity.getIdentifier())
                                                      .uuid("Cron_" + UUIDGenerator.generateUuid())
                                                      .build();

        if (isEmpty(triggerDetails.getNgTriggerConfigV2().getPipelineBranchName())
            && isEmpty(triggerDetails.getNgTriggerConfigV2().getInputSetRefs())) {
          runtimeInputYaml = triggerDetails.getNgTriggerConfigV2().getInputYaml();
        } else {
          SecurityContextBuilder.setContext(
              new ServicePrincipal(AuthorizationServiceHeader.PIPELINE_SERVICE.getServiceId()));
          SourcePrincipalContextBuilder.setSourcePrincipal(
              new ServicePrincipal(AuthorizationServiceHeader.PIPELINE_SERVICE.getServiceId()));
          runtimeInputYaml = triggerExecutionHelper.fetchInputSetYAML(triggerDetails, null);
        }

        PlanExecution response = ngTriggerExecutionHelper.resolveRuntimeInputAndSubmitExecutionRequest(triggerDetails,
            TriggerPayload.newBuilder().setType(Type.SCHEDULED).build(), triggerWebhookEvent, null, runtimeInputYaml);
        triggerEventHistoryRepository.save(toHistoryRecord(entity, "TARGET_EXECUTION_REQUESTED",
            "Pipeline execution was requested successfully", false, response, runtimeInputYaml));
        log.info("Execution started for cron trigger: " + entity + " with response " + response);
      } catch (Exception e) {
        triggerEventHistoryRepository.save(
            toHistoryRecord(entity, "EXCEPTION_WHILE_PROCESSING", e.getMessage(), true, null, runtimeInputYaml));
        log.error("Exception while triggering cron. Please check", e);
      }
    }
  }
  private TriggerEventHistory toHistoryRecord(NGTriggerEntity entity, String finalStatus, String message,
      boolean exceptionOccurred, PlanExecution planExecution, String runtimeInputYaml) {
    TriggerEventHistoryBuilder triggerEventHistoryBuilder = TriggerEventHistory.builder()
                                                                .accountId(entity.getAccountId())
                                                                .orgIdentifier(entity.getOrgIdentifier())
                                                                .projectIdentifier(entity.getProjectIdentifier())
                                                                .targetIdentifier(entity.getTargetIdentifier())
                                                                .eventCreatedAt(System.currentTimeMillis())
                                                                .finalStatus(finalStatus)
                                                                .message(message)
                                                                .exceptionOccurred(exceptionOccurred)
                                                                .triggerIdentifier(entity.getIdentifier());

    if (planExecution != null) {
      triggerEventHistoryBuilder.targetExecutionSummary(TargetExecutionSummary.builder()
                                                            .runtimeInput(runtimeInputYaml)
                                                            .planExecutionId(planExecution.getUuid())
                                                            .startTs(planExecution.getStartTs())
                                                            .triggerId(entity.getIdentifier())
                                                            .executionStatus(planExecution.getStatus().name())
                                                            .targetId(entity.getTargetIdentifier())
                                                            .build());
    }

    return triggerEventHistoryBuilder.build();
  }
}
