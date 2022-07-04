/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.executions.resources;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.pipeline.executions.beans.CDPipelineModuleInfo;
import io.harness.cdng.pipeline.executions.beans.CDStageModuleInfo;
import io.harness.ng.core.dto.ErrorDTO;
import io.harness.ng.core.dto.FailureDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.core.environment.beans.EnvironmentType;
import io.harness.plancreator.stages.stage.StageElementConfig;
import io.harness.plancreator.steps.StepElementConfig;
import io.harness.pms.execution.ExecutionStatus;

import com.codahale.metrics.annotation.ExceptionMetered;
import com.codahale.metrics.annotation.Timed;
import com.google.inject.Inject;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(CDC)
@Api("executions")
@Path("executions")
@Produces({"application/json", "text/yaml", "text/html"})
@Consumes({"application/json", "text/yaml", "text/html"})
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@ApiResponses(value =
    {
      @ApiResponse(code = 400, response = FailureDTO.class, message = "Bad Request")
      , @ApiResponse(code = 500, response = ErrorDTO.class, message = "Internal server error")
    })
@Slf4j
public class CDNGExecutionResource {
  @GET
  @Timed
  @ExceptionMetered
  @ApiOperation(value = "Gets Execution Status list", nickname = "getExecutionStatuses")
  @Path("/executionStatus")
  public ResponseDTO<List<ExecutionStatus>> getExecutionStatuses() {
    return ResponseDTO.newResponse(Arrays.asList(ExecutionStatus.values()));
  }

  @GET
  @Timed
  @ExceptionMetered
  @ApiOperation(value = "dummy api", nickname = "getDummyCDStageModuleInfo")
  @Path("/dummyCDStageModuleInfo")
  public ResponseDTO<CDStageModuleInfo> getDummyCDStageModuleInfo() {
    return ResponseDTO.newResponse(CDStageModuleInfo.builder().nodeExecutionId("node1").build());
  }

  @GET
  @Timed
  @ExceptionMetered
  @ApiOperation(value = "dummy api", nickname = "getDummyCDPipelineModuleInfo")
  @Path("/dummyCDPipelineModuleInfo")
  public ResponseDTO<CDPipelineModuleInfo> getDummyCDPipelineModuleInfo() {
    List<String> serviceIdentifiers = new ArrayList<>();
    serviceIdentifiers.add("dummyService1");
    List<String> envIdentifiers = new ArrayList<>();
    envIdentifiers.add("DymmyEnv1");
    List<String> serviceDefinitionTypes = new ArrayList<>();
    serviceDefinitionTypes.add("DummyTYpe1");
    List<EnvironmentType> environmentTypes = new ArrayList<>();
    environmentTypes.add(EnvironmentType.PreProduction);
    return ResponseDTO.newResponse(CDPipelineModuleInfo.builder()
                                       .serviceIdentifiers(serviceIdentifiers)
                                       .envIdentifiers(envIdentifiers)
                                       .serviceDefinitionTypes(serviceDefinitionTypes)
                                       .environmentTypes(environmentTypes)
                                       .build());
  }

  @GET
  @ApiOperation(value = "dummy api for checking deployment stage", nickname = "dummyApiForSwaggerStageSchemaCheck")
  @Path("/dummyApiForSwaggerStageSchemaCheck")
  // DO NOT DELETE THIS WITHOUT CONFIRMING WITH UI
  public ResponseDTO<StageElementConfig> dummyApiForSwaggerSchemaCheckForStage() {
    return ResponseDTO.newResponse(StageElementConfig.builder().build());
  }

  @GET
  @ApiOperation(value = "dummy api for checking deployment stage", nickname = "dummyApiForSwaggerStepSchemaCheck")
  @Path("/dummyApiForSwaggerStepSchemaCheck")
  // DO NOT DELETE THIS WITHOUT CONFIRMING WITH UI
  public ResponseDTO<StepElementConfig> dummyApiForSwaggerSchemaCheckForStep() {
    return ResponseDTO.newResponse(StepElementConfig.builder().build());
  }
}
