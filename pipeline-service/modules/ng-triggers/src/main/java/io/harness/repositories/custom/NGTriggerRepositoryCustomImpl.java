/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.repositories.custom;
import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.data.structure.EmptyPredicate;
import io.harness.ngtriggers.beans.entity.NGTriggerEntity;
import io.harness.ngtriggers.beans.entity.NGTriggerEntity.NGTriggerEntityKeys;
import io.harness.ngtriggers.beans.entity.metadata.WebhookRegistrationStatus;
import io.harness.ngtriggers.beans.entity.metadata.status.PollingSubscriptionStatus;
import io.harness.ngtriggers.beans.entity.metadata.status.StatusResult;
import io.harness.ngtriggers.beans.entity.metadata.status.TriggerStatus;
import io.harness.ngtriggers.beans.entity.metadata.status.TriggerStatus.TriggerStatusKeys;
import io.harness.ngtriggers.beans.entity.metadata.status.ValidationStatus;
import io.harness.ngtriggers.beans.entity.metadata.status.WebhookAutoRegistrationStatus;
import io.harness.ngtriggers.beans.source.TriggerUpdateCount;
import io.harness.ngtriggers.mapper.TriggerFilterHelper;
import io.harness.springdata.PersistenceUtils;

import com.google.inject.Inject;
import com.mongodb.client.result.DeleteResult;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.repository.support.PageableExecutionUtils;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_TRIGGERS})
@AllArgsConstructor(access = AccessLevel.PRIVATE, onConstructor = @__({ @Inject }))
@Slf4j
@OwnedBy(PIPELINE)
public class NGTriggerRepositoryCustomImpl implements NGTriggerRepositoryCustom {
  private final MongoTemplate mongoTemplate;

  @Override
  public Page<NGTriggerEntity> findAll(Criteria criteria, Pageable pageable) {
    Query query = new Query(criteria).with(pageable);
    List<NGTriggerEntity> triggers = mongoTemplate.find(query, NGTriggerEntity.class);

    triggers = updateTriggerStatus(triggers);

    return PageableExecutionUtils.getPage(
        triggers, pageable, () -> mongoTemplate.count(Query.of(query).limit(-1).skip(-1), NGTriggerEntity.class));
  }

  public static List<NGTriggerEntity> updateTriggerStatus(List<NGTriggerEntity> triggers) {
    for (NGTriggerEntity trigger : triggers) {
      TriggerStatus triggerStatus = trigger.getTriggerStatus();

      if (triggerStatus == null) {
        TriggerStatus status = TriggerStatus.builder().status(StatusResult.FAILED).build();
        trigger.setTriggerStatus(status);
        continue;
      }

      PollingSubscriptionStatus pollingSubscriptionStatus = triggerStatus.getPollingSubscriptionStatus();
      ValidationStatus validationStatus = triggerStatus.getValidationStatus();
      WebhookAutoRegistrationStatus webhookAutoRegistrationStatus = triggerStatus.getWebhookAutoRegistrationStatus();

      List<String> detailedMessages = new ArrayList<>();

      if ((pollingSubscriptionStatus != null && pollingSubscriptionStatus.getStatusResult() == StatusResult.FAILED)
          || (validationStatus != null && validationStatus.getStatusResult() == StatusResult.FAILED)
          || (webhookAutoRegistrationStatus != null
              && (webhookAutoRegistrationStatus.getRegistrationResult() == WebhookRegistrationStatus.FAILED
                  || webhookAutoRegistrationStatus.getRegistrationResult() == WebhookRegistrationStatus.TIMEOUT
                  || webhookAutoRegistrationStatus.getRegistrationResult() == WebhookRegistrationStatus.ERROR))) {
        triggerStatus.setStatus(StatusResult.FAILED);

        if (pollingSubscriptionStatus != null
            && EmptyPredicate.isNotEmpty(pollingSubscriptionStatus.getDetailedMessage())) {
          detailedMessages.add(pollingSubscriptionStatus.getDetailedMessage());
        }
        if (validationStatus != null && EmptyPredicate.isNotEmpty(validationStatus.getDetailedMessage())) {
          detailedMessages.add(validationStatus.getDetailedMessage());
        }
        if (webhookAutoRegistrationStatus != null
            && EmptyPredicate.isNotEmpty(webhookAutoRegistrationStatus.getDetailedMessage())) {
          detailedMessages.add(webhookAutoRegistrationStatus.getDetailedMessage());
        }
      } else if (pollingSubscriptionStatus != null
          && pollingSubscriptionStatus.getStatusResult() == StatusResult.PENDING) {
        // TODO: (Vinicius) Set trigger status to PENDING here when UI is capable of handling it.
        triggerStatus.setStatus(StatusResult.SUCCESS);
      } else {
        triggerStatus.setStatus(StatusResult.SUCCESS);
      }

      if (pollingSubscriptionStatus != null) {
        triggerStatus.setLastPolled(pollingSubscriptionStatus.getLastPolled());
        triggerStatus.setLastPollingUpdate(pollingSubscriptionStatus.getLastPollingUpdate());
      }
      triggerStatus.setDetailMessages(detailedMessages);
      trigger.setTriggerStatus(triggerStatus);
    }
    return triggers;
  }

  @Override
  public NGTriggerEntity update(Criteria criteria, NGTriggerEntity ngTriggerEntity) {
    Query query = new Query(criteria);
    Update update = TriggerFilterHelper.getUpdateOperations(ngTriggerEntity);
    RetryPolicy<Object> retryPolicy = getRetryPolicy(
        "[Retrying]: Failed updating Trigger; attempt: {}", "[Failed]: Failed updating Trigger; attempt: {}");
    return Failsafe.with(retryPolicy)
        .get(()
                 -> mongoTemplate.findAndModify(
                     query, update, new FindAndModifyOptions().returnNew(true), NGTriggerEntity.class));
  }

  @Override
  public NGTriggerEntity updateValidationStatus(Criteria criteria, NGTriggerEntity ngTriggerEntity) {
    Query query = new Query(criteria);
    Update update = new Update();
    update.set(NGTriggerEntityKeys.triggerStatus, ngTriggerEntity.getTriggerStatus());
    update.set(NGTriggerEntityKeys.enabled, ngTriggerEntity.getEnabled());
    RetryPolicy<Object> retryPolicy = getRetryPolicy(
        "[Retrying]: Failed updating Trigger; attempt: {}", "[Failed]: Failed updating Trigger; attempt: {}");
    return Failsafe.with(retryPolicy)
        .get(()
                 -> mongoTemplate.findAndModify(
                     query, update, new FindAndModifyOptions().returnNew(true), NGTriggerEntity.class));
  }

  @Override
  public NGTriggerEntity updateValidationStatusAndMetadata(Criteria criteria, NGTriggerEntity ngTriggerEntity) {
    Query query = new Query(criteria);
    Update update = new Update();
    update.set(NGTriggerEntityKeys.triggerStatus, ngTriggerEntity.getTriggerStatus());
    update.set(NGTriggerEntityKeys.metadata, ngTriggerEntity.getMetadata());
    RetryPolicy<Object> retryPolicy = getRetryPolicy(
        "[Retrying]: Failed updating Trigger; attempt: {}", "[Failed]: Failed updating Trigger; attempt: {}");
    return Failsafe.with(retryPolicy)
        .get(()
                 -> mongoTemplate.findAndModify(
                     query, update, new FindAndModifyOptions().returnNew(true), NGTriggerEntity.class));
  }

  @Override
  public DeleteResult hardDelete(Criteria criteria) {
    Query query = new Query(criteria);
    RetryPolicy<Object> retryPolicy = getRetryPolicy(
        "[Retrying]: Failed hard deleting Trigger; attempt: {}", "[Failed]: Failed deleting Trigger; attempt: {}");
    return Failsafe.with(retryPolicy).get(() -> mongoTemplate.remove(query, NGTriggerEntity.class));
  }

  @Override
  public TriggerUpdateCount updateTriggerYaml(List<NGTriggerEntity> ngTriggerEntityList) {
    BulkOperations bulkOperations = mongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED, NGTriggerEntity.class);
    for (NGTriggerEntity triggerEntity : ngTriggerEntityList) {
      Update update = new Update();
      update.set(NGTriggerEntityKeys.yaml, triggerEntity.getYaml());
      Criteria criteria = Criteria.where(NGTriggerEntityKeys.accountId)
                              .is(triggerEntity.getAccountId())
                              .and(NGTriggerEntityKeys.orgIdentifier)
                              .is(triggerEntity.getOrgIdentifier())
                              .and(NGTriggerEntityKeys.projectIdentifier)
                              .is(triggerEntity.getProjectIdentifier())
                              .and(NGTriggerEntityKeys.targetIdentifier)
                              .is(triggerEntity.getTargetIdentifier())
                              .and(NGTriggerEntityKeys.identifier)
                              .is(triggerEntity.getIdentifier());
      bulkOperations.updateOne(new Query(criteria), update);
    }
    try {
      long successTriggerUpdateCount = bulkOperations.execute().getModifiedCount();
      long failedTriggerUpdateCount = ngTriggerEntityList.size() - successTriggerUpdateCount;
      return TriggerUpdateCount.builder()
          .failureCount(failedTriggerUpdateCount)
          .successCount(successTriggerUpdateCount)
          .build();
    } catch (Exception ex) {
      log.error("Error while updating trigger yaml", ex);
      throw ex;
    }
  }

  @Override
  public boolean updateManyTriggerPollingSubscriptionStatusBySignatures(String accountId, List<String> signatures,
      boolean status, String errorMessage, List<String> versions, Long timestamp) {
    Update update = new Update();
    PollingSubscriptionStatus pollingSubscriptionStatus =
        PollingSubscriptionStatus.builder()
            .statusResult(status ? StatusResult.SUCCESS : StatusResult.FAILED)
            .detailedMessage(errorMessage)
            .lastPolled(versions)
            .lastPollingUpdate(timestamp)
            .build();
    update.set(NGTriggerEntityKeys.triggerStatus + "." + TriggerStatusKeys.pollingSubscriptionStatus,
        pollingSubscriptionStatus);
    Query query =
        new Query(TriggerFilterHelper.createCriteriaFormBuildTriggerUsingAccIdAndSignature(accountId, signatures));
    RetryPolicy<Object> retryPolicy = getRetryPolicy(
        "[Retrying]: Failed updating Trigger; attempt: {}", "[Failed]: Failed updating Trigger; attempt: {}");
    Failsafe.with(retryPolicy).get(() -> mongoTemplate.updateMulti(query, update, NGTriggerEntity.class));
    return true;
  }

  private RetryPolicy<Object> getRetryPolicy(String failedAttemptMessage, String failureMessage) {
    return PersistenceUtils.getRetryPolicy(failedAttemptMessage, failureMessage);
  }
}
