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
import lombok.AllArgsConstructor;

@Singleton
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@OwnedBy(CI)
public class TriggerWebhookValidator {
  private final NGTriggerService ngTriggerService;

  public void applyValidationsForCustomWebhook(TriggerWebhookEvent triggerWebhookEvent) {
    ValidationResult validationResult = validateTriggers(triggerWebhookEvent);
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
    }
    return builder.build();
  }
}
