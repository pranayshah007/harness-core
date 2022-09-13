/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ngtriggers.validations.impl;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.ngtriggers.Constants.MANIFEST_REF;
import static io.harness.ngtriggers.beans.source.ManifestType.HELM_MANIFEST;

import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.InvalidRequestException;
import io.harness.ngtriggers.beans.dto.TriggerDetails;
import io.harness.ngtriggers.buildtriggers.helpers.BuildTriggerHelper;
import io.harness.ngtriggers.buildtriggers.helpers.dtos.BuildTriggerOpsData;
import io.harness.ngtriggers.buildtriggers.helpers.generator.GeneratorFactory;
import io.harness.ngtriggers.buildtriggers.helpers.generator.PollingItemGenerator;
import io.harness.ngtriggers.helpers.TriggerHelper;
import io.harness.ngtriggers.validations.TriggerValidator;
import io.harness.ngtriggers.validations.ValidationResult;
import io.harness.ngtriggers.validations.ValidationResult.ValidationResultBuilder;
import io.harness.polling.contracts.PollingItem;

import com.fasterxml.jackson.databind.node.TextNode;
import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Optional;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Singleton
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
@OwnedBy(PIPELINE)
public class ManifestTriggerValidator implements TriggerValidator {
  private final BuildTriggerHelper validationHelper;
  private final GeneratorFactory generatorFactory;

  @Override
  public ValidationResult validate(TriggerDetails triggerDetails) {
    ValidationResultBuilder builder = ValidationResult.builder().success(true);

    try {
      Optional<String> pipelineYmlOptional =
          validationHelper.fetchResolvedTemplatesPipelineForTrigger(triggerDetails.getNgTriggerEntity());

      if (!pipelineYmlOptional.isPresent()) {
        return builder.success(false).message("Pipeline doesn't exists").build();
      }

      // make sure, stage and artifact identifiers are given
      if(triggerDetails.getNgTriggerEntity().getWithServiceV2() == false) {
        validationHelper.verifyStageAndBuildRef(triggerDetails, MANIFEST_REF);
      }

      String pipelineYml = pipelineYmlOptional.get();
      BuildTriggerOpsData buildTriggerOpsData =
          validationHelper.generateBuildTriggerOpsDataForManifest(triggerDetails, pipelineYml);

      // stageRef & manifestRef exists
      if (triggerDetails.getNgTriggerEntity().getWithServiceV2() == false && isEmpty(buildTriggerOpsData.getPipelineBuildSpecMap())) {
        throw new InvalidRequestException(
            "Manifest With Given StageIdentifier and ManifestRef in Trigger does not exist in Pipeline");
      }

      // type is validated {HemlChart}
      validationHelper.validateBuildType(buildTriggerOpsData);
      validateBasedOnManifestType(buildTriggerOpsData);
    } catch (Exception e) {
      log.error("Exception while applying ManifestTriggerValidation for Trigger: "
              + TriggerHelper.getTriggerRef(triggerDetails.getNgTriggerEntity()),
          e);
      builder.success(false).message(
          new StringBuilder("Exception while applying ManifestTriggerValidation for Trigger. Exception: ")
              .append(e.getMessage())
              .toString());
    }

    return builder.build();
  }

  @VisibleForTesting
  void validateBasedOnManifestType(BuildTriggerOpsData buildTriggerOpsData) {
    String typeFromTrigger = validationHelper.fetchBuildType(buildTriggerOpsData.getTriggerSpecMap());

    if (HELM_MANIFEST.getValue().equals(typeFromTrigger)) {
      validateForHelmChart(buildTriggerOpsData);
    }
  }

  @VisibleForTesting
  void validateForHelmChart(BuildTriggerOpsData buildTriggerOpsData) {

    if(buildTriggerOpsData.getTriggerDetails().getNgTriggerEntity().getWithServiceV2() == false) {
      // Only check when complete Store is not runtimeInput
      if (!buildTriggerOpsData.getPipelineBuildSpecMap().containsKey("spec.store")) {
        String storeTypeFromTrigger = validationHelper.fetchStoreTypeForHelm(buildTriggerOpsData);

        String storeTypeFromPipeline =
                ((TextNode) buildTriggerOpsData.getPipelineBuildSpecMap().get("spec.store.type")).asText();

        // Store type mismatch
        if (!storeTypeFromPipeline.equals(storeTypeFromTrigger)) {
          throw new InvalidRequestException(
                  String.format("Manifest Store Type in Trigger:%s does not match with Manifest Store Type in Pipeline %s",
                          storeTypeFromTrigger, storeTypeFromPipeline));
        }
      }

      // ChartVersion can not be a fixed value
      if (buildTriggerOpsData.getPipelineBuildSpecMap().containsKey("spec.chartVersion")) {
        String chartVersion =
                ((TextNode) buildTriggerOpsData.getPipelineBuildSpecMap().get("spec.chartVersion")).asText();
        if (!chartVersion.equals("<+input>")) {
          throw new InvalidRequestException(
                  "ChartVersion should not have fixed value in Pipeline when creating Manifest Trigger");
        }
      }
    }
    validateRuntimeInputsForHelmChart(buildTriggerOpsData);
  }

  @VisibleForTesting
  void validateRuntimeInputsForHelmChart(BuildTriggerOpsData buildTriggerOpsData) {
    PollingItemGenerator pollingItemGenerator = generatorFactory.retrievePollingItemGenerator(buildTriggerOpsData);
    if (pollingItemGenerator == null) {
      throw new InvalidRequestException(
          "Failed to find Polling Generator For Trigger. Please Check Manifest Config In Trigger");
    }

    PollingItem pollingItem = pollingItemGenerator.generatePollingItem(buildTriggerOpsData);
    validationHelper.validatePollingItemForHelmChart(pollingItem);
  }
}
