/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.configfile.steps;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.configfile.ConfigFileOutcome;
import io.harness.cdng.configfile.ConfigFilesOutcome;
import io.harness.cdng.service.steps.helpers.ServiceStepsHelper;
import io.harness.cdng.stepsdependency.constants.OutcomeExpressionConstants;
import io.harness.data.structure.EmptyPredicate;
import io.harness.executions.steps.ExecutionNodeType;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.sdk.core.data.Outcome;
import io.harness.pms.sdk.core.plan.creation.yaml.StepOutcomeGroup;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.steps.fork.ForkStepParameters;
import io.harness.steps.fork.NGForkStep;
import io.harness.tasks.ResponseData;

import com.google.inject.Inject;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@OwnedBy(HarnessTeam.CDP)
public class ConfigFilesStep extends NGForkStep {
  public static final StepType STEP_TYPE = StepType.newBuilder()
                                               .setType(ExecutionNodeType.CONFIG_FILES.getName())
                                               .setStepCategory(StepCategory.FORK)
                                               .build();

  @Inject private ServiceStepsHelper serviceStepsHelper;

  @Override
  public StepResponse handleChildrenResponse(
      Ambiance ambiance, ForkStepParameters stepParameters, Map<String, ResponseData> responseDataMap) {
    List<Outcome> outcomes = serviceStepsHelper.getChildrenOutcomes(responseDataMap);
    ConfigFilesOutcome configFilesOutcome = new ConfigFilesOutcome();
    if (EmptyPredicate.isNotEmpty(outcomes)) {
      for (Outcome outcome : outcomes) {
        if (!(outcome instanceof ConfigFileOutcome)) {
          continue;
        }

        ConfigFileOutcome configFileOutcome = (ConfigFileOutcome) outcome;
        configFilesOutcome.put(configFileOutcome.getIdentifier(), configFileOutcome);
      }
    }

    StepResponse stepResponse = super.handleChildrenResponse(ambiance, stepParameters, responseDataMap);
    return stepResponse.withStepOutcomes(Collections.singleton(StepResponse.StepOutcome.builder()
                                                                   .name(OutcomeExpressionConstants.CONFIG_FILES)
                                                                   .outcome(configFilesOutcome)
                                                                   .group(StepOutcomeGroup.STAGE.name())
                                                                   .build()));
  }
}
