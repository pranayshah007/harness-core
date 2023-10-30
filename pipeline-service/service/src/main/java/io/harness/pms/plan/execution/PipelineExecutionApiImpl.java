/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.plan.execution;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.eraro.ErrorCode;
import io.harness.exception.ngexception.NGTemplateException;
import io.harness.exception.ngexception.PipelineException;
import io.harness.pms.annotations.PipelineServiceAuth;
import io.harness.spec.server.pipeline.v1.PipelineExecutionApi;
import io.harness.spec.server.pipeline.v1.model.ExecutionDetails;
import io.harness.spec.server.pipeline.v1.model.PipelineExecuteBody;
import io.harness.spec.server.pipeline.v1.model.PipelineExecuteResponseBody;

import com.google.inject.Inject;
import javax.validation.Valid;
import javax.ws.rs.core.Response;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(HarnessTeam.PIPELINE)
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@PipelineServiceAuth
@Slf4j
public class PipelineExecutionApiImpl implements PipelineExecutionApi {
  private final PipelineExecutor pipelineExecutor;

  @Override
  public Response executePipeline(String org, String project, String pipeline, @Valid PipelineExecuteBody body,
      String harnessAccount, String module, Boolean useFqnIfErrorResponse, Boolean notifyOnlyUser, String notes) {
    try {
      String inputSetPipelineYaml = null;
      if (body != null) {
        inputSetPipelineYaml = body.getYaml();
      }
      PlanExecutionResponseDto planExecutionResponseDto = pipelineExecutor.runPipelineWithInputSetPipelineYaml(
          harnessAccount, org, project, pipeline, module, inputSetPipelineYaml, false, notifyOnlyUser, notes);
      PipelineExecuteResponseBody pipelineExecuteResponseBody = new PipelineExecuteResponseBody();
      ExecutionDetails executionDetails = new ExecutionDetails();
      executionDetails.setExecutionId(planExecutionResponseDto.getPlanExecution().getUuid());
      executionDetails.setStatus(planExecutionResponseDto.getPlanExecution().getStatus().toString());
      pipelineExecuteResponseBody.setExecutionDetails(executionDetails);
      return Response.ok().entity(pipelineExecuteResponseBody).build();
    } catch (NGTemplateException ex) {
      throw new PipelineException(
          PipelineException.PIPELINE_Execution_MESSAGE, ex, ErrorCode.NG_PIPELINE_EXECUTION_EXCEPTION);
    }
  }
}
