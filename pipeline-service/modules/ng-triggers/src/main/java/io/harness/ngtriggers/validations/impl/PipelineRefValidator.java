/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ngtriggers.validations.impl;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.ngtriggers.beans.dto.TriggerDetails;
import io.harness.ngtriggers.beans.entity.NGTriggerEntity;
import io.harness.ngtriggers.buildtriggers.helpers.BuildTriggerHelper;
import io.harness.ngtriggers.helpers.TriggerHelper;
import io.harness.ngtriggers.validations.TriggerValidator;
import io.harness.ngtriggers.validations.ValidationResult;
import io.harness.ngtriggers.validations.ValidationResult.ValidationResultBuilder;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Optional;
import lombok.AllArgsConstructor;

@Singleton
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@OwnedBy(PIPELINE)
public class PipelineRefValidator implements TriggerValidator {
  private final BuildTriggerHelper validationHelper;

  @Override
  public ValidationResult validate(TriggerDetails triggerDetails) {
    ValidationResultBuilder builder = ValidationResult.builder().success(true);
    if (triggerDetails.getNgTriggerConfigV2() != null
        && TriggerHelper.isBranchExpr(triggerDetails.getNgTriggerConfigV2().getPipelineBranchName())) {
      // Impossible to check if pipeline ref is valid in case pipelineBranchName is an expression.
      return builder.build();
    }
    NGTriggerEntity ngTriggerEntity = triggerDetails.getNgTriggerEntity();
    Optional<String> pipelineYmlOptional = validationHelper.fetchPipelineYamlForTrigger(triggerDetails);

    if (!pipelineYmlOptional.isPresent()) {
      String ref = new StringBuilder(128)
                       .append("Pipeline with Ref -> ")
                       .append(ngTriggerEntity.getAccountId())
                       .append(':')
                       .append(ngTriggerEntity.getOrgIdentifier())
                       .append(':')
                       .append(ngTriggerEntity.getProjectIdentifier())
                       .append(':')
                       .append(ngTriggerEntity.getTargetIdentifier())
                       .append(" does not exists")
                       .toString();
      builder.success(false).message(ref);
    }

    return builder.build();
  }
}
