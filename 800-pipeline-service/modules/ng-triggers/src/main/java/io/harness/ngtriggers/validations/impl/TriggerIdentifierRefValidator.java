/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ngtriggers.validations.impl;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import static org.apache.commons.lang3.StringUtils.isBlank;

import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.InvalidArgumentsException;
import io.harness.ngtriggers.beans.dto.TriggerDetails;
import io.harness.ngtriggers.beans.entity.NGTriggerEntity;
import io.harness.ngtriggers.validations.TriggerValidator;
import io.harness.ngtriggers.validations.ValidationResult;
import io.harness.ngtriggers.validations.ValidationResult.ValidationResultBuilder;

@OwnedBy(PIPELINE)
public class TriggerIdentifierRefValidator implements TriggerValidator {
  @Override
  public ValidationResult validate(TriggerDetails triggerDetails) {
    ValidationResultBuilder builder = ValidationResult.builder().success(true);
    boolean success = true;
    StringBuilder message = new StringBuilder(512);

    NGTriggerEntity ngTriggerEntity = triggerDetails.getNgTriggerEntity();
    if (ngTriggerEntity == null) {
      throw new InvalidArgumentsException("Trigger Entity was NULL");
    }

    if (isBlank(ngTriggerEntity.getIdentifier())) {
      success = false;
      message.append("Identifier can not be null for trigger\n");
    }

    if (isBlank(ngTriggerEntity.getName())) {
      success = false;
      message.append("Name can not be null for trigger\n");
    }

    if (isBlank(ngTriggerEntity.getAccountId())) {
      success = false;
      message.append("AccountId can not be null for trigger\n");
    }

    if (isBlank(ngTriggerEntity.getOrgIdentifier())) {
      success = false;
      message.append("OrgIdentifier can not be null for trigger\n");
    }

    if (isBlank(ngTriggerEntity.getProjectIdentifier())) {
      success = false;
      message.append("ProjectIdentifier can not be null for trigger\n");
    }

    if (isBlank(ngTriggerEntity.getTargetIdentifier())) {
      success = false;
      message.append("PipelineIdentifier can not be null for trigger\n");
    }

    if (!success) {
      builder.success(false).message(message.toString());
    }

    return builder.build();
  }
}
