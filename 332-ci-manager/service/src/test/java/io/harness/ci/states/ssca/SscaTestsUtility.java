/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.states.ssca;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.plancreator.steps.common.SpecParameters;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.plan.ExecutionMetadata;
import io.harness.pms.plan.execution.SetupAbstractionKeys;

import java.util.HashMap;
import lombok.experimental.UtilityClass;

@UtilityClass
@OwnedBy(HarnessTeam.SSCA)
public class SscaTestsUtility {
  public String STEP_IDENTIFIER = "stepIdentifier";
  public String ACCOUNT_ID = "accountId";
  public String ORG_ID = "orgId";
  public String PROJECT_ID = "projectId";
  public String PIPELINE_ID = "pipelineId";
  public String STEP_EXECUTION_ID = "runtimeId";

  public Ambiance getAmbiance() {
    HashMap<String, String> setupAbstractions = new HashMap<>();
    setupAbstractions.put(SetupAbstractionKeys.accountId, ACCOUNT_ID);
    setupAbstractions.put(SetupAbstractionKeys.orgIdentifier, ORG_ID);
    setupAbstractions.put(SetupAbstractionKeys.projectIdentifier, PROJECT_ID);

    return Ambiance.newBuilder()
        .setMetadata(ExecutionMetadata.newBuilder().setPipelineIdentifier(PIPELINE_ID).setRunSequence(1).build())
        .putAllSetupAbstractions(setupAbstractions)
        .addLevels(Level.newBuilder()
                       .setRuntimeId(STEP_EXECUTION_ID)
                       .setIdentifier("identifierId")
                       .setOriginalIdentifier("originalIdentifierId")
                       .setRetryIndex(1)
                       .build())
        .build();
  }

  public StepElementParameters getStepElementParameters(SpecParameters specParameters) {
    return StepElementParameters.builder().identifier(STEP_IDENTIFIER).name("name").spec(specParameters).build();
  }
}
