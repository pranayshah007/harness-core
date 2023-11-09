/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.resources;
import static io.harness.NGCommonEntityConstants.ACCOUNT_KEY;
import static io.harness.NGCommonEntityConstants.CONNECTOR_IDENTIFIER_KEY;
import static io.harness.NGCommonEntityConstants.ENVIRONMENT_IDENTIFIER_KEY;
import static io.harness.NGCommonEntityConstants.IDENTIFIER_KEY;
import static io.harness.NGCommonEntityConstants.INFRA_IDENTIFIER;
import static io.harness.NGCommonEntityConstants.ORG_KEY;
import static io.harness.NGCommonEntityConstants.PIPELINE_KEY;
import static io.harness.NGCommonEntityConstants.PLAN_KEY;
import static io.harness.NGCommonEntityConstants.PROJECT_KEY;
import static io.harness.NGCommonEntityConstants.SERVICE_IDENTIFIER_KEY;
import static io.harness.NGCommonEntityConstants.STAGE_KEY;
import static io.harness.NGCommonEntityConstants.STEP_KEY;
import static io.harness.NGCommonEntityConstants.USER_ID;

import static dev.morphia.mapping.Mapper.ID_KEY;

import io.harness.ChangeDataCaptureBulkMigrationHelper;
import io.harness.accesscontrol.NGAccessControlCheck;
import io.harness.annotations.ExposeInternalException;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.ccm.commons.entities.billing.CECloudAccount.CECloudAccountKeys;
import io.harness.cdng.execution.StageExecutionInfo.StageExecutionInfoKeys;
import io.harness.changestreamsframework.ChangeType;
import io.harness.connector.entities.Connector.ConnectorKeys;
import io.harness.entities.AccountEntity;
import io.harness.entities.CDCEntity;
import io.harness.entities.CDStageExecutionCDCEntity;
import io.harness.entities.CECloudAccountCDCEntity;
import io.harness.entities.ConnectorCDCEntity;
import io.harness.entities.EnvironmentCDCEntity;
import io.harness.entities.InfrastructureEntityTimeScale;
import io.harness.entities.OrganizationEntity;
import io.harness.entities.PipelineCDCEntity;
import io.harness.entities.PipelineExecutionSummaryEntityCDCEntity;
import io.harness.entities.PipelineStageExecutionCDCEntity;
import io.harness.entities.ProjectEntity;
import io.harness.entities.ServiceCDCEntity;
import io.harness.entities.StepExecutionCDCEntity;
import io.harness.entities.UserEntity;
import io.harness.eraro.ErrorCode;
import io.harness.eraro.ResponseMessage;
import io.harness.execution.stage.StageExecutionEntity.StageExecutionEntityKeys;
import io.harness.execution.step.StepExecutionEntity.StepExecutionEntityKeys;
import io.harness.ng.core.entities.Organization.OrganizationKeys;
import io.harness.ng.core.entities.Project.ProjectKeys;
import io.harness.ng.core.environment.beans.Environment.EnvironmentKeys;
import io.harness.ng.core.infrastructure.entity.InfrastructureEntity.InfrastructureEntityKeys;
import io.harness.ng.core.service.entity.ServiceEntity.ServiceEntityKeys;
import io.harness.ng.core.user.entities.UserMetadata.UserMetadataKeys;
import io.harness.pms.pipeline.PipelineEntity.PipelineEntityKeys;
import io.harness.pms.plan.execution.beans.PipelineExecutionSummaryEntity.PlanExecutionSummaryKeys;
import io.harness.pms.rbac.PipelineRbacPermissions;
import io.harness.rest.RestResponse;
import io.harness.security.annotations.PublicApi;

import software.wings.beans.Account.AccountKeys;

import com.codahale.metrics.annotation.ExceptionMetered;
import com.codahale.metrics.annotation.Timed;
import com.google.inject.Inject;
import com.mongodb.client.model.Filters;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import javax.annotation.Nullable;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import lombok.extern.slf4j.Slf4j;
import org.bson.conversions.Bson;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_DASHBOARD})
@Api("sync")
@Path("/sync")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@PublicApi
@Slf4j
@ExposeInternalException
@OwnedBy(HarnessTeam.CDC)
public class PartialSyncResource {
  private static final String HANDLER_KEY = "handler";
  private static final String CHANGE_TYPE_KEY = "changeType";

  @Inject ChangeDataCaptureBulkMigrationHelper changeDataCaptureBulkMigrationHelper;

  @Inject AccountEntity accountEntity;
  @Inject CECloudAccountCDCEntity ceCloudAccountCDCEntity;
  @Inject EnvironmentCDCEntity environmentCDCEntity;
  @Inject InfrastructureEntityTimeScale infrastructureEntityTimeScale;
  @Inject OrganizationEntity organizationEntity;
  @Inject PipelineCDCEntity pipelineCDCEntity;
  @Inject PipelineExecutionSummaryEntityCDCEntity pipelineExecutionSummaryEntityCDCEntity;
  @Inject ProjectEntity projectEntity;
  @Inject ServiceCDCEntity serviceCDCEntity;
  @Inject ConnectorCDCEntity connectorCDCEntity;
  @Inject UserEntity userEntity;
  @Inject CDStageExecutionCDCEntity cdStageExecutionCDCEntity;
  @Inject PipelineStageExecutionCDCEntity pipelineStageExecutionCDCEntity;
  @Inject StepExecutionCDCEntity stepExecutionCDCEntity;

  @GET
  @Path("/accounts")
  @Timed
  @ExceptionMetered
  @ApiOperation(value = "trigger bulk sync for the account entity using supplied filters")
  @NGAccessControlCheck(resourceType = "PIPELINE", permission = PipelineRbacPermissions.PIPELINE_EXECUTE)
  public RestResponse<String> triggerAccountSync(@QueryParam(ACCOUNT_KEY) @Nullable String accountId,
      @QueryParam("createdAt_from") @Nullable Long createdAtFrom,
      @QueryParam("createdAt_to") @Nullable Long createdAtTo,
      @QueryParam(CHANGE_TYPE_KEY) @Nullable ChangeType changeType) {
    List<Bson> filters = new ArrayList<>();
    addEqFilter(filters, ID_KEY, accountId);
    addTsFilter(filters, AccountKeys.createdAt, createdAtFrom, createdAtTo);

    return triggerSync(accountEntity, filters, null, changeType);
  }

  @GET
  @Path("/cloudAccount")
  @Timed
  @ExceptionMetered
  @ApiOperation(value = "trigger bulk sync for the cloud account entity using supplied filters")
  public RestResponse<String> triggerCloudAccountSync(@QueryParam(IDENTIFIER_KEY) @Nullable String identifier,
      @QueryParam(ACCOUNT_KEY) @Nullable String accountId, @QueryParam(HANDLER_KEY) @Nullable String handler,
      @QueryParam("createdAt_from") @Nullable Long createdAtFrom,
      @QueryParam("createdAt_to") @Nullable Long createdAtTo,
      @QueryParam(CHANGE_TYPE_KEY) @Nullable ChangeType changeType) {
    List<Bson> filters = new ArrayList<>();
    addEqFilter(filters, ID_KEY, identifier);
    addEqFilter(filters, CECloudAccountKeys.accountId, accountId);
    addTsFilter(filters, CECloudAccountKeys.createdAt, createdAtFrom, createdAtTo);

    return triggerSync(ceCloudAccountCDCEntity, filters, handler, changeType);
  }

  @GET
  @Path("/environments")
  @Timed
  @ExceptionMetered
  @ApiOperation(value = "trigger bulk sync for the environments entity using supplied filters")
  public RestResponse<String> triggerEnvironmentSync(
      @QueryParam(ENVIRONMENT_IDENTIFIER_KEY) @Nullable String identifier,
      @QueryParam(ACCOUNT_KEY) @Nullable String accountId, @QueryParam(PROJECT_KEY) @Nullable String projectIdentifier,
      @QueryParam("createdAt_from") @Nullable Long createdAtFrom,
      @QueryParam("createdAt_to") @Nullable Long createdAtTo,
      @QueryParam(CHANGE_TYPE_KEY) @Nullable ChangeType changeType) {
    List<Bson> filters = new ArrayList<>();
    addEqFilter(filters, EnvironmentKeys.identifier, identifier);
    addEqFilter(filters, EnvironmentKeys.accountId, accountId);
    addEqFilter(filters, EnvironmentKeys.projectIdentifier, projectIdentifier);
    addTsFilter(filters, EnvironmentKeys.createdAt, createdAtFrom, createdAtTo);

    return triggerSync(environmentCDCEntity, filters, null, changeType);
  }

  @GET
  @Path("/infrastructures")
  @Timed
  @ExceptionMetered
  @ApiOperation(value = "trigger bulk sync for the infrastructures entity using supplied filters")
  public RestResponse<String> triggerInfrastructureSync(@QueryParam(INFRA_IDENTIFIER) @Nullable String identifier,
      @QueryParam(ACCOUNT_KEY) @Nullable String accountId, @QueryParam(PROJECT_KEY) @Nullable String projectIdentifier,
      @QueryParam("createdAt_from") @Nullable Long createdAtFrom,
      @QueryParam("createdAt_to") @Nullable Long createdAtTo,
      @QueryParam(CHANGE_TYPE_KEY) @Nullable ChangeType changeType) {
    List<Bson> filters = new ArrayList<>();
    addEqFilter(filters, InfrastructureEntityKeys.identifier, identifier);
    addEqFilter(filters, InfrastructureEntityKeys.accountId, accountId);
    addEqFilter(filters, InfrastructureEntityKeys.projectIdentifier, projectIdentifier);
    addTsFilter(filters, InfrastructureEntityKeys.createdAt, createdAtFrom, createdAtTo);

    return triggerSync(infrastructureEntityTimeScale, filters, null, changeType);
  }

  @GET
  @Path("/organizations")
  @Timed
  @ExceptionMetered
  @ApiOperation(value = "trigger bulk sync for the organization entity using supplied filters")
  public RestResponse<String> triggerOrganizationSync(@QueryParam(ORG_KEY) @Nullable String identifier,
      @QueryParam(ACCOUNT_KEY) @Nullable String accountId, @QueryParam(PROJECT_KEY) @Nullable String projectIdentifier,
      @QueryParam(HANDLER_KEY) @Nullable String handler, @QueryParam("createdAt_from") @Nullable Long createdAtFrom,
      @QueryParam("createdAt_to") @Nullable Long createdAtTo,
      @QueryParam(CHANGE_TYPE_KEY) @Nullable ChangeType changeType) {
    List<Bson> filters = new ArrayList<>();
    addEqFilter(filters, OrganizationKeys.identifier, identifier);
    addEqFilter(filters, OrganizationKeys.accountIdentifier, accountId);
    addTsFilter(filters, OrganizationKeys.createdAt, createdAtFrom, createdAtTo);

    return triggerSync(organizationEntity, filters, handler, changeType);
  }

  @GET
  @Path("/pipelines")
  @Timed
  @ExceptionMetered
  @ApiOperation(value = "trigger bulk sync for the pipelines entity using supplied filters")
  public RestResponse<String> triggerPipelinesSync(@QueryParam(PIPELINE_KEY) @Nullable String identifier,
      @QueryParam(ACCOUNT_KEY) @Nullable String accountId, @QueryParam(PROJECT_KEY) @Nullable String projectIdentifier,
      @QueryParam(HANDLER_KEY) @Nullable String handler, @QueryParam("createdAt_from") @Nullable Long createdAtFrom,
      @QueryParam("createdAt_to") @Nullable Long createdAtTo,
      @QueryParam(CHANGE_TYPE_KEY) @Nullable ChangeType changeType) {
    List<Bson> filters = new ArrayList<>();
    addEqFilter(filters, PipelineEntityKeys.identifier, identifier);
    addEqFilter(filters, PipelineEntityKeys.accountId, accountId);
    addEqFilter(filters, PipelineEntityKeys.projectIdentifier, projectIdentifier);
    addTsFilter(filters, PipelineEntityKeys.createdAt, createdAtFrom, createdAtTo);

    return triggerSync(pipelineCDCEntity, filters, handler, changeType);
  }

  @GET
  @Path("/executions")
  @Timed
  @ExceptionMetered
  @ApiOperation(value = "trigger bulk sync for the account entity using supplied filters")
  public RestResponse<String> triggerPipelineExecutionSync(@QueryParam(PLAN_KEY) @Nullable String identifier,
      @QueryParam(ACCOUNT_KEY) @Nullable String accountId, @QueryParam(PROJECT_KEY) @Nullable String projectIdentifier,
      @QueryParam(PIPELINE_KEY) @Nullable String pipelineIdentifier,
      @QueryParam(PLAN_KEY) @Nullable String planExecutionId, @QueryParam(HANDLER_KEY) @Nullable String handler,
      @QueryParam("startTs_from") @Nullable Long startTsFrom, @QueryParam("startTs_to") @Nullable Long startTsTo,
      @QueryParam(CHANGE_TYPE_KEY) @Nullable ChangeType changeType) {
    List<Bson> filters = new ArrayList<>();
    addEqFilter(filters, PlanExecutionSummaryKeys.planExecutionId, planExecutionId);
    addEqFilter(filters, PlanExecutionSummaryKeys.accountId, accountId);
    addEqFilter(filters, PlanExecutionSummaryKeys.projectIdentifier, projectIdentifier);
    addEqFilter(filters, PlanExecutionSummaryKeys.pipelineIdentifier, pipelineIdentifier);
    addTsFilter(filters, PlanExecutionSummaryKeys.startTs, startTsFrom, startTsTo);

    return triggerSync(pipelineExecutionSummaryEntityCDCEntity, filters, handler, changeType);
  }

  @GET
  @Path("/cdStageExecutions")
  @Timed
  @ExceptionMetered
  @ApiOperation(value = "trigger bulk sync for the stage Execution entity using supplied filters")
  public RestResponse<String> triggerCDStageExecutionSync(@QueryParam(STAGE_KEY) @Nullable String identifier,
      @QueryParam(ACCOUNT_KEY) @Nullable String accountId, @QueryParam(PROJECT_KEY) @Nullable String projectIdentifier,
      @QueryParam(PIPELINE_KEY) @Nullable String pipelineIdentifier,
      @QueryParam(PLAN_KEY) @Nullable String planExecutionId, @QueryParam(HANDLER_KEY) @Nullable String handler,
      @QueryParam("startTs_from") @Nullable Long startTsFrom, @QueryParam("startTs_to") @Nullable Long startTsTo,
      @QueryParam(CHANGE_TYPE_KEY) @Nullable ChangeType changeType) {
    List<Bson> filters = new ArrayList<>();
    addEqFilter(filters, StageExecutionInfoKeys.stageExecutionId, identifier);
    addEqFilter(filters, StageExecutionInfoKeys.planExecutionId, planExecutionId);
    addEqFilter(filters, StageExecutionInfoKeys.accountIdentifier, accountId);
    addEqFilter(filters, StageExecutionInfoKeys.projectIdentifier, projectIdentifier);
    addEqFilter(filters, StageExecutionInfoKeys.pipelineIdentifier, pipelineIdentifier);
    addTsFilter(filters, StageExecutionInfoKeys.startts, startTsFrom, startTsTo);

    return triggerSync(cdStageExecutionCDCEntity, filters, handler, changeType);
  }

  @GET
  @Path("/pipelineStageExecutions")
  @Timed
  @ExceptionMetered
  @ApiOperation(value = "trigger bulk sync for the pipeline stage Execution entity using supplied filters")
  public RestResponse<String> triggerPipelineStageExecutionSync(@QueryParam(STAGE_KEY) @Nullable String identifier,
      @QueryParam(ACCOUNT_KEY) @Nullable String accountId, @QueryParam(PROJECT_KEY) @Nullable String projectIdentifier,
      @QueryParam(PIPELINE_KEY) @Nullable String pipelineIdentifier,
      @QueryParam(PLAN_KEY) @Nullable String planExecutionId, @QueryParam(HANDLER_KEY) @Nullable String handler,
      @QueryParam("startTs_from") @Nullable Long startTsFrom, @QueryParam("startTs_to") @Nullable Long startTsTo,
      @QueryParam(CHANGE_TYPE_KEY) @Nullable ChangeType changeType) {
    List<Bson> filters = new ArrayList<>();
    addEqFilter(filters, StageExecutionEntityKeys.stageExecutionId, identifier);
    addEqFilter(filters, StageExecutionEntityKeys.planExecutionId, planExecutionId);
    addEqFilter(filters, StageExecutionEntityKeys.accountIdentifier, accountId);
    addEqFilter(filters, StageExecutionEntityKeys.projectIdentifier, projectIdentifier);
    addEqFilter(filters, StageExecutionEntityKeys.pipelineIdentifier, pipelineIdentifier);
    addTsFilter(filters, StageExecutionEntityKeys.startts, startTsFrom, startTsTo);

    return triggerSync(pipelineStageExecutionCDCEntity, filters, handler, changeType);
  }

  @GET
  @Path("/stepExecutions")
  @Timed
  @ExceptionMetered
  @ApiOperation(value = "trigger bulk sync for the step Execution entity using supplied filters")
  public RestResponse<String> triggerStepExecutionSync(@QueryParam(STEP_KEY) @Nullable String identifier,
      @QueryParam(ACCOUNT_KEY) @Nullable String accountId, @QueryParam(PROJECT_KEY) @Nullable String projectIdentifier,
      @QueryParam(PIPELINE_KEY) @Nullable String pipelineIdentifier,
      @QueryParam(PLAN_KEY) @Nullable String planExecutionId, @QueryParam(HANDLER_KEY) @Nullable String handler,
      @QueryParam(STAGE_KEY) @Nullable String stageExecutionId, @QueryParam("startTs_from") @Nullable Long startTsFrom,
      @QueryParam("startTs_to") @Nullable Long startTsTo,
      @QueryParam(CHANGE_TYPE_KEY) @Nullable ChangeType changeType) {
    List<Bson> filters = new ArrayList<>();
    addEqFilter(filters, StepExecutionEntityKeys.stepExecutionId, identifier);
    addEqFilter(filters, StepExecutionEntityKeys.stageExecutionId, stageExecutionId);
    addEqFilter(filters, StageExecutionEntityKeys.planExecutionId, planExecutionId);
    addEqFilter(filters, StageExecutionEntityKeys.accountIdentifier, accountId);
    addEqFilter(filters, StageExecutionEntityKeys.projectIdentifier, projectIdentifier);
    addEqFilter(filters, StageExecutionEntityKeys.pipelineIdentifier, pipelineIdentifier);
    addTsFilter(filters, StageExecutionEntityKeys.startts, startTsFrom, startTsTo);

    return triggerSync(stepExecutionCDCEntity, filters, handler, changeType);
  }

  @GET
  @Path("/projects")
  @Timed
  @ExceptionMetered
  @ApiOperation(value = "trigger bulk sync for the projects entity using supplied filters")
  public RestResponse<String> triggerProjectsSync(@QueryParam(PROJECT_KEY) @Nullable String identifier,
      @QueryParam(ACCOUNT_KEY) @Nullable String accountId, @QueryParam(HANDLER_KEY) @Nullable String handler,
      @QueryParam("createdAt_from") @Nullable Long createdAtFrom,
      @QueryParam("createdAt_to") @Nullable Long createdAtTo,
      @QueryParam(CHANGE_TYPE_KEY) @Nullable ChangeType changeType) {
    List<Bson> filters = new ArrayList<>();
    addEqFilter(filters, ProjectKeys.identifier, identifier);
    addEqFilter(filters, ProjectKeys.accountIdentifier, accountId);
    addTsFilter(filters, ProjectKeys.createdAt, createdAtFrom, createdAtTo);

    return triggerSync(projectEntity, filters, handler, changeType);
  }

  @GET
  @Path("/services")
  @Timed
  @ExceptionMetered
  @ApiOperation(value = "trigger bulk sync for the services entity using supplied filters")
  public RestResponse<String> triggerServicesSync(@QueryParam(SERVICE_IDENTIFIER_KEY) @Nullable String identifier,
      @QueryParam(ACCOUNT_KEY) @Nullable String accountId, @QueryParam(HANDLER_KEY) @Nullable String handler,
      @QueryParam("createdAt_from") @Nullable Long createdAtFrom,
      @QueryParam("createdAt_to") @Nullable Long createdAtTo,
      @QueryParam(CHANGE_TYPE_KEY) @Nullable ChangeType changeType) {
    List<Bson> filters = new ArrayList<>();
    addEqFilter(filters, ServiceEntityKeys.identifier, identifier);
    addEqFilter(filters, ServiceEntityKeys.accountId, accountId);
    addTsFilter(filters, ServiceEntityKeys.createdAt, createdAtFrom, createdAtTo);

    return triggerSync(serviceCDCEntity, filters, handler, changeType);
  }

  @GET
  @Path("/connectors")
  @Timed
  @ExceptionMetered
  @ApiOperation(value = "trigger bulk sync for the connectors entity using supplied filters")
  public RestResponse<String> triggerConnectorsSync(@QueryParam(CONNECTOR_IDENTIFIER_KEY) @Nullable String identifier,
      @QueryParam(ACCOUNT_KEY) @Nullable String accountId, @QueryParam(HANDLER_KEY) @Nullable String handler,
      @QueryParam("createdAt_from") @Nullable Long createdAtFrom,
      @QueryParam("createdAt_to") @Nullable Long createdAtTo,
      @QueryParam(CHANGE_TYPE_KEY) @Nullable ChangeType changeType) {
    List<Bson> filters = new ArrayList<>();
    addEqFilter(filters, ConnectorKeys.identifier, identifier);
    addEqFilter(filters, ConnectorKeys.accountIdentifier, accountId);
    addTsFilter(filters, ConnectorKeys.createdAt, createdAtFrom, createdAtTo);

    return triggerSync(connectorCDCEntity, filters, handler, changeType);
  }

  @GET
  @Path("/users")
  @Timed
  @ExceptionMetered
  @ApiOperation(value = "trigger bulk sync for the users entity using supplied filters")
  public RestResponse<String> triggerUsersSync(@QueryParam(USER_ID) @Nullable String identifier,
      @QueryParam(HANDLER_KEY) @Nullable String handler, @QueryParam("createdAt_from") @Nullable Long createdAtFrom,
      @QueryParam("createdAt_to") @Nullable Long createdAtTo,
      @QueryParam(CHANGE_TYPE_KEY) @Nullable ChangeType changeType) {
    List<Bson> filters = new ArrayList<>();
    addEqFilter(filters, UserMetadataKeys.userId, identifier);
    addTsFilter(filters, ServiceEntityKeys.createdAt, createdAtFrom, createdAtTo);

    return triggerSync(userEntity, filters, handler, changeType);
  }

  private void addEqFilter(List<Bson> filters, String key, String value) {
    if (value != null) {
      filters.add(Filters.eq(key, value));
    }
  }

  private void addTsFilter(List<Bson> filters, String key, Long from, Long to) {
    if (from != null && to != null) {
      filters.add(Filters.gt(key, from));
      filters.add(Filters.lt(key, to));
    }
  }

  public RestResponse<String> triggerSync(
      CDCEntity<?> entity, List<Bson> filters, String handler, ChangeType changeType) {
    if (filters.isEmpty()) {
      RestResponse<String> restResponse = new RestResponse<>();
      restResponse.setResponseMessages(List.of(ResponseMessage.builder()
                                                   .code(ErrorCode.INVALID_ARGUMENT)
                                                   .message("You must provide at least one filter")
                                                   .build()));
      restResponse.setResponseMessages(List.of(ResponseMessage.builder()
                                                   .code(ErrorCode.INVALID_ARGUMENT)
                                                   .message("You must provide at least one filter")
                                                   .build()));
      return restResponse;
    }

    int count =
        changeDataCaptureBulkMigrationHelper.doPartialSync(Set.of(entity), Filters.and(filters), handler, changeType);
    return new RestResponse<>(count + " events synced");
  }
}
