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

import static dev.morphia.mapping.Mapper.ID_KEY;

import io.harness.ChangeDataCaptureBulkMigrationHelper;
import io.harness.accesscontrol.NGAccessControlCheck;
import io.harness.annotations.ExposeInternalException;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ccm.commons.entities.billing.CECloudAccount.CECloudAccountKeys;
import io.harness.connector.entities.Connector.ConnectorKeys;
import io.harness.entities.AccountEntity;
import io.harness.entities.CDCEntity;
import io.harness.entities.CECloudAccountCDCEntity;
import io.harness.entities.ConnectorCDCEntity;
import io.harness.entities.EnvironmentCDCEntity;
import io.harness.entities.InfrastructureEntityTimeScale;
import io.harness.entities.OrganizationEntity;
import io.harness.entities.PipelineCDCEntity;
import io.harness.entities.PipelineExecutionSummaryEntityCDCEntity;
import io.harness.entities.ProjectEntity;
import io.harness.entities.ServiceCDCEntity;
import io.harness.eraro.ErrorCode;
import io.harness.eraro.ResponseMessage;
import io.harness.ng.core.entities.Organization.OrganizationKeys;
import io.harness.ng.core.entities.Project.ProjectKeys;
import io.harness.ng.core.environment.beans.Environment.EnvironmentKeys;
import io.harness.ng.core.infrastructure.entity.InfrastructureEntity.InfrastructureEntityKeys;
import io.harness.ng.core.service.entity.ServiceEntity.ServiceEntityKeys;
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

  @GET
  @Path("/accounts")
  @Timed
  @ExceptionMetered
  @ApiOperation(value = "trigger bulk sync for the account entity using supplied filters")
  @NGAccessControlCheck(resourceType = "PIPELINE", permission = PipelineRbacPermissions.PIPELINE_EXECUTE)
  public RestResponse<String> triggerAccountSync(@QueryParam(ACCOUNT_KEY) @Nullable String accountId,
      @QueryParam("createdAt_from") @Nullable Long createdAtFrom,
      @QueryParam("createdAt_to") @Nullable Long createdAtTo) {
    List<Bson> filters = new ArrayList<>();
    addEqFilter(filters, ID_KEY, accountId);
    addTsFilter(filters, AccountKeys.createdAt, createdAtFrom, createdAtTo);

    return triggerSync(accountEntity, filters, null);
  }

  @GET
  @Path("/cloudAccount")
  @Timed
  @ExceptionMetered
  @ApiOperation(value = "trigger bulk sync for the cloud account entity using supplied filters")
  public RestResponse<String> triggerCloudAccountSync(@QueryParam(IDENTIFIER_KEY) @Nullable String identifier,
      @QueryParam(ACCOUNT_KEY) @Nullable String accountId, @QueryParam(HANDLER_KEY) @Nullable String handler,
      @QueryParam("createdAt_from") @Nullable Long createdAtFrom,
      @QueryParam("createdAt_to") @Nullable Long createdAtTo) {
    List<Bson> filters = new ArrayList<>();
    addEqFilter(filters, ID_KEY, identifier);
    addEqFilter(filters, CECloudAccountKeys.accountId, accountId);
    addTsFilter(filters, CECloudAccountKeys.createdAt, createdAtFrom, createdAtTo);

    return triggerSync(ceCloudAccountCDCEntity, filters, handler);
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
      @QueryParam("createdAt_to") @Nullable Long createdAtTo) {
    List<Bson> filters = new ArrayList<>();
    addEqFilter(filters, EnvironmentKeys.identifier, identifier);
    addEqFilter(filters, EnvironmentKeys.accountId, accountId);
    addEqFilter(filters, EnvironmentKeys.projectIdentifier, projectIdentifier);
    addTsFilter(filters, EnvironmentKeys.createdAt, createdAtFrom, createdAtTo);

    return triggerSync(environmentCDCEntity, filters, null);
  }

  @GET
  @Path("/infrastructures")
  @Timed
  @ExceptionMetered
  @ApiOperation(value = "trigger bulk sync for the infrastructures entity using supplied filters")
  public RestResponse<String> triggerInfrastructureSync(@QueryParam(INFRA_IDENTIFIER) @Nullable String identifier,
      @QueryParam(ACCOUNT_KEY) @Nullable String accountId, @QueryParam(PROJECT_KEY) @Nullable String projectIdentifier,
      @QueryParam("createdAt_from") @Nullable Long createdAtFrom,
      @QueryParam("createdAt_to") @Nullable Long createdAtTo) {
    List<Bson> filters = new ArrayList<>();
    addEqFilter(filters, InfrastructureEntityKeys.identifier, identifier);
    addEqFilter(filters, InfrastructureEntityKeys.accountId, accountId);
    addEqFilter(filters, InfrastructureEntityKeys.projectIdentifier, projectIdentifier);
    addTsFilter(filters, InfrastructureEntityKeys.createdAt, createdAtFrom, createdAtTo);

    return triggerSync(infrastructureEntityTimeScale, filters, null);
  }

  @GET
  @Path("/organizations")
  @Timed
  @ExceptionMetered
  @ApiOperation(value = "trigger bulk sync for the organization entity using supplied filters")
  public RestResponse<String> triggerOrganizationSync(@QueryParam(ORG_KEY) @Nullable String identifier,
      @QueryParam(ACCOUNT_KEY) @Nullable String accountId, @QueryParam(PROJECT_KEY) @Nullable String projectIdentifier,
      @QueryParam(HANDLER_KEY) @Nullable String handler, @QueryParam("createdAt_from") @Nullable Long createdAtFrom,
      @QueryParam("createdAt_to") @Nullable Long createdAtTo) {
    List<Bson> filters = new ArrayList<>();
    addEqFilter(filters, OrganizationKeys.identifier, identifier);
    addEqFilter(filters, OrganizationKeys.accountIdentifier, accountId);
    addTsFilter(filters, OrganizationKeys.createdAt, createdAtFrom, createdAtTo);

    return triggerSync(organizationEntity, filters, handler);
  }

  @GET
  @Path("/pipelines")
  @Timed
  @ExceptionMetered
  @ApiOperation(value = "trigger bulk sync for the pipelines entity using supplied filters")
  public RestResponse<String> triggerPipelinesSync(@QueryParam(PIPELINE_KEY) @Nullable String identifier,
      @QueryParam(ACCOUNT_KEY) @Nullable String accountId, @QueryParam(PROJECT_KEY) @Nullable String projectIdentifier,
      @QueryParam(HANDLER_KEY) @Nullable String handler, @QueryParam("createdAt_from") @Nullable Long createdAtFrom,
      @QueryParam("createdAt_to") @Nullable Long createdAtTo) {
    List<Bson> filters = new ArrayList<>();
    addEqFilter(filters, PipelineEntityKeys.identifier, identifier);
    addEqFilter(filters, PipelineEntityKeys.accountId, accountId);
    addEqFilter(filters, PipelineEntityKeys.projectIdentifier, projectIdentifier);
    addTsFilter(filters, PipelineEntityKeys.createdAt, createdAtFrom, createdAtTo);

    return triggerSync(pipelineCDCEntity, filters, handler);
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
      @QueryParam("startTs_from") @Nullable Long startTsFrom, @QueryParam("startTs_to") @Nullable Long startTsTo) {
    List<Bson> filters = new ArrayList<>();
    addEqFilter(filters, PlanExecutionSummaryKeys.planExecutionId, planExecutionId);
    addEqFilter(filters, PlanExecutionSummaryKeys.accountId, accountId);
    addEqFilter(filters, PlanExecutionSummaryKeys.projectIdentifier, projectIdentifier);
    addEqFilter(filters, PlanExecutionSummaryKeys.pipelineIdentifier, pipelineIdentifier);
    addTsFilter(filters, PlanExecutionSummaryKeys.startTs, startTsFrom, startTsTo);

    return triggerSync(pipelineExecutionSummaryEntityCDCEntity, filters, handler);
  }

  @GET
  @Path("/projects")
  @Timed
  @ExceptionMetered
  @ApiOperation(value = "trigger bulk sync for the projects entity using supplied filters")
  public RestResponse<String> triggerProjectsSync(@QueryParam(PROJECT_KEY) @Nullable String identifier,
      @QueryParam(ACCOUNT_KEY) @Nullable String accountId, @QueryParam(HANDLER_KEY) @Nullable String handler,
      @QueryParam("createdAt_from") @Nullable Long createdAtFrom,
      @QueryParam("createdAt_to") @Nullable Long createdAtTo) {
    List<Bson> filters = new ArrayList<>();
    addEqFilter(filters, ProjectKeys.identifier, identifier);
    addEqFilter(filters, ProjectKeys.accountIdentifier, accountId);
    addTsFilter(filters, ProjectKeys.createdAt, createdAtFrom, createdAtTo);

    return triggerSync(projectEntity, filters, handler);
  }

  @GET
  @Path("/services")
  @Timed
  @ExceptionMetered
  @ApiOperation(value = "trigger bulk sync for the services entity using supplied filters")
  public RestResponse<String> triggerServicesSync(@QueryParam(SERVICE_IDENTIFIER_KEY) @Nullable String identifier,
      @QueryParam(ACCOUNT_KEY) @Nullable String accountId, @QueryParam(HANDLER_KEY) @Nullable String handler,
      @QueryParam("createdAt_from") @Nullable Long createdAtFrom,
      @QueryParam("createdAt_to") @Nullable Long createdAtTo) {
    List<Bson> filters = new ArrayList<>();
    addEqFilter(filters, ServiceEntityKeys.identifier, identifier);
    addEqFilter(filters, ServiceEntityKeys.accountId, accountId);
    addTsFilter(filters, ServiceEntityKeys.createdAt, createdAtFrom, createdAtTo);

    return triggerSync(serviceCDCEntity, filters, handler);
  }

  @GET
  @Path("/connectors")
  @Timed
  @ExceptionMetered
  @ApiOperation(value = "trigger bulk sync for the connectors entity using supplied filters")
  public RestResponse<String> triggerConnectorsSync(@QueryParam(CONNECTOR_IDENTIFIER_KEY) @Nullable String identifier,
      @QueryParam(ACCOUNT_KEY) @Nullable String accountId, @QueryParam(HANDLER_KEY) @Nullable String handler,
      @QueryParam("createdAt_from") @Nullable Long createdAtFrom,
      @QueryParam("createdAt_to") @Nullable Long createdAtTo) {
    List<Bson> filters = new ArrayList<>();
    addEqFilter(filters, ConnectorKeys.identifier, identifier);
    addEqFilter(filters, ConnectorKeys.accountIdentifier, accountId);
    addTsFilter(filters, ConnectorKeys.createdAt, createdAtFrom, createdAtTo);

    return triggerSync(connectorCDCEntity, filters, handler);
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

  public RestResponse<String> triggerSync(CDCEntity<?> entity, List<Bson> filters, String handler) {
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

    int count = changeDataCaptureBulkMigrationHelper.doPartialSync(Set.of(entity), Filters.and(filters), handler);
    return new RestResponse<>(count + " events synced");
  }
}
