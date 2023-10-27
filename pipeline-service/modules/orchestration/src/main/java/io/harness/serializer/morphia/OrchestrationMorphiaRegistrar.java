/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.serializer.morphia;
import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.OrchestrationEventLog;
import io.harness.engine.expressions.usages.beans.ExpressionUsagesEntity;
import io.harness.engine.interrupts.AbortInterruptCallback;
import io.harness.engine.pms.resume.EngineResumeAllCallback;
import io.harness.engine.pms.resume.EngineResumeCallback;
import io.harness.engine.pms.resume.EngineWaitRetryCallback;
import io.harness.engine.pms.resume.EngineWaitRetryCallbackV2;
import io.harness.engine.progress.EngineProgressCallback;
import io.harness.morphia.MorphiaRegistrar;
import io.harness.morphia.MorphiaRegistrarHelperPut;

import java.util.Set;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(CDC)
public class OrchestrationMorphiaRegistrar implements MorphiaRegistrar {
  @Override
  public void registerClasses(Set<Class> set) {
    set.add(OrchestrationEventLog.class);
    set.add(ExpressionUsagesEntity.class);
  }

  @Override
  public void registerImplementationClasses(MorphiaRegistrarHelperPut h, MorphiaRegistrarHelperPut w) {
    // Engine Callback
    h.put("engine.resume.EngineResumeAllCallback", EngineResumeAllCallback.class);
    h.put("engine.resume.EngineResumeCallback", EngineResumeCallback.class);
    h.put("engine.resume.EngineWaitRetryCallback", EngineWaitRetryCallback.class);
    h.put("engine.resume.EngineWaitRetryCallbackV2", EngineWaitRetryCallbackV2.class);
    h.put("engine.progress.EngineProgressCallback", EngineProgressCallback.class);
    h.put("engine.interrupts.InterruptCallback", AbortInterruptCallback.class);
  }
}
