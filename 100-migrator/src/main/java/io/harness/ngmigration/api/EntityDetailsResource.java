/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.api;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import static software.wings.security.PermissionAttribute.PermissionType.LOGGED_IN;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

import io.harness.NGCommonEntityConstants;
import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.EmptyPredicate;
import io.harness.ngmigration.dto.BaseEntityDetailsDTO;
import io.harness.persistence.HPersistence;
import io.harness.rest.RestResponse;
import io.harness.security.annotations.NextGenManagerAuth;

import software.wings.beans.Pipeline;
import software.wings.beans.Pipeline.PipelineKeys;
import software.wings.beans.Workflow;
import software.wings.beans.Workflow.WorkflowKeys;
import software.wings.beans.trigger.Trigger;
import software.wings.beans.trigger.Trigger.TriggerKeys;
import software.wings.security.PermissionAttribute.ResourceType;
import software.wings.security.annotations.ApiKeyAuthorized;
import software.wings.security.annotations.Scope;

import com.codahale.metrics.annotation.ExceptionMetered;
import com.codahale.metrics.annotation.Timed;
import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(CDC)
@Slf4j
@Path("/")
@Consumes(APPLICATION_JSON)
@Produces(APPLICATION_JSON)
@Scope(ResourceType.APPLICATION)
@NextGenManagerAuth
public class EntityDetailsResource {
  @Inject HPersistence hPersistence;

  @GET
  @Path("/workflows")
  @Timed
  @ExceptionMetered
  @ApiKeyAuthorized(permissionType = LOGGED_IN)
  public RestResponse<List<BaseEntityDetailsDTO>> listWorkflows(
      @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId, @QueryParam("appId") String appId) {
    List<BaseEntityDetailsDTO> entities = new ArrayList<>();
    List<Workflow> workflowList = hPersistence.createQuery(Workflow.class)
                                      .filter(Workflow.ACCOUNT_ID_KEY, accountId)
                                      .filter(WorkflowKeys.appId, appId)
                                      .project(WorkflowKeys.uuid, true)
                                      .project(WorkflowKeys.name, true)
                                      .asList();

    if (EmptyPredicate.isNotEmpty(workflowList)) {
      entities = workflowList.stream()
                     .map(entity -> BaseEntityDetailsDTO.builder().id(entity.getUuid()).name(entity.getName()).build())
                     .collect(Collectors.toList());
    }
    return new RestResponse<>(entities);
  }

  @GET
  @Path("/pipelines")
  @Timed
  @ExceptionMetered
  @ApiKeyAuthorized(permissionType = LOGGED_IN)
  public RestResponse<List<BaseEntityDetailsDTO>> listPipelines(
      @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId, @QueryParam("appId") String appId) {
    List<BaseEntityDetailsDTO> entities = new ArrayList<>();
    List<Pipeline> pipelines = hPersistence.createQuery(Pipeline.class)
                                   .filter(Pipeline.ACCOUNT_ID_KEY, accountId)
                                   .filter(PipelineKeys.appId, appId)
                                   .project(PipelineKeys.uuid, true)
                                   .project(PipelineKeys.name, true)
                                   .asList();

    if (EmptyPredicate.isNotEmpty(pipelines)) {
      entities = pipelines.stream()
                     .map(entity -> BaseEntityDetailsDTO.builder().id(entity.getUuid()).name(entity.getName()).build())
                     .collect(Collectors.toList());
    }
    return new RestResponse<>(entities);
  }

  @GET
  @Path("/triggers")
  @Timed
  @ExceptionMetered
  @ApiKeyAuthorized(permissionType = LOGGED_IN)
  public RestResponse<List<BaseEntityDetailsDTO>> listTriggers(
      @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId, @QueryParam("appId") String appId) {
    List<BaseEntityDetailsDTO> entities = new ArrayList<>();
    List<Trigger> triggers = hPersistence.createQuery(Trigger.class)
                                 .filter(Trigger.ACCOUNT_ID_KEY, accountId)
                                 .filter(TriggerKeys.appId, appId)
                                 .project(TriggerKeys.uuid, true)
                                 .project(TriggerKeys.name, true)
                                 .asList();

    if (EmptyPredicate.isNotEmpty(triggers)) {
      entities = triggers.stream()
                     .map(entity -> BaseEntityDetailsDTO.builder().id(entity.getUuid()).name(entity.getName()).build())
                     .collect(Collectors.toList());
    }
    return new RestResponse<>(entities);
  }
}
