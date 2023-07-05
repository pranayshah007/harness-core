/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.pipeline.stages.steps;

import io.harness.idp.pipeline.stages.IDPStageSpecParams;
import io.harness.plancreator.steps.common.StageElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.ChildExecutableResponse;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.sdk.core.steps.executables.ChildExecutable;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.tasks.ResponseData;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

import static io.harness.steps.SdkCoreStepUtils.createStepResponseFromChildResponse;

@Slf4j
public class IDPStageStep implements ChildExecutable<StageElementParameters> {
    public static final StepType STEP_TYPE =
            StepType.newBuilder().setType("IDPStage").setStepCategory(StepCategory.STAGE).build();

    @Override
    public Class<StageElementParameters> getStepParametersClass() {
        return StageElementParameters.class;
    }

    @Override
    public ChildExecutableResponse obtainChild(
            Ambiance ambiance, StageElementParameters stepParameters, StepInputPackage inputPackage) {
        log.info("Executing custom stage with params [{}]", stepParameters);
        IDPStageSpecParams specParameters = (IDPStageSpecParams) stepParameters.getSpecConfig();
        String executionNodeId = specParameters.getChildNodeID();
        return ChildExecutableResponse.newBuilder().setChildNodeId(executionNodeId).build();
    }

    @Override
    public StepResponse handleChildResponse(
            Ambiance ambiance, StageElementParameters stepParameters, Map<String, ResponseData> responseDataMap) {
        log.info("Executed custom stage [{}]", stepParameters);
        return createStepResponseFromChildResponse(responseDataMap);
    }

}
