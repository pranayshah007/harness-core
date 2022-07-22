/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.sdk.core.execution;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.InvalidRequestException;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.refobjects.RefObject;
import io.harness.pms.data.OrchestrationRefType;
import io.harness.pms.sdk.core.resolver.Resolver;
import io.harness.pms.sdk.core.resolver.outcome.OutcomeService;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.sdk.core.steps.io.ResolvedRefInput;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepInputPackage.StepInputPackageBuilder;

import com.google.inject.Inject;
import java.util.List;

@OwnedBy(PIPELINE)
@SuppressWarnings("rawtypes")
public class EngineObtainmentHelper {
  @Inject private ExecutionSweepingOutputService sweepingOutputService;
  @Inject private OutcomeService outcomeService;

  public StepInputPackage obtainInputPackage(Ambiance ambiance, List<RefObject> refObjects) {
    StepInputPackageBuilder inputPackageBuilder = StepInputPackage.builder();

    if (!isEmpty(refObjects)) {
      for (RefObject refObject : refObjects) {
        Resolver resolver = getResolver(refObject);
        inputPackageBuilder.input(
            ResolvedRefInput.builder().transput(resolver.resolve(ambiance, refObject)).refObject(refObject).build());
      }
    }
    return inputPackageBuilder.build();
  }

  public Resolver getResolver(RefObject refObject) {
    Resolver resolver;
    if (refObject.getRefType().getType().equals(OrchestrationRefType.SWEEPING_OUTPUT)) {
      resolver = sweepingOutputService;
    } else if (refObject.getRefType().getType().equals(OrchestrationRefType.OUTCOME)) {
      resolver = outcomeService;
    } else {
      throw new InvalidRequestException("Cannot Find Resolver for refType :" + refObject.getRefType().getType());
    }
    return resolver;
  }
}
