/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.plan.creation.validator;

import static java.lang.String.format;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.InvalidRequestException;
import io.harness.logging.AutoLogContext;
import io.harness.pms.contracts.plan.PlanCreationBlobResponse;
import io.harness.pms.contracts.plan.PlanNodeProto;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.pipeline.service.PipelineEnforcementService;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.sdk.core.plan.creation.creators.PlanCreatorServiceHelper;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Set;
import java.util.stream.Collectors;

@OwnedBy(HarnessTeam.PIPELINE)
@Singleton
public class PlanCreationValidator implements CreationValidator<PlanCreationBlobResponse> {
  @Inject PipelineEnforcementService pipelineEnforcementService;

  @Override
  public void validate(String accountId, PlanCreationBlobResponse finalResponse) {
    PlanCreationContext ctx = PlanCreationContext.builder().globalContext(finalResponse.getContextMap()).build();
    try (AutoLogContext ignore = PlanCreatorServiceHelper.autoLogContext(ctx)) {
      if (EmptyPredicate.isNotEmpty(finalResponse.getDeps().getDependenciesMap())) {
        throw new InvalidRequestException(
            format("Unable to interpret nodes: %s", finalResponse.getDeps().getDependenciesMap().keySet().toString()));
      }
      if (EmptyPredicate.isEmpty(finalResponse.getStartingNodeId())) {
        throw new InvalidRequestException("Unable to find out starting node");
      }
      Set<StepType> stepTypes =
          finalResponse.getNodesMap().values().stream().map(PlanNodeProto::getStepType).collect(Collectors.toSet());
      pipelineEnforcementService.validatePipelineExecutionRestriction(accountId, stepTypes);
    }
  }
}
