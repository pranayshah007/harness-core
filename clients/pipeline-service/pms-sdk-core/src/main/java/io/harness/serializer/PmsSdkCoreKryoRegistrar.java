/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.serializer;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.async.AsyncResponseCallback;
import io.harness.pms.sdk.core.adviser.abort.OnAbortAdviserParameters;
import io.harness.pms.sdk.core.adviser.fail.OnFailAdviserParameters;
import io.harness.pms.sdk.core.adviser.ignore.IgnoreAdviserParameters;
import io.harness.pms.sdk.core.adviser.manualintervention.ManualInterventionAdviserParameters;
import io.harness.pms.sdk.core.adviser.marksuccess.OnMarkSuccessAdviserParameters;
import io.harness.pms.sdk.core.adviser.retry.RetryAdviserParameters;
import io.harness.pms.sdk.core.adviser.success.OnSuccessAdviserParameters;
import io.harness.pms.sdk.core.data.ExecutionSweepingOutput;
import io.harness.pms.sdk.core.execution.AsyncSdkProgressCallback;
import io.harness.pms.sdk.core.execution.AsyncSdkResumeCallback;
import io.harness.pms.sdk.core.execution.AsyncSdkSingleCallback;
import io.harness.pms.sdk.core.plan.PlanNode;
import io.harness.pms.sdk.core.steps.io.PassThroughData;
import io.harness.pms.sdk.core.steps.io.StatusNotifyResponseData;
import io.harness.pms.sdk.core.steps.io.StepResponse.StepOutcome;
import io.harness.pms.sdk.core.steps.io.StepResponseNotifyData;

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

    // New classes here
    kryo.register(PlanNode.class, 88201);
    kryo.register(ExecutionSweepingOutput.class, 88202);
    kryo.register(AsyncSdkResumeCallback.class, 88204);
    kryo.register(AsyncSdkProgressCallback.class, 88205);
    kryo.register(AsyncSdkSingleCallback.class, 88206);
    kryo.register(AsyncResponseCallback.class, 88407);
  }
}
