/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ngtriggers.validations.impl;
import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.ngtriggers.Constants.ARTIFACT_REF;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.exception.InvalidRequestException;
import io.harness.ngtriggers.beans.dto.TriggerDetails;
import io.harness.ngtriggers.beans.source.artifact.ArtifactType;
import io.harness.ngtriggers.buildtriggers.helpers.BuildTriggerHelper;
import io.harness.ngtriggers.buildtriggers.helpers.dtos.BuildTriggerOpsData;
import io.harness.ngtriggers.buildtriggers.helpers.generator.GeneratorFactory;
import io.harness.ngtriggers.buildtriggers.helpers.generator.PollingItemGenerator;
import io.harness.ngtriggers.helpers.TriggerHelper;
import io.harness.ngtriggers.validations.TriggerValidator;
import io.harness.ngtriggers.validations.ValidationResult;
import io.harness.ngtriggers.validations.ValidationResult.ValidationResultBuilder;
import io.harness.polling.contracts.PollingItem;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_TRIGGERS})
@Singleton
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
@OwnedBy(PIPELINE)
public class ArtifactTriggerValidator implements TriggerValidator {
  private final BuildTriggerHelper validationHelper;
  private final GeneratorFactory generatorFactory;
  private static final List<String> artifactTypesSupported = new ArrayList<>();

  static {
    for (ArtifactType artifactType : ArtifactType.values()) {
      artifactTypesSupported.add(artifactType.getValue());
    }
  }

  @Override
  public ValidationResult validate(TriggerDetails triggerDetails) {
    ValidationResultBuilder builder = ValidationResult.builder().success(true);
    try {
      Optional<String> pipelineYmlOptional = validationHelper.fetchResolvedTemplatesPipelineForTrigger(triggerDetails);

      if (pipelineYmlOptional.isEmpty()) {
        return builder.success(false).message("Pipeline doesn't exists").build();
      }

      // make sure, stage and artifact identifiers are given
      if (!triggerDetails.getNgTriggerEntity().getWithServiceV2()) {
        validationHelper.verifyStageAndBuildRef(triggerDetails, ARTIFACT_REF);
      }

      String pipelineYml = pipelineYmlOptional.get();
      BuildTriggerOpsData buildTriggerOpsData =
          validationHelper.generateBuildTriggerOpsDataForArtifact(triggerDetails, pipelineYml);
      // stageRef & manifestRef exists
      if (!triggerDetails.getNgTriggerEntity().getWithServiceV2()
          && isEmpty(buildTriggerOpsData.getPipelineBuildSpecMap())) {
        throw new InvalidRequestException(
            "Artifact With Given StageIdentifier and ArtifactRef in Trigger does not exist in Pipeline");
      }

      // type is validated {Gcr, DockerRegistry}
      validationHelper.validateBuildType(buildTriggerOpsData);

      validateBasedOnArtifactType(buildTriggerOpsData);
    } catch (Exception e) {
      String message = new StringBuilder(128)
                           .append("Exception while applying ArtifactTriggerValidation for Trigger: ")
                           .append(TriggerHelper.getTriggerRef(triggerDetails.getNgTriggerEntity()))
                           .toString();
      log.error(message, e);

      builder.success(false).message(new StringBuilder("Error while validating Artifact Trigger Yaml. Exception: ")
                                         .append(e.getMessage())
                                         .toString());
    }

    return builder.build();
  }

  public void validateBasedOnArtifactType(BuildTriggerOpsData buildTriggerOpsData) {
    String typeFromTrigger = validationHelper.fetchBuildType(buildTriggerOpsData.getTriggerSpecMap());
    if (!artifactTypesSupported.contains(typeFromTrigger)) {
      throw new InvalidRequestException(
          new StringBuilder(128)
              .append("Artifact Type in Trigger (")
              .append(typeFromTrigger)
              .append(") is not supported. Supported artifact types are [Gcr, Ecr, DockerRegistry]")
              .toString());
    }

    PollingItemGenerator pollingItemGenerator = generatorFactory.retrievePollingItemGenerator(buildTriggerOpsData);
    if (pollingItemGenerator == null) {
      throw new InvalidRequestException(
          "Failed to find Polling Generator For Trigger. Please Check Manifest Config In Trigger");
    }

    PollingItem pollingItem = pollingItemGenerator.generatePollingItem(buildTriggerOpsData);
    validationHelper.validatePollingItemForArtifact(pollingItem);
  }
}
