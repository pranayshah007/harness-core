/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ngtriggers.validations;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.ngtriggers.beans.source.NGTriggerType.ARTIFACT;
import static io.harness.ngtriggers.beans.source.NGTriggerType.MANIFEST;

import io.harness.annotations.dev.OwnedBy;
import io.harness.ngtriggers.beans.dto.TriggerDetails;
import io.harness.ngtriggers.beans.entity.NGTriggerEntity;
import io.harness.ngtriggers.mapper.NGTriggerElementMapper;
import io.harness.ngtriggers.validations.impl.ArtifactTriggerValidator;
import io.harness.ngtriggers.validations.impl.ManifestTriggerValidator;
import io.harness.ngtriggers.validations.impl.PipelineRefValidator;
import io.harness.ngtriggers.validations.impl.TriggerIdentifierRefValidator;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;

@Singleton
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@OwnedBy(PIPELINE)
public class TriggerValidationHandler {
  private final NGTriggerElementMapper ngTriggerElementMapper;
  private final TriggerIdentifierRefValidator triggerIdentifierRefValidator;
  private final PipelineRefValidator pipelineRefValidator;
  private final ManifestTriggerValidator manifestTriggerValidator;
  private final ArtifactTriggerValidator artifactTriggerValidator;

  public ValidationResult applyValidations(TriggerDetails triggerDetails) {
    List<TriggerValidator> applicableValidators = getApplicableValidators(triggerDetails);

    // Remove it later, as this should not happen
    if (isEmpty(applicableValidators)) {
      return ValidationResult.builder().success(true).build();
    }

    ValidationResult validationResult = null;
    for (TriggerValidator triggerValidator : applicableValidators) {
      validationResult = triggerValidator.validate(triggerDetails);
      if (!validationResult.isSuccess()) {
        break;
      }
    }

    return validationResult;
  }

  @VisibleForTesting
  List<TriggerValidator> getApplicableValidators(TriggerDetails triggerDetails) {
    List<TriggerValidator> validators = new ArrayList<>();
    NGTriggerEntity ngTriggerEntity = triggerDetails.getNgTriggerEntity();
    validators.add(triggerIdentifierRefValidator);
    validators.add(pipelineRefValidator);

    if (ngTriggerEntity.getType() == MANIFEST) {
      validators.add(manifestTriggerValidator);
    }

    if (ngTriggerEntity.getType() == ARTIFACT) {
      validators.add(artifactTriggerValidator);
    }

    return validators;
  }
}
