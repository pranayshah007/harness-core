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
import io.harness.beans.EncryptedData;
import io.harness.beans.EncryptedData.EncryptedDataKeys;
import io.harness.data.structure.EmptyPredicate;
import io.harness.ngmigration.dto.BaseEntityDetailsDTO;
import io.harness.persistence.HPersistence;
import io.harness.rest.RestResponse;
import io.harness.security.annotations.NextGenManagerAuth;

import software.wings.beans.Application;
import software.wings.beans.Environment;
import software.wings.beans.Environment.EnvironmentKeys;
import software.wings.beans.Pipeline;
import software.wings.beans.Pipeline.PipelineKeys;
import software.wings.beans.Service;
import software.wings.beans.Service.ServiceKeys;
import software.wings.beans.SettingAttribute;
import software.wings.beans.SettingAttribute.SettingAttributeKeys;
import software.wings.beans.Workflow;
import software.wings.beans.Workflow.WorkflowKeys;
import software.wings.beans.template.Template;
import software.wings.beans.template.Template.TemplateKeys;
import software.wings.beans.trigger.Trigger;
import software.wings.beans.trigger.Trigger.TriggerKeys;
import software.wings.security.PermissionAttribute.ResourceType;
import software.wings.security.annotations.ApiKeyAuthorized;
import software.wings.security.annotations.Scope;

import com.codahale.metrics.annotation.ExceptionMetered;
import com.codahale.metrics.annotation.Timed;
import com.google.inject.Inject;
import dev.morphia.query.Query;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

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

  @GET
  @Path("/apps")
  @Timed
  @ExceptionMetered
  @ApiKeyAuthorized(permissionType = LOGGED_IN)
  public RestResponse<List<BaseEntityDetailsDTO>> listApps(
      @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId) {
    List<BaseEntityDetailsDTO> entities = new ArrayList<>();
    List<Application> triggers = hPersistence.createQuery(Application.class)
                                     .filter(Trigger.ACCOUNT_ID_KEY, accountId)
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

  @GET
  @Path("/connectors")
  @Timed
  @ExceptionMetered
  @ApiKeyAuthorized(permissionType = LOGGED_IN)
  public RestResponse<List<BaseEntityDetailsDTO>> listConnectors(
      @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId) {
    List<BaseEntityDetailsDTO> entities = new ArrayList<>();
    List<SettingAttribute> triggers = hPersistence.createQuery(SettingAttribute.class)
                                          .filter(SettingAttribute.ACCOUNT_ID_KEY, accountId)
                                          .project(SettingAttributeKeys.uuid, true)
                                          .project(SettingAttributeKeys.name, true)
                                          .asList();

    if (EmptyPredicate.isNotEmpty(triggers)) {
      entities = triggers.stream()
                     .map(entity -> BaseEntityDetailsDTO.builder().id(entity.getUuid()).name(entity.getName()).build())
                     .collect(Collectors.toList());
    }
    return new RestResponse<>(entities);
  }

  @GET
  @Path("/services")
  @Timed
  @ExceptionMetered
  @ApiKeyAuthorized(permissionType = LOGGED_IN)
  public RestResponse<List<BaseEntityDetailsDTO>> listServices(
      @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId, @QueryParam("appId") String appId) {
    List<BaseEntityDetailsDTO> entities = new ArrayList<>();
    List<Service> services = hPersistence.createQuery(Service.class)
                                 .filter(Service.ACCOUNT_ID_KEY, accountId)
                                 .filter(ServiceKeys.appId, appId)
                                 .project(ServiceKeys.uuid, true)
                                 .project(ServiceKeys.name, true)
                                 .asList();

    if (EmptyPredicate.isNotEmpty(services)) {
      entities = services.stream()
                     .map(entity -> BaseEntityDetailsDTO.builder().id(entity.getUuid()).name(entity.getName()).build())
                     .collect(Collectors.toList());
    }
    return new RestResponse<>(entities);
  }

  @GET
  @Path("/environments")
  @Timed
  @ExceptionMetered
  @ApiKeyAuthorized(permissionType = LOGGED_IN)
  public RestResponse<List<BaseEntityDetailsDTO>> listEnvironments(
      @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId, @QueryParam("appId") String appId) {
    List<BaseEntityDetailsDTO> entities = new ArrayList<>();
    List<Environment> environments = hPersistence.createQuery(Environment.class)
                                         .filter(Environment.ACCOUNT_ID_KEY, accountId)
                                         .filter(EnvironmentKeys.appId, appId)
                                         .project(EnvironmentKeys.uuid, true)
                                         .project(EnvironmentKeys.name, true)
                                         .asList();

    if (EmptyPredicate.isNotEmpty(environments)) {
      entities = environments.stream()
                     .map(entity -> BaseEntityDetailsDTO.builder().id(entity.getUuid()).name(entity.getName()).build())
                     .collect(Collectors.toList());
    }
    return new RestResponse<>(entities);
  }

  @GET
  @Path("/secrets")
  @Timed
  @ExceptionMetered
  @ApiKeyAuthorized(permissionType = LOGGED_IN)
  public RestResponse<List<BaseEntityDetailsDTO>> listSecrets(
      @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId) {
    List<BaseEntityDetailsDTO> entities = new ArrayList<>();
    List<EncryptedData> secrets = hPersistence.createQuery(EncryptedData.class)
                                      .filter(EncryptedData.ACCOUNT_ID_KEY, accountId)
                                      .project(EncryptedDataKeys.uuid, true)
                                      .project(EncryptedDataKeys.name, true)
                                      .asList();

    if (EmptyPredicate.isNotEmpty(secrets)) {
      entities = secrets.stream()
                     .map(entity -> BaseEntityDetailsDTO.builder().id(entity.getUuid()).name(entity.getName()).build())
                     .collect(Collectors.toList());
    }
    return new RestResponse<>(entities);
  }

  @GET
  @Path("/templates")
  @Timed
  @ExceptionMetered
  @ApiKeyAuthorized(permissionType = LOGGED_IN)
  public RestResponse<List<BaseEntityDetailsDTO>> listTemplates(
      @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId, @QueryParam("appId") String appId) {
    List<BaseEntityDetailsDTO> entities = new ArrayList<>();
    Query<Template> query = hPersistence.createQuery(Template.class).filter(TemplateKeys.accountId, accountId);
    if (StringUtils.isNotBlank(appId)) {
      query = query.filter(TemplateKeys.appId, appId);
    }
    List<Template> templates = query.project(TemplateKeys.uuid, true).project(TemplateKeys.name, true).asList();

    if (EmptyPredicate.isNotEmpty(templates)) {
      entities = templates.stream()
                     .map(entity -> BaseEntityDetailsDTO.builder().id(entity.getUuid()).name(entity.getName()).build())
                     .collect(Collectors.toList());
    }
    return new RestResponse<>(entities);
  }
}
