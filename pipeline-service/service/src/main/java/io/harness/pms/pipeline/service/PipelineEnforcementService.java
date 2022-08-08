/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.pipeline.service;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.enforcement.constants.FeatureRestrictionName;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.pipeline.PipelineEntity;
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

  void validateExecutionEnforcementsBasedOnStage(PipelineEntity pipelineEntity);
}
