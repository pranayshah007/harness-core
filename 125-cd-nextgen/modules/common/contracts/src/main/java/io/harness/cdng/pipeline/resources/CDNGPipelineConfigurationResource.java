/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.pipeline.resources;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.NGCommonEntityConstants;
import io.harness.accesscontrol.AccountIdentifier;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.ExecutionStrategyType;
import io.harness.cdng.infra.beans.ProvisionerType;
import io.harness.cdng.pipeline.StepCategory;
import io.harness.cdng.pipeline.helpers.CDNGPipelineConfigurationHelper;
import io.harness.cdng.service.beans.ServiceDefinitionType;
import io.harness.ng.core.dto.ErrorDTO;
import io.harness.ng.core.dto.FailureDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.steps.matrix.StrategyParameters;

import com.google.common.io.Resources;
import com.google.inject.Inject;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javax.validation.constraints.NotNull;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import retrofit2.http.Body;

@OwnedBy(CDP)
@Api("pipelines")
@Path("pipelines/configuration")
@Produces({"application/json", "application/yaml"})
@Consumes({"application/json", "application/yaml"})
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@ApiResponses(value =
    {
      @ApiResponse(code = 400, response = FailureDTO.class, message = "Bad Request")
      , @ApiResponse(code = 500, response = ErrorDTO.class, message = "Internal server error")
    })
@Slf4j
public class CDNGPipelineConfigurationResource {
  private final CDNGPipelineConfigurationHelper cdngPipelineConfigurationHelper;

  @GET
  @Path("/strategies")
  @ApiOperation(value = "Gets Execution Strategy list", nickname = "getExecutionStrategyList")
  public ResponseDTO<Map<ServiceDefinitionType, List<ExecutionStrategyType>>> getExecutionStrategyList() {
    log.info("Get List of execution Strategy");
    return ResponseDTO.newResponse(cdngPipelineConfigurationHelper.getExecutionStrategyList());
  }

  @GET
  @Path("/strategies/yaml-snippets")
  @ApiOperation(value = "Gets Yaml for Execution Strategy based on deployment type and selected strategy",
      nickname = "getExecutionStrategyYaml")
  public ResponseDTO<String>
  getExecutionStrategyYaml(@NotNull @QueryParam("serviceDefinitionType") ServiceDefinitionType serviceDefinitionType,
      @NotNull @QueryParam("strategyType") ExecutionStrategyType executionStrategyType,
      @QueryParam("includeVerify") boolean includeVerify,
      @Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountIdentifier,
      @QueryParam("deploymentMetadataYaml") String deploymentMetaDataYaml) throws IOException {
    return ResponseDTO.newResponse(cdngPipelineConfigurationHelper.getExecutionStrategyYaml(
        serviceDefinitionType, executionStrategyType, includeVerify, deploymentMetaDataYaml));
  }

  @GET
  @Path("/strategies/provisioner-yaml-snippets")
  @ApiOperation(value = "Gets Yaml for Execution Strategy based on Provisioner Type",
      nickname = "getProvisionerExecutionStrategyYaml")
  @Operation(operationId = "getProvisionerExecutionStrategyYaml",
      summary = "Gets Yaml for Execution Strategy based on Provisioner Type",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns execution strategy yaml")
      })
  public ResponseDTO<String>
  getProvisionerExecutionStrategyYaml(@Parameter(description = "Provisioner type to fetch yaml snippet") @NotNull
      @QueryParam("provisionerType") ProvisionerType provisionerType) throws IOException {
    return ResponseDTO.newResponse(
        cdngPipelineConfigurationHelper.getProvisionerExecutionStrategyYaml(provisionerType));
  }

  @GET
  @Path("/serviceDefinitionTypes")
  @ApiOperation(value = "Git list of service definition types", nickname = "getServiceDefinitionTypes")
  public ResponseDTO<List<ServiceDefinitionType>> getServiceDefinitionTypes(@QueryParam("accountId") String accountId) {
    return ResponseDTO.newResponse(cdngPipelineConfigurationHelper.getServiceDefinitionTypes(accountId));
  }

  @GET
  @Path("/steps")
  @ApiOperation(value = "get steps for given service definition type", nickname = "getSteps")
  public ResponseDTO<StepCategory> getSteps(
      @NotNull @QueryParam("serviceDefinitionType") ServiceDefinitionType serviceDefinitionType) {
    return ResponseDTO.newResponse(cdngPipelineConfigurationHelper.getSteps(serviceDefinitionType));
  }

  @GET
  @Path("/provisioner-steps")
  @ApiOperation(value = "get provisioner steps", nickname = "getProvisionerSteps")
  @Operation(operationId = "getProvisionerSteps", summary = "Get provisioner steps",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns list of provisioner steps")
      })
  public ResponseDTO<StepCategory>
  getProvisionerSteps() {
    return ResponseDTO.newResponse(cdngPipelineConfigurationHelper.getStepsForProvisioners());
  }

  @GET
  @Path("/cd-stage-yaml-snippet")
  @ApiOperation(value = "Gets the failure strategy yaml snippet for CD stage", nickname = "getFailureStrategiesYaml")
  @Operation(operationId = "getFailureStrategiesYaml", summary = "Get Failure Strategies yaml snippet for CD stage",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns failure strategies yaml snippet for CD stage")
      })
  public ResponseDTO<String>
  getFailureStrategiesYaml() throws IOException {
    ClassLoader classLoader = this.getClass().getClassLoader();
    return ResponseDTO.newResponse(
        Resources.toString(Objects.requireNonNull(classLoader.getResource("failureStrategyYaml/failurestrategy.yaml")),
            StandardCharsets.UTF_8));
  }

  @POST
  @Path("/strategies/yaml-snippets/")
  @ApiOperation(
      value = "Gets generated Yaml snippet based on strategy parameters", nickname = "postExecutionStrategyYaml")
  public ResponseDTO<String>
  getExecutionStrategyYaml(@Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
                               NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountIdentifier,
      @NotNull @QueryParam("serviceDefinitionType") ServiceDefinitionType serviceDefinitionType,
      @NotNull @QueryParam("strategyType") ExecutionStrategyType executionStrategyType,
      @QueryParam("includeVerify") boolean includeVerify, @NotNull @Body StrategyParameters strategyParameters)
      throws IOException {
    return ResponseDTO.newResponse(cdngPipelineConfigurationHelper.generateExecutionStrategyYaml(
        accountIdentifier, serviceDefinitionType, executionStrategyType, includeVerify, strategyParameters));
  }
}
