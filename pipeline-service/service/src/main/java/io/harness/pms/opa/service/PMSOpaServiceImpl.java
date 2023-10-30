/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.opa.service;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.EmptyPredicate;
import io.harness.engine.executions.plan.PlanExecutionMetadataService;
import io.harness.engine.executions.plan.PlanExecutionService;
import io.harness.exception.InvalidRequestException;
import io.harness.execution.PlanExecution;
import io.harness.execution.PlanExecutionMetadata;
import io.harness.execution.PlanExecutionMetadata.PlanExecutionMetadataKeys;
import io.harness.opaclient.OpaUtils;
import io.harness.opaclient.model.PipelineOpaEvaluationContext;
import io.harness.opaclient.model.PipelineOpaEvaluationContext.PipelineOpaEvaluationContextBuilder;
import io.harness.opaclient.model.UserOpaEvaluationContext;
import io.harness.opaclient.model.UserOpaEvaluationContext.UserOpaEvaluationContextBuilder;
import io.harness.pms.helpers.CurrentUserHelper;
import io.harness.pms.merger.helpers.InputSetMergeHelper;
import io.harness.pms.pipeline.PipelineEntity;
import io.harness.pms.pipeline.service.PMSPipelineService;
import io.harness.security.PrincipalHelper;
import io.harness.security.dto.Principal;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.Optional;
import java.util.Set;
import javax.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Singleton
@OwnedBy(HarnessTeam.PIPELINE)
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
public class PMSOpaServiceImpl implements PMSOpaService {
  private final PlanExecutionMetadataService planExecutionMetadataService;
  private final PlanExecutionService planExecutionService;
  private final PMSPipelineService pmsPipelineService;
  private final CurrentUserHelper currentUserHelper;

  @Override
  public PipelineOpaEvaluationContext getPipelineContext(@NotNull String accountId, @NotNull String orgIdentifier,
      @NotNull String projectIdentifier, @NotNull String pipelineIdentifier, String inputSetPipelineYaml,
      @NotNull String action) throws IOException {
    Optional<PipelineEntity> pipelineEntity =
        pmsPipelineService.getPipeline(accountId, orgIdentifier, projectIdentifier, pipelineIdentifier, false, false);
    if (pipelineEntity.isEmpty()) {
      throw new InvalidRequestException(String.format("The given pipeline id [%s] does not exist", pipelineIdentifier));
    }

    String pipelineYaml;
    if (EmptyPredicate.isEmpty(inputSetPipelineYaml)) {
      pipelineYaml = pipelineEntity.get().getYaml();
    } else {
      pipelineYaml =
          InputSetMergeHelper.mergeInputSetIntoPipeline(pipelineEntity.get().getYaml(), inputSetPipelineYaml, false);
    }
    PipelineOpaEvaluationContextBuilder pipelineBuilder =
        PipelineOpaEvaluationContext.builder().pipeline(OpaUtils.extractObjectFromYamlString(pipelineYaml, "pipeline"));
    Principal principal = currentUserHelper.getPrincipalFromSecurityContext();
    UserOpaEvaluationContextBuilder userBuilder = UserOpaEvaluationContext.builder()
                                                      .email(PrincipalHelper.getEmail(principal))
                                                      .name(PrincipalHelper.getIdentifier(principal));

    pipelineBuilder.user(userBuilder.build());
    pipelineBuilder.action(action);
    return pipelineBuilder.build();
  }

  @Override
  public PipelineOpaEvaluationContext getPipelineContextFromExecution(@NotNull String accountId,
      @NotNull String orgIdentifier, @NotNull String projectIdentifier, @NotNull String planExecutionId,
      @NotNull String action) throws IOException {
    PlanExecution planExecution = planExecutionService.getWithFieldsIncluded(
        planExecutionId, Set.of(PlanExecution.ExecutionMetadataKeys.pipelineIdentifier));
    PlanExecutionMetadata planExecutionMetadata = planExecutionMetadataService.getWithFieldsIncludedFromSecondary(
        planExecutionId, Set.of(PlanExecutionMetadataKeys.inputSetYaml));
    return getPipelineContext(accountId, orgIdentifier, projectIdentifier,
        planExecution.getMetadata().getPipelineIdentifier(), planExecutionMetadata.getInputSetYaml(), action);
  }
}
