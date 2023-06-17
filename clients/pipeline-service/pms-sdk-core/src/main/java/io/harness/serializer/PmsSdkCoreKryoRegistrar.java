/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.serializer;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.advisers.manualIntervention.ManualInterventionAdviserRollbackParameters;
import io.harness.advisers.nextstep.NextStageAdviserParameters;
import io.harness.advisers.nextstep.NextStepAdviserParameters;
import io.harness.advisers.pipelinerollback.OnFailPipelineRollbackParameters;
import io.harness.advisers.retry.RetryAdviserRollbackParameters;
import io.harness.advisers.rollback.OnFailRollbackParameters;
import io.harness.advisers.rollback.RollbackStrategy;
import io.harness.annotations.dev.OwnedBy;
import io.harness.async.AsyncResponseCallback;
import io.harness.pms.sdk.core.adviser.abort.OnAbortAdviserParameters;
import io.harness.pms.sdk.core.adviser.fail.OnFailAdviserParameters;
import io.harness.pms.sdk.core.adviser.ignore.IgnoreAdviserParameters;
import io.harness.pms.sdk.core.adviser.manualintervention.ManualInterventionAdviserParameters;
import io.harness.pms.sdk.core.adviser.markFailure.OnMarkFailureAdviserParameters;
import io.harness.pms.sdk.core.adviser.marksuccess.OnMarkSuccessAdviserParameters;
import io.harness.pms.sdk.core.adviser.proceedwithdefault.ProceedWithDefaultAdviserParameters;
import io.harness.pms.sdk.core.adviser.retry.RetryAdviserParameters;
import io.harness.pms.sdk.core.adviser.success.OnSuccessAdviserParameters;
import io.harness.pms.sdk.core.data.ExecutionSweepingOutput;
import io.harness.pms.sdk.core.execution.AsyncSdkProgressCallback;
import io.harness.pms.sdk.core.execution.AsyncSdkResumeCallback;
import io.harness.pms.sdk.core.execution.AsyncSdkSingleCallback;
import io.harness.pms.sdk.core.execution.AsyncTimeoutResponseData;
import io.harness.pms.sdk.core.execution.async.AsyncProgressData;
import io.harness.pms.sdk.core.plan.PlanNode;
import io.harness.pms.sdk.core.steps.io.PassThroughData;
import io.harness.pms.sdk.core.steps.io.StatusNotifyResponseData;
import io.harness.pms.sdk.core.steps.io.StepResponse.StepOutcome;
import io.harness.pms.sdk.core.steps.io.StepResponseNotifyData;
import io.harness.steps.fork.ForkStepParameters;
import io.harness.steps.matrix.StrategyMetadata;
import io.harness.steps.section.chain.SectionChainPassThroughData;
import io.harness.steps.section.chain.SectionChainStepParameters;

import com.esotericsoftware.kryo.Kryo;

@OwnedBy(PIPELINE)
public class PmsSdkCoreKryoRegistrar implements KryoRegistrar {
  @Override
  public void register(Kryo kryo) {
    kryo.register(StatusNotifyResponseData.class, 2502);
    kryo.register(StepOutcome.class, 2521);
    kryo.register(PassThroughData.class, 2535);
    kryo.register(StepResponseNotifyData.class, 2519);

    kryo.register(RetryAdviserParameters.class, 3103);
    kryo.register(OnSuccessAdviserParameters.class, 3104);
    kryo.register(OnFailAdviserParameters.class, 3105);
    kryo.register(IgnoreAdviserParameters.class, 3106);
    kryo.register(ManualInterventionAdviserParameters.class, 3107);
    kryo.register(OnMarkSuccessAdviserParameters.class, 3108);
    kryo.register(OnAbortAdviserParameters.class, 3109);
    kryo.register(OnMarkFailureAdviserParameters.class, 3110);

    // New classes here
    kryo.register(PlanNode.class, 88201);
    kryo.register(ExecutionSweepingOutput.class, 88202);
    kryo.register(AsyncSdkResumeCallback.class, 88204);
    kryo.register(AsyncSdkProgressCallback.class, 88205);
    kryo.register(AsyncSdkSingleCallback.class, 88206);
    kryo.register(AsyncResponseCallback.class, 88407);

    kryo.register(RetryAdviserRollbackParameters.class, 87801);
    kryo.register(RollbackStrategy.class, 87802);
    kryo.register(OnFailRollbackParameters.class, 87803);
    kryo.register(ManualInterventionAdviserRollbackParameters.class, 87804);
    kryo.register(NextStepAdviserParameters.class, 87805);

    kryo.register(ForkStepParameters.class, 3211);
    kryo.register(SectionChainStepParameters.class, 3214);
    kryo.register(SectionChainPassThroughData.class, 3217);
    kryo.register(StrategyMetadata.class, 878001);
    kryo.register(ProceedWithDefaultAdviserParameters.class, 878018);
    kryo.register(AsyncTimeoutResponseData.class, 878019);
    kryo.register(AsyncProgressData.class, 878020);
    kryo.register(OnFailPipelineRollbackParameters.class, 878021);
    kryo.register(NextStageAdviserParameters.class, 878022);
  }
}
