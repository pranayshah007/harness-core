/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ngtriggers.validations;

import static io.harness.annotations.dev.HarnessTeam.CI;
import static io.harness.data.structure.EmptyPredicate.isEmpty;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.InvalidRequestException;
import io.harness.ngtriggers.beans.entity.NGTriggerEntity;
import io.harness.ngtriggers.beans.entity.TriggerWebhookEvent;
import io.harness.ngtriggers.service.NGTriggerService;
import io.harness.ngtriggers.validations.ValidationResult.ValidationResultBuilder;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import java.util.Optional;
import lombok.AllArgsConstructor;

@Singleton
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@OwnedBy(CI)
public class TriggerWebhookValidator {
  private final NGTriggerService ngTriggerService;

  public void applyValidationsForCustomWebhook(TriggerWebhookEvent triggerWebhookEvent, String webhookToken) {
    ValidationResult validationResult;
    if (webhookToken == null) {
      validationResult = validateTriggers(triggerWebhookEvent);
    } else {
      validationResult = validateTriggersFetchedViaWebhookToken(webhookToken);
    }
    if (!validationResult.isSuccess()) {
      throw new InvalidRequestException(validationResult.getMessage());
    }
  }

  private ValidationResult validateTriggers(TriggerWebhookEvent triggerWebhookEvent) {
    ValidationResultBuilder builder = ValidationResult.builder().success(true);
    List<NGTriggerEntity> triggersForAccount =
        ngTriggerService.findTriggersForCustomWehbook(triggerWebhookEvent, false, true);

    if (isEmpty(triggersForAccount)) {
      StringBuilder errorMsg = new StringBuilder(256)
                                   .append("No enabled custom trigger found for Account:")
                                   .append(triggerWebhookEvent.getAccountId())
                                   .append(", Org: ")
                                   .append(triggerWebhookEvent.getOrgIdentifier())
                                   .append(", Project: ")
                                   .append(triggerWebhookEvent.getProjectIdentifier());

      if (isNotBlank(triggerWebhookEvent.getPipelineIdentifier())) {
        errorMsg.append(", Pipeline: ").append(triggerWebhookEvent.getPipelineIdentifier());
      }
      if (isNotBlank(triggerWebhookEvent.getTriggerIdentifier())) {
        errorMsg.append(", Trigger: ").append(triggerWebhookEvent.getTriggerIdentifier());
      }
      builder.success(false).message(errorMsg.toString());
    } else if (!isEmpty(triggersForAccount.get(0).getCustomWebhookToken())) {
      String errorMsg =
          "This webhook url is no longer supported for newly created custom webhook triggers. We are migrating to new webhook urls using custom webhook tokens in the url.";
      builder.success(false).message(errorMsg);
    }
    return builder.build();
  }

  private ValidationResult validateTriggersFetchedViaWebhookToken(String webhookToken) {
    ValidationResultBuilder builder = ValidationResult.builder().success(true);
    Optional<NGTriggerEntity> customTriggerOptional =
        ngTriggerService.findTriggersForCustomWebhookViaCustomWebhookToken(webhookToken);

    if (customTriggerOptional.isPresent()) {
      NGTriggerEntity customTrigger = customTriggerOptional.get();
      if ((!customTrigger.getEnabled()) || (customTrigger.getDeleted())) {
        String errorMsg = "No enabled custom trigger found for the used custom webhook token: " + webhookToken;
        builder.success(false).message(errorMsg);
      }
    } else {
      String errorMsg = "No custom trigger found for the used custom webhook token: " + webhookToken;
      builder.success(false).message(errorMsg);
    }
    return builder.build();
  }
}
