/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.inject.Inject;
import io.harness.EntityType;
import io.harness.annotations.dev.OwnedBy;
import io.harness.app.resources.HttpTaskNotifyCallback;
import io.harness.beans.DelegateTaskRequest;
import io.harness.ci.app.intfc.CIYamlSchemaService;
import io.harness.ci.plan.creator.execution.CIPipelineModuleInfo;
import io.harness.cimanager.yamlschema.api.CIYamlSchemaResource;
import io.harness.common.EntityTypeConstants;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.ErrorNotifyResponseData;
import io.harness.delegate.task.http.HttpTaskParametersNg;
import io.harness.encryption.Scope;
import io.harness.exception.ArtifactoryServerException;
import io.harness.exception.DelegateServiceDriverException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.ng.core.dto.ErrorDTO;
import io.harness.ng.core.dto.FailureDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.plancreator.stages.stage.StageElementConfig;
import io.harness.plancreator.steps.StepElementConfig;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.service.DelegateGrpcClientWrapper;
import io.harness.waiter.WaitNotifyEngine;
import io.harness.yaml.schema.YamlSchemaResource;
import io.harness.yaml.schema.beans.PartialSchemaDTO;
import io.harness.yaml.schema.beans.YamlSchemaDetailsWrapper;
import io.harness.yaml.schema.beans.YamlSchemaWithDetails;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import software.wings.beans.TaskType;

import javax.ws.rs.Consumes;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static io.harness.annotations.dev.HarnessTeam.CI;
import static io.harness.eraro.ErrorCode.ARTIFACT_SERVER_ERROR;
import static io.harness.exception.WingsException.USER;
import static io.harness.pms.listener.NgOrchestrationNotifyEventListener.NG_ORCHESTRATION;
import static java.lang.String.format;

@OwnedBy(CI)
@Api("/partial-yaml-schema")
@Path("/partial-yaml-schema")
@Produces({"application/json", "text/yaml", "text/html", "text/plain"})
@Consumes({"application/json", "text/yaml", "text/html", "text/plain"})
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@ApiResponses(value =
    {
      @ApiResponse(code = 400, response = FailureDTO.class, message = "Bad Request")
      , @ApiResponse(code = 500, response = ErrorDTO.class, message = "Internal server error")
    })
@Slf4j
public class CIYamlSchemaResourceImpl implements CIYamlSchemaResource, YamlSchemaResource {
  CIYamlSchemaService ciYamlSchemaService;

  DelegateGrpcClientWrapper delegateGrpcClientWrapper;
  WaitNotifyEngine waitNotifyEngine;

  public ResponseDTO<List<PartialSchemaDTO>> getYamlSchema(
      String accountIdentifier, String projectIdentifier, String orgIdentifier, Scope scope) {
    List<PartialSchemaDTO> partialSchemaDTOList = new ArrayList<>();
    partialSchemaDTOList.add(
        ciYamlSchemaService.getStageYamlSchema(accountIdentifier, orgIdentifier, projectIdentifier, scope));

    return ResponseDTO.newResponse(partialSchemaDTOList);
  }

  @Override
  public String idpProxyServer() {
    log.info("Starting the proxy task");
    DelegateTaskRequest delegateTaskRequest = DelegateTaskRequest.builder()
            .accountId("kmpySmUISimoRrJL6NL73w")
            .executionTimeout(java.time.Duration.ofSeconds(60))
            .taskType(TaskType.HTTP_TASK_NG.name())
            .taskParameters(getTaskParams("http://google.com","GET"))
            .taskDescription("IDP Proxy Http Task")
            .eligibleToExecuteDelegateIds(List.of("acctgroup"))
            .taskSetupAbstraction("ng", "true")
            .build();
    try {
      DelegateResponseData responseData = delegateGrpcClientWrapper.executeSyncTask(delegateTaskRequest);
      if (responseData instanceof ErrorNotifyResponseData) {
        ErrorNotifyResponseData errorNotifyResponseData = (ErrorNotifyResponseData) responseData;
        log.error("errorMessage: {}" , errorNotifyResponseData.getErrorMessage());
      }
    } catch (DelegateServiceDriverException ex) {
      log.error("Delegate error: ", ex);
    }
    //String taskId = delegateGrpcClientWrapper.submitAsyncTask(delegateTaskRequest, Duration.ZERO);
    //log.info("Task Successfully queued with taskId: {}", taskId);
    //waitNotifyEngine.waitForAllOn(NG_ORCHESTRATION, new HttpTaskNotifyCallback(), taskId);
    return "Success";
  }

  // DO NOT DELETE THIS WITHOUT CONFIRMING WITH UI
  public ResponseDTO<StageElementConfig> dummyApiForSwaggerSchemaCheckForStage() {
    return ResponseDTO.newResponse(StageElementConfig.builder().build());
  }

  // DO NOT DELETE THIS WITHOUT CONFIRMING WITH UI
  public ResponseDTO<StepElementConfig> dummyApiForSwaggerSchemaCheckForStep() {
    return ResponseDTO.newResponse(StepElementConfig.builder().build());
  }

  // DO NOT DELETE THIS WITHOUT CONFIRMING WITH UI
  public ResponseDTO<CIPipelineModuleInfo> dummyApiForSwaggerSchemaCheckForCIPipelineModuleInfo() {
    return ResponseDTO.newResponse(CIPipelineModuleInfo.builder().build());
  }

  public ResponseDTO<YamlSchemaDetailsWrapper> getYamlSchemaWithDetails(
      String accountIdentifier, String projectIdentifier, String orgIdentifier, Scope scope) {
    List<YamlSchemaWithDetails> ciSchemaWithDetails =
        ciYamlSchemaService.getStageYamlSchemaWithDetails(accountIdentifier, orgIdentifier, projectIdentifier, scope);
    return ResponseDTO.newResponse(
        YamlSchemaDetailsWrapper.builder().yamlSchemaWithDetailsList(ciSchemaWithDetails).build());
  }

  public ResponseDTO<List<PartialSchemaDTO>> getMergedYamlSchema(String accountIdentifier, String projectIdentifier,
      String orgIdentifier, Scope scope, YamlSchemaDetailsWrapper yamlSchemaDetailsWrapper) {
    PartialSchemaDTO ciSchema = ciYamlSchemaService.getMergedStageYamlSchema(accountIdentifier, projectIdentifier,
        orgIdentifier, scope, yamlSchemaDetailsWrapper.getYamlSchemaWithDetailsList());

    List<PartialSchemaDTO> partialSchemaDTOList = new ArrayList<>();
    partialSchemaDTOList.add(ciSchema);
    return ResponseDTO.newResponse(partialSchemaDTOList);
  }

  public ResponseDTO<JsonNode> getStepYamlSchema(String accountIdentifier, String projectIdentifier,
      String orgIdentifier, Scope scope, EntityType entityType, String yamlGroup,
      YamlSchemaDetailsWrapper yamlSchemaDetailsWrapper) {
    if (yamlGroup.equals(StepCategory.STAGE.toString())) {
      // Add more cases when ci module contains more stages.
      if (entityType.getYamlName().equals(EntityTypeConstants.INTEGRATION_STAGE)) {
        return ResponseDTO.newResponse(
            ciYamlSchemaService
                .getMergedStageYamlSchema(accountIdentifier, projectIdentifier, orgIdentifier, scope,
                    yamlSchemaDetailsWrapper.getYamlSchemaWithDetailsList())
                .getSchema());
      } else {
        throw new InvalidRequestException(format("stage %s does not exist in module ci", entityType));
      }
    }
    return ResponseDTO.newResponse(
        ciYamlSchemaService.getIndividualYamlSchema(entityType, orgIdentifier, projectIdentifier, scope));
  }

  private HttpTaskParametersNg getTaskParams(String url,String methodType) {
    HttpTaskParametersNg httpTaskParametersNg = HttpTaskParametersNg.builder()
            .url(url)
            .method(methodType)
            .socketTimeoutMillis(10000).build();
    return httpTaskParametersNg;
  }
}
