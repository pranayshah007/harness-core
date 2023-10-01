/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.sdk.core.steps.executables;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.AsyncExecutableResponse;
import io.harness.pms.sdk.core.steps.Step;
import io.harness.pms.sdk.core.steps.io.PassThroughData;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepParameters;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.tasks.ResponseData;

import java.util.List;
import java.util.Map;

/**
 * An executable interface for async activities. This is used when you want to do async activities other that spawning a
 * delegate task. If your step acts as a wrapper to spawning a delegate task use {@link
 * TaskExecutable}
 *
 * Interface Signature Details:
 *
 * executeAsync: This responds with set of callback ids upon completion of the job associated with this the handleAsync
 * is called with the job response
 *
 * handleAsyncResponse: This concludes this step and responds with the {@link StepResponse}. The result of the job is
 * supplied as parameter in responseDataMap. The key is the callbackId supplied in method above
 *
 */
@OwnedBy(PIPELINE)
public interface AsyncExecutable<T extends StepParameters>
    extends Step<T>, Abortable<T, AsyncExecutableResponse>, Failable<T>, Expirable<T, AsyncExecutableResponse>,
            Progressable<T> {
  AsyncExecutableResponse executeAsync(
      Ambiance ambiance, T stepParameters, StepInputPackage inputPackage, PassThroughData passThroughData);

  // TODO : There a couple of options we can explore to make this better/optimised
  // 1. Implement a waitMode in wait engine, which would finish the wait instance if any response received is failed
  // 2. Combine this with progress updates

  // For optimizations this will only be invoked if you have multiple callback ids if not we just ignore this
  default void handleForCallbackId(
      Ambiance ambiance, T stepParameters, List<String> allCallbackIds, String callbackId, ResponseData responseData){
      // NOOP : By default this is noop
  };

  StepResponse handleAsyncResponse(Ambiance ambiance, T stepParameters, Map<String, ResponseData> responseDataMap);

  @Override
  default void handleFailureInterrupt(Ambiance ambiance, T stepParameters, Map<String, String> metadata) {
    // NOOP : By default this is noop as task failure is handled by the PMS but you are free to override it
  }

  @Override
  default void handleExpire(Ambiance ambiance, T stepParameters, AsyncExecutableResponse executableResponse) {
    // NOOP : By default this is noop as task expire is handled by the PMS but you are free to override it
  }
}
