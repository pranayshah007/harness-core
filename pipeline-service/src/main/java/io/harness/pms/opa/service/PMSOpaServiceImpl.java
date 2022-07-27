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
import io.harness.exception.InvalidRequestException;
import io.harness.opaclient.OpaUtils;
import io.harness.opaclient.model.PipelineOpaEvaluationContext;
import io.harness.opaclient.model.PipelineOpaEvaluationContext.PipelineOpaEvaluationContextBuilder;
import io.harness.opaclient.model.UserOpaEvaluationContext;
import io.harness.opaclient.model.UserOpaEvaluationContext.UserOpaEvaluationContextBuilder;
import io.harness.pms.helpers.CurrentUserHelper;
import io.harness.pms.merger.helpers.InputSetMergeHelper;
import io.harness.pms.pipeline.PipelineEntity;
import io.harness.pms.pipeline.service.PMSPipelineService;
import io.harness.pms.plan.execution.beans.PipelineExecutionSummaryEntity;
import io.harness.pms.plan.execution.service.PMSExecutionService;
import io.harness.security.PrincipalHelper;
import io.harness.security.dto.Principal;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.Optional;
import javax.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Singleton
@OwnedBy(HarnessTeam.PIPELINE)
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
public class PMSOpaServiceImpl implements PMSOpaService {
  private final PMSPipelineService pmsPipelineService;
  private final PMSExecutionService pmsExecutionService;
  private final CurrentUserHelper currentUserHelper;

  @Override
  public PipelineOpaEvaluationContext getPipelineContext(@NotNull String accountId, @NotNull String orgIdentifier,
      @NotNull String projectIdentifier, @NotNull String pipelineIdentifier, String inputSetPipelineYaml,
      @NotNull String action) throws IOException {
    Optional<PipelineEntity> pipelineEntity =
        pmsPipelineService.get(accountId, orgIdentifier, projectIdentifier, pipelineIdentifier, false);
    if (!pipelineEntity.isPresent()) {
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
    PipelineExecutionSummaryEntity executionSummaryEntity = pmsExecutionService.getPipelineExecutionSummaryEntity(
        accountId, orgIdentifier, projectIdentifier, planExecutionId, false);
    return getPipelineContext(accountId, orgIdentifier, projectIdentifier,
        executionSummaryEntity.getPipelineIdentifier(), executionSummaryEntity.getInputSetYaml(), action);
  }
}
