/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.sdk.core.steps.executables;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.ChildrenExecutableResponse;
import io.harness.pms.sdk.core.steps.Step;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepParameters;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.sdk.core.steps.io.StepResponseNotifyData;
import io.harness.tasks.ResponseData;

import java.util.Map;

/**
 * Use this Executable when you want to spawn multiple children in one go example fork state.
 *
 * InterfaceDefinition:
 *
 * obtainChildren: This expects a list of nodeIds which will be spawned in one go (in parallel). You can also supply any
 * additional inputs per child.
 *
 * handleChildrenResponse: All the responses from children will be accumulated in the response data map. The keys will
 * be {@link StepResponseNotifyData}
 */

@OwnedBy(CDC)
public interface ChildrenExecutable<T extends StepParameters> extends Step<T> {
  ChildrenExecutableResponse obtainChildren(Ambiance ambiance, T stepParameters, StepInputPackage inputPackage);

  StepResponse handleChildrenResponse(Ambiance ambiance, T stepParameters, Map<String, ResponseData> responseDataMap);
}
