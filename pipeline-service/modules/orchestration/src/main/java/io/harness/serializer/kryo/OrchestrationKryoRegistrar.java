/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.serializer.kryo;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.OwnedBy;
import io.harness.concurrency.MaxConcurrentChildCallback;
import io.harness.engine.execution.ExecutionInputData;
import io.harness.engine.execution.PipelineStageResponseData;
import io.harness.engine.execution.WaitForExecutionInputCallback;
import io.harness.engine.governance.PolicyEvaluationFailureException;
import io.harness.engine.interrupts.AbortInterruptCallback;
import io.harness.engine.interrupts.UserMarkedFailureInterruptCallback;
import io.harness.engine.interrupts.callback.FailureInterruptCallback;
import io.harness.engine.interrupts.handlers.AbortAllInterruptCallback;
import io.harness.engine.interrupts.handlers.UserMarkedFailAllInterruptCallback;
import io.harness.engine.pms.execution.strategy.plan.PlanExecutionResumeCallback;
import io.harness.engine.pms.resume.EngineResumeAllCallback;
import io.harness.engine.pms.resume.EngineResumeCallback;
import io.harness.engine.pms.resume.EngineWaitRetryCallback;
import io.harness.engine.progress.EngineProgressCallback;
import io.harness.serializer.KryoRegistrar;

import com.esotericsoftware.kryo.Kryo;

@OwnedBy(CDC)
public class OrchestrationKryoRegistrar implements KryoRegistrar {
  @Override
  public void register(Kryo kryo) {
    kryo.register(EngineResumeAllCallback.class, 87001);
    kryo.register(EngineResumeCallback.class, 87002);
    kryo.register(EngineWaitRetryCallback.class, 87004);
    kryo.register(EngineProgressCallback.class, 87007);
    kryo.register(AbortInterruptCallback.class, 87008);
    kryo.register(AbortAllInterruptCallback.class, 87009);
    kryo.register(FailureInterruptCallback.class, 87010);
    kryo.register(PolicyEvaluationFailureException.class, 87011);
    kryo.register(ExecutionInputData.class, 87012);
    kryo.register(WaitForExecutionInputCallback.class, 87013);
    kryo.register(MaxConcurrentChildCallback.class, 87014);
    kryo.register(PipelineStageResponseData.class, 87015);
    kryo.register(PlanExecutionResumeCallback.class, 87016);
    kryo.register(UserMarkedFailureInterruptCallback.class, 87017);
    kryo.register(UserMarkedFailAllInterruptCallback.class, 87018);
  }
}
