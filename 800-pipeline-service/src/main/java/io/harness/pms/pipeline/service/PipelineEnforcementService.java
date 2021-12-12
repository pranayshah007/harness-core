package io.harness.pms.pipeline.service;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.enforcement.constants.FeatureRestrictionName;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.yaml.YamlField;

import java.util.Map;
import java.util.Set;

@OwnedBy(HarnessTeam.PIPELINE)
public interface PipelineEnforcementService {
  boolean isFeatureRestricted(String accountId, String featureRestrictionName);

  Map<FeatureRestrictionName, Boolean> getFeatureRestrictionMap(
      String accountId, Set<String> featureRestrictionNameList);

  Set<FeatureRestrictionName> getDisabledFeatureRestrictionNames(
      String accountId, Set<String> featureRestrictionNameList);

  /**
   * Check and validate the feature restriction on the given stepTypes
   * Also, validates the deployments and builds feature restriction check if stepType contains module specific stage
   */
  void validatePipelineExecutionRestriction(String accountId, Set<StepType> stepTypes);

  void validateExecutionEnforcementsBasedOnStage(String accountId, YamlField pipelineField);
}
