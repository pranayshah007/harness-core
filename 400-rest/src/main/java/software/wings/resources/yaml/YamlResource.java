/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.resources.yaml;

import static io.harness.annotations.dev.HarnessTeam.DX;
import static io.harness.beans.PageResponse.PageResponseBuilder.aPageResponse;
import static io.harness.validation.Validator.notNullCheck;

import static software.wings.beans.CGConstants.GLOBAL_APP_ID;
import static software.wings.security.PermissionAttribute.PermissionType.ACCOUNT_MANAGEMENT;
import static software.wings.security.PermissionAttribute.PermissionType.APP_TEMPLATE;
import static software.wings.security.PermissionAttribute.PermissionType.ENV;
import static software.wings.security.PermissionAttribute.PermissionType.LOGGED_IN;
import static software.wings.security.PermissionAttribute.PermissionType.MANAGE_ACCOUNT_DEFAULTS;
import static software.wings.security.PermissionAttribute.PermissionType.MANAGE_APPLICATIONS;
import static software.wings.security.PermissionAttribute.PermissionType.MANAGE_CLOUD_PROVIDERS;
import static software.wings.security.PermissionAttribute.PermissionType.MANAGE_CONFIG_AS_CODE;
import static software.wings.security.PermissionAttribute.PermissionType.MANAGE_CONNECTORS;
import static software.wings.security.PermissionAttribute.PermissionType.MANAGE_DEPLOYMENT_FREEZES;
import static software.wings.security.PermissionAttribute.PermissionType.MANAGE_TAGS;
import static software.wings.security.PermissionAttribute.PermissionType.PIPELINE;
import static software.wings.security.PermissionAttribute.PermissionType.PROVISIONER;
import static software.wings.security.PermissionAttribute.PermissionType.SERVICE;
import static software.wings.security.PermissionAttribute.PermissionType.TEMPLATE_MANAGEMENT;
import static software.wings.security.PermissionAttribute.PermissionType.WORKFLOW;
import static software.wings.security.PermissionAttribute.ResourceType.SETTING;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.MediaType.MULTIPART_FORM_DATA;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.CgEventConfig;
import io.harness.beans.PageResponse;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.UnauthorizedException;
import io.harness.exception.WingsException;
import io.harness.rest.RestResponse;
import io.harness.security.annotations.PublicApiWithWhitelist;
import io.harness.stream.BoundedInputStream;
import io.harness.yaml.BaseYaml;

import software.wings.app.MainConfiguration;
import software.wings.beans.Application;
import software.wings.beans.Base;
import software.wings.beans.ConfigFile;
import software.wings.beans.EntityType;
import software.wings.beans.Environment;
import software.wings.beans.InfrastructureProvisioner;
import software.wings.beans.Pipeline;
import software.wings.beans.Service;
import software.wings.beans.SettingAttribute;
import software.wings.beans.User;
import software.wings.beans.Workflow;
import software.wings.beans.appmanifest.ManifestFile;
import software.wings.beans.artifact.ArtifactStream;
import software.wings.beans.command.ServiceCommand;
import software.wings.beans.template.Template;
import software.wings.beans.yaml.EntityInformation;
import software.wings.exception.YamlProcessingException;
import software.wings.infra.InfrastructureDefinition;
import software.wings.security.PermissionAttribute.Action;
import software.wings.security.UserThreadLocal;
import software.wings.security.annotations.ApiKeyAuthorized;
import software.wings.security.annotations.AuthRule;
import software.wings.security.annotations.Scope;
import software.wings.service.impl.security.auth.DefaultsAuthHandler;
import software.wings.service.impl.security.auth.TemplateAuthHandler;
import software.wings.service.intfc.AuthService;
import software.wings.service.intfc.HarnessUserGroupService;
import software.wings.service.intfc.yaml.AppYamlResourceService;
import software.wings.service.intfc.yaml.YamlArtifactStreamService;
import software.wings.service.intfc.yaml.YamlDirectoryService;
import software.wings.service.intfc.yaml.YamlGitService;
import software.wings.service.intfc.yaml.YamlResourceService;
import software.wings.service.intfc.yaml.clone.YamlCloneService;
import software.wings.service.intfc.yaml.sync.YamlService;
import software.wings.yaml.FileOperationStatus;
import software.wings.yaml.YamlHelper;
import software.wings.yaml.YamlOperationResponse;
import software.wings.yaml.YamlPayload;
import software.wings.yaml.directory.DirectoryNode;
import software.wings.yaml.errorhandling.GitSyncError;
import software.wings.yaml.gitSync.GitSyncWebhook;
import software.wings.yaml.gitSync.YamlGitConfig;

import com.codahale.metrics.annotation.ExceptionMetered;
import com.codahale.metrics.annotation.Timed;
import com.google.common.base.Charsets;
import com.google.inject.Inject;
import io.swagger.annotations.Api;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.List;
import java.util.concurrent.ExecutorService;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import lombok.extern.slf4j.Slf4j;
import org.glassfish.jersey.media.multipart.FormDataContentDisposition;
import org.glassfish.jersey.media.multipart.FormDataParam;
import org.hibernate.validator.constraints.NotEmpty;
import org.jvnet.hk2.annotations.Optional;

/**
 * Created by bsollish
 */
@Api("setup-as-code/yaml")
@Path("setup-as-code/yaml")
@Produces(APPLICATION_JSON)
@Scope(SETTING)
@ApiKeyAuthorized(permissionType = LOGGED_IN, skipAuth = true)
@OwnedBy(DX)
@Slf4j
public class YamlResource {
  private YamlResourceService yamlResourceService;
  private AppYamlResourceService appYamlResourceService;
  private YamlArtifactStreamService yamlArtifactStreamService;
  private YamlService yamlService;
  private YamlDirectoryService yamlDirectoryService;
  private YamlGitService yamlGitService;
  private AuthService authService;
  private HarnessUserGroupService harnessUserGroupService;
  @Inject private MainConfiguration configuration;
  @Inject private YamlCloneService yamlCloneService;
  @Inject private ExecutorService executorService;
  @Inject TemplateAuthHandler templateAuthHandler;
  @Inject DefaultsAuthHandler defaultsAuthHandler;

  /**
   * Instantiates a new service resource.
   *
   * @param yamlResourceService       the yaml resource servicewe
   * @param appYamlResourceService    the app yaml resource service
   * @param yamlDirectoryService      the yaml directory service
   * @param yamlArtifactStreamService the yaml artifact stream service
   * @param yamlService               the yaml service
   * @param yamlGitSyncService
   */
  @Inject
  public YamlResource(YamlResourceService yamlResourceService, AppYamlResourceService appYamlResourceService,
      YamlDirectoryService yamlDirectoryService, YamlArtifactStreamService yamlArtifactStreamService,
      YamlService yamlService, YamlGitService yamlGitSyncService, AuthService authService,
      HarnessUserGroupService harnessUserGroupService) {
    this.yamlResourceService = yamlResourceService;
    this.appYamlResourceService = appYamlResourceService;
    this.yamlDirectoryService = yamlDirectoryService;
    this.yamlArtifactStreamService = yamlArtifactStreamService;
    this.yamlService = yamlService;
    this.yamlGitService = yamlGitSyncService;
    this.harnessUserGroupService = harnessUserGroupService;
    this.authService = authService;
  }

  /**
   * Gets the yaml for a workflow
   *
   * @param appId      the app id
   * @param workflowId the workflow id
   * @return the rest response
   */
  @GET
  @Path("/workflows/{workflowId}")
  @Timed
  @ExceptionMetered
  @ApiKeyAuthorized(permissionType = WORKFLOW, action = Action.READ)
  @AuthRule(permissionType = WORKFLOW, action = Action.READ)
  public RestResponse<YamlPayload> getWorkflow(
      @QueryParam("appId") String appId, @PathParam("workflowId") String workflowId) {
    return yamlResourceService.getWorkflow(appId, workflowId);
  }

  /**
   * Gets the yaml for global template library
   *
   * @param accountId  the account id
   * @param templateId the template id
   * @return the rest response
   */
  @GET
  @Path("/templates/{templateId}")
  @Timed
  @ExceptionMetered
  public RestResponse<YamlPayload> getTemplateLibrary(@QueryParam("accountId") String accountId,
      @PathParam("templateId") String templateId, @DefaultValue(GLOBAL_APP_ID) @QueryParam("appId") String appId) {
    return yamlResourceService.getTemplateLibrary(accountId, appId, templateId);
  }

  @PUT
  @Path("/templates/{templateId}")
  @Timed
  @ExceptionMetered
  @ApiKeyAuthorized(permissionType = TEMPLATE_MANAGEMENT, action = Action.UPDATE, skipAuth = true)
  @AuthRule(permissionType = APP_TEMPLATE, skipAuth = true)
  public RestResponse<Template> updateTemplate(@QueryParam("accountId") String accountId,
      @DefaultValue(GLOBAL_APP_ID) @QueryParam("appId") String appId, YamlPayload yamlPayload,
      @PathParam("templateId") String templateId) {
    templateAuthHandler.authorizeUpdate(appId, templateId);
    return yamlService.update(yamlPayload, accountId, templateId);
  }

  /**
   * Gets the yaml for a workflow
   *
   * @param appId         the app id
   * @param provisionerId the provisioner id
   * @return the rest response
   */
  @GET
  @Path("/infrastructureprovisioners/{infraProvisionerId}")
  @Timed
  @ExceptionMetered
  @ApiKeyAuthorized(permissionType = PROVISIONER, action = Action.READ)
  @AuthRule(permissionType = PROVISIONER, action = Action.READ)
  public RestResponse<YamlPayload> getProvisioner(
      @QueryParam("appId") String appId, @PathParam("infraProvisionerId") String provisionerId) {
    return yamlResourceService.getProvisioner(appId, provisionerId);
  }

  @PUT
  @Path("/infrastructureprovisioners/{infraProvisionerId}")
  @Timed
  @ExceptionMetered
  @ApiKeyAuthorized(permissionType = PROVISIONER, action = Action.UPDATE)
  @AuthRule(permissionType = PROVISIONER, action = Action.UPDATE)
  public RestResponse<InfrastructureProvisioner> updateProvisioner(@QueryParam("accountId") String accountId,
      @QueryParam("appId") String appId, YamlPayload yamlPayload,
      @PathParam("infraProvisionerId") String infraProvisionerId) {
    return yamlService.update(yamlPayload, accountId, infraProvisionerId);
  }

  // TODO - need to add a PUT for updateWorkflow HERE

  /**
   * Gets the yaml version of a trigger by artifactStreamId
   *
   * @param appId            the app id
   * @param artifactStreamId the artifact stream id
   * @return the rest response
   */
  @GET
  @Path("/artifactTriggers/{artifactStreamId}")
  @Timed
  @ExceptionMetered
  public RestResponse<YamlPayload> getArtifactTrigger(
      @QueryParam("appId") String appId, @PathParam("artifactStreamId") String artifactStreamId) {
    return yamlResourceService.getArtifactTrigger(appId, artifactStreamId);
  }

  /**
   * Update a trigger that is sent as Yaml (in a JSON "wrapper")
   *
   * @param yamlPayload the yaml version of the service command
   * @param accountId   the account id
   * @return the rest response
   */
  @PUT
  @Path("/artifactTriggers/{artifactStreamId}")
  @Timed
  @ExceptionMetered
  @ApiKeyAuthorized(permissionType = ACCOUNT_MANAGEMENT)
  @AuthRule(permissionType = ACCOUNT_MANAGEMENT)
  public RestResponse<ArtifactStream> updateArtifactTrigger(@QueryParam("accountId") String accountId,
      @QueryParam("appId") String appId, YamlPayload yamlPayload,
      @PathParam("artifactStreamId") String artifactStreamId) {
    return yamlService.update(yamlPayload, accountId, artifactStreamId);
  }

  /**
   * Gets the yaml version of a trigger by trigger id
   *
   * @param appId     the app id
   * @param triggerId the artifact stream id
   * @return the rest response
   */
  @GET
  @Path("/triggers/{triggerId}")
  @Timed
  @ExceptionMetered
  public RestResponse<YamlPayload> getTrigger(
      @QueryParam("appId") String appId, @PathParam("triggerId") String triggerId) {
    return yamlResourceService.getTrigger(appId, triggerId);
  }

  /**
   * Update a trigger that is sent as Yaml (in a JSON "wrapper")
   *
   * @param yamlPayload the yaml version of the service command
   * @param accountId   the account id
   * @return the rest response
   */
  @PUT
  @Path("/triggers/{triggerId}")
  @Timed
  @ExceptionMetered
  @ApiKeyAuthorized(permissionType = ACCOUNT_MANAGEMENT)
  @AuthRule(permissionType = ACCOUNT_MANAGEMENT)
  public RestResponse<Base> updateTrigger(@QueryParam("appId") String appId, @QueryParam("accountId") String accountId,
      YamlPayload yamlPayload, @QueryParam("deleteEnabled") @DefaultValue("false") boolean deleteEnabled,
      @PathParam("triggerId") String triggerId) {
    return yamlService.update(yamlPayload, accountId, triggerId);
  }

  /**
   * Gets the yaml version of a pipeline by pipelineId
   *
   * @param appId      the app id
   * @param pipelineId the pipeline id
   * @return the rest response
   */
  @GET
  @Path("/pipelines/{pipelineId}")
  @Timed
  @ExceptionMetered
  @ApiKeyAuthorized(permissionType = PIPELINE, action = Action.READ)
  @AuthRule(permissionType = PIPELINE, action = Action.READ)
  public RestResponse<YamlPayload> getPipeline(
      @QueryParam("appId") String appId, @PathParam("pipelineId") String pipelineId) {
    return yamlResourceService.getPipeline(appId, pipelineId);
  }

  @GET
  @Path("/cgeventconfigs/{eventConfigId}")
  @Timed
  @ExceptionMetered
  public RestResponse<YamlPayload> getCgEventConfig(@QueryParam("appId") String appId,
      @QueryParam("accountId") String accountId, @PathParam("eventConfigId") String eventConfigId) {
    return yamlResourceService.getEventConfig(appId, eventConfigId);
  }

  @GET
  @Path("/application-manifests/{applicationManifestId}")
  @Timed
  @ExceptionMetered
  @ApiKeyAuthorized(permissionType = SERVICE, action = Action.READ)
  @AuthRule(permissionType = SERVICE, action = Action.READ)
  public RestResponse<YamlPayload> getApplicationManifestId(@QueryParam("appId") String appId,
      @QueryParam("serviceId") String serviceId, @PathParam("applicationManifestId") String applicationManifestId) {
    return yamlResourceService.getApplicationManifest(appId, applicationManifestId);
  }

  @PUT
  @Path("/application-manifests/{applicationManifestId}")
  @Timed
  @ExceptionMetered
  @AuthRule(permissionType = SERVICE, action = Action.UPDATE, skipAuth = true)
  public RestResponse<YamlPayload> updateApplicationManifest(@QueryParam("accountId") String accountId,
      @QueryParam("appId") String appId, @QueryParam("serviceId") String serviceId,
      @PathParam("applicationManifestId") String applicationManifestId, YamlPayload yamlPayload) {
    return yamlService.update(yamlPayload, accountId, applicationManifestId);
  }

  @GET
  @Path("/manifest-files/{manifestFileId}")
  @Timed
  @ExceptionMetered
  @ApiKeyAuthorized(permissionType = SERVICE, action = Action.READ)
  @AuthRule(permissionType = SERVICE, action = Action.READ)
  public RestResponse<YamlPayload> getApplicationManifestFile(@QueryParam("appId") String appId,
      @QueryParam("serviceId") String serviceId, @PathParam("manifestFileId") String manifestFileId) {
    return yamlResourceService.getManifestFile(appId, manifestFileId);
  }

  @PUT
  @Path("/manifest-files/{manifestFileId}")
  @Timed
  @ExceptionMetered
  @AuthRule(permissionType = SERVICE, action = Action.UPDATE, skipAuth = true)
  public RestResponse<ManifestFile> updateManifestFile(@QueryParam("accountId") String accountId,
      @QueryParam("serviceId") String serviceId, @QueryParam("appId") String appId, YamlPayload yamlPayload,
      @PathParam("manifestFileId") String manifestFileId) {
    return yamlService.update(yamlPayload, accountId, manifestFileId);
  }

  /**
   * Update a pipeline that is sent as Yaml (in a JSON "wrapper")
   *
   * @param appId       the app id
   * @param yamlPayload the yaml version of the service command
   * @return the rest response
   */
  @PUT
  @Path("/pipelines/{pipelineId}")
  @Timed
  @ExceptionMetered
  @ApiKeyAuthorized(permissionType = PIPELINE, action = Action.UPDATE)
  @AuthRule(permissionType = PIPELINE, action = Action.UPDATE)
  public RestResponse<Pipeline> updatePipeline(@QueryParam("accountId") String accountId,
      @QueryParam("appId") String appId, YamlPayload yamlPayload, @PathParam("pipelineId") String pipelineId) {
    return yamlService.update(yamlPayload, accountId, pipelineId);
  }

  @PUT
  @Path("/cgeventconfigs/{eventConfigId}")
  @Timed
  @ExceptionMetered
  public RestResponse<CgEventConfig> updateCgEventConfig(@QueryParam("accountId") String accountId,
      @QueryParam("appId") String appId, YamlPayload yamlPayload, @PathParam("eventConfigId") String eventConfigId) {
    return yamlService.update(yamlPayload, accountId, eventConfigId);
  }

  @PUT
  @Path("/workflows/{workflowId}")
  @Timed
  @ExceptionMetered
  @ApiKeyAuthorized(permissionType = WORKFLOW, action = Action.UPDATE)
  @AuthRule(permissionType = WORKFLOW, action = Action.UPDATE)
  public RestResponse<Workflow> updateWorkflow(@QueryParam("accountId") String accountId,
      @QueryParam("appId") String appId, YamlPayload yamlPayload, @PathParam("workflowId") String workflowId) {
    return yamlService.update(yamlPayload, accountId, workflowId);
  }

  /**
   * Gets the yaml version of a notification group by id
   *
   * @param accountId           the account id
   * @param notificationGroupId the notification group id
   * @return the rest response
   */
  @GET
  @Path("/notification-groups/{notificationGroupId}")
  @Timed
  @ExceptionMetered
  public RestResponse<YamlPayload> getNotificationGroup(
      @QueryParam("accountId") String accountId, @PathParam("notificationGroupId") String notificationGroupId) {
    return yamlResourceService.getNotificationGroup(accountId, notificationGroupId);
  }

  /**
   * Gets the yaml version of a service command by serviceCommandId
   *
   * @param appId            the app id
   * @param serviceCommandId the service command id
   * @return the rest response
   */
  @GET
  @Path("/service-commands/{serviceCommandId}")
  @Timed
  @ExceptionMetered
  public RestResponse<YamlPayload> getServiceCommand(
      @QueryParam("appId") String appId, @PathParam("serviceCommandId") String serviceCommandId) {
    return yamlResourceService.getServiceCommand(appId, serviceCommandId);
  }

  /**
   * Update a service command that is sent as Yaml (in a JSON "wrapper")
   *
   * @param accountId   the account id
   * @param yamlPayload the yaml version of the service command
   * @return the rest response
   */
  @PUT
  @Path("/service-commands/{serviceCommandId}")
  @Timed
  @ExceptionMetered
  @ApiKeyAuthorized(permissionType = SERVICE, action = Action.UPDATE)
  @AuthRule(permissionType = SERVICE, action = Action.UPDATE)
  public RestResponse<ServiceCommand> updateServiceCommand(@QueryParam("accountId") String accountId,
      YamlPayload yamlPayload, @PathParam("serviceCommandId") String serviceCommandId) {
    return yamlService.update(yamlPayload, accountId, serviceCommandId);
  }

  /**
   * Gets all the setting attributes of a given type by accountId
   *
   * @param accountId the account id
   * @param type      the SettingVariableTypes
   * @return the rest response
   */
  @GET
  @Path("/settings")
  @Timed
  @ExceptionMetered
  public RestResponse<YamlPayload> getSettingAttributesList(
      @QueryParam("accountId") String accountId, @QueryParam("type") String type) {
    return yamlResourceService.getGlobalSettingAttributesList(accountId, type);
  }

  /**
   * Gets the yaml for a setting attribute by accountId and uuid
   *
   * @param accountId the account id
   * @param uuid      the uid of the setting attribute
   * @return the rest response
   */
  @GET
  @Path("/settings/{uuid}")
  @Timed
  @ExceptionMetered
  public RestResponse<YamlPayload> getSettingAttribute(
      @QueryParam("accountId") String accountId, @PathParam("uuid") String uuid) {
    return yamlResourceService.getSettingAttribute(accountId, uuid);
  }

  /**
   * Gets the yaml for a setting attribute by accountId and uuid
   *
   * @param accountId the account id
   * @param uuid      the uid of the setting attribute
   * @return the rest response
   */
  @GET
  @Path("/defaults/{uuid}")
  @Timed
  @ExceptionMetered
  public RestResponse<YamlPayload> getDefaults(
      @QueryParam("accountId") String accountId, @PathParam("uuid") String uuid) {
    defaultsAuthHandler.authorizeUsage(uuid, accountId);
    return yamlResourceService.getDefaultVariables(accountId, uuid);
  }

  /**
   * Update defaults that is sent as Yaml
   *
   * @param accountId   the account id
   * @param yamlPayload the yaml version of the defaults
   * @return the rest response
   */
  @PUT
  @Path("/defaults/{uuid}")
  @Timed
  @ExceptionMetered
  @ApiKeyAuthorized(permissionType = MANAGE_ACCOUNT_DEFAULTS, skipAuth = true)
  @AuthRule(permissionType = MANAGE_ACCOUNT_DEFAULTS, skipAuth = true)
  public RestResponse<ServiceCommand> updateDefaults(
      @QueryParam("accountId") String accountId, YamlPayload yamlPayload, @PathParam("uuid") String uuid) {
    defaultsAuthHandler.authorizeUsage(uuid, accountId);
    return yamlService.update(yamlPayload, accountId, uuid);
  }

  /**
   * Update setting attribute sent as Yaml (in a JSON "wrapper")
   *
   * @param accountId     the account id
   * @param uuid          the uid of the setting attribute
   * @param type          the SettingVariableTypes
   * @param yamlPayload   the yaml version of setup
   * @param deleteEnabled the delete enabled
   * @return the rest response
   */
  @PUT
  @Path("/settings/{uuid}")
  @Timed
  @ExceptionMetered
  @ApiKeyAuthorized(permissionType = MANAGE_CONNECTORS)
  @AuthRule(permissionType = MANAGE_CONNECTORS)
  @AuthRule(permissionType = MANAGE_CLOUD_PROVIDERS)
  public RestResponse<SettingAttribute> updateSettingAttribute(@QueryParam("accountId") String accountId,
      @PathParam("uuid") String uuid, @QueryParam("type") String type, YamlPayload yamlPayload,
      @QueryParam("deleteEnabled") @DefaultValue("false") boolean deleteEnabled) {
    return yamlService.update(yamlPayload, accountId, uuid);
  }

  /**
   * Gets the yaml version of an environment by envId
   *
   * @param appId the app id
   * @param envId the environment id
   * @return the rest response
   */
  @GET
  @Path("/environments/{envId}")
  @Timed
  @ExceptionMetered
  @ApiKeyAuthorized(permissionType = ENV, action = Action.READ)
  @AuthRule(permissionType = ENV, action = Action.READ)
  public RestResponse<YamlPayload> getEnvironment(@QueryParam("appId") String appId, @PathParam("envId") String envId) {
    return yamlResourceService.getEnvironment(appId, envId);
  }

  @GET
  @Path("/configs/{configId}")
  @Timed
  @ExceptionMetered
  public RestResponse<YamlPayload> getConfigFile(@QueryParam("accountId") String accountId,
      @QueryParam("appId") String appId, @PathParam("configId") String configId) {
    return yamlResourceService.getConfigFileYaml(accountId, appId, configId);
  }

  /**
   * Update a environment that is sent as Yaml (in a JSON "wrapper")
   *
   * @param accountId   the account id
   * @param appId       the app id
   * @param yamlPayload the yaml version of environment
   * @return the rest response
   */
  @PUT
  @Path("/environments/{envId}")
  @Timed
  @ExceptionMetered
  @ApiKeyAuthorized(permissionType = ENV, action = Action.UPDATE)
  @AuthRule(permissionType = ENV, action = Action.UPDATE)
  public RestResponse<Environment> updateEnvironment(@QueryParam("accountId") String accountId,
      @QueryParam("appId") String appId, YamlPayload yamlPayload, @PathParam("envId") String envId) {
    return yamlService.update(yamlPayload, accountId, envId);
  }

  /**
   * Gets the yaml version of a service by serviceId
   *
   * @param appId     the app id
   * @param serviceId the service id
   * @return the rest response
   */
  @GET
  @Path("/services/{serviceId}")
  @Timed
  @ExceptionMetered
  @ApiKeyAuthorized(permissionType = SERVICE, action = Action.READ)
  @AuthRule(permissionType = SERVICE, action = Action.READ)
  public RestResponse<YamlPayload> getService(
      @QueryParam("appId") String appId, @PathParam("serviceId") String serviceId) {
    return yamlResourceService.getService(appId, serviceId);
  }

  /**
   * Update a service that is sent as Yaml (in a JSON "wrapper")
   *
   * @param appId       the app id
   * @param accountId   the account id
   * @param yamlPayload the yaml version of service
   * @return the rest response
   */
  @PUT
  @Path("/services/{serviceId}")
  @Timed
  @ExceptionMetered
  @ApiKeyAuthorized(permissionType = SERVICE, action = Action.UPDATE)
  @AuthRule(permissionType = SERVICE, action = Action.UPDATE)
  public RestResponse<Service> updateService(@QueryParam("accountId") String accountId,
      @QueryParam("appId") String appId, YamlPayload yamlPayload, @PathParam("serviceId") String serviceId) {
    return yamlService.update(yamlPayload, accountId, serviceId);
  }

  /**
   * Update a config file that is sent as Yaml (in a JSON "wrapper")
   *
   * @param appId         app id
   * @param configId      the config id
   * @param yamlPayload   the yaml version of configFile
   * @param deleteEnabled
   * @return
   */
  @PUT
  @Path("/configs/{configId}")
  @Timed
  @ExceptionMetered
  @AuthRule(permissionType = SERVICE, action = Action.UPDATE, skipAuth = true)
  public RestResponse<ConfigFile> updateConfigFile(@QueryParam("accountId") String accountId,
      @QueryParam("appId") String appId, @PathParam("configId") String configId, YamlPayload yamlPayload,
      @QueryParam("deleteEnabled") @DefaultValue("false") boolean deleteEnabled) {
    return yamlService.update(yamlPayload, accountId, configId);
  }

  /**
   * Gets the yaml version of an app by appId
   *
   * @param appId the app id
   * @return the rest response
   */
  @GET
  @Path("/applications/{appId}")
  @Timed
  @ExceptionMetered
  public RestResponse<YamlPayload> getApp(@PathParam("appId") String appId) {
    return appYamlResourceService.getApp(appId);
  }

  /**
   * Update an app that is sent as Yaml (in a JSON "wrapper")
   *
   * @param appId         the app id
   * @param yamlPayload   the yaml version of app
   * @param deleteEnabled the delete enabled
   * @return the rest response
   */
  @PUT
  @Path("/applications/{appId}")
  @Timed
  @ExceptionMetered
  @ApiKeyAuthorized(permissionType = MANAGE_APPLICATIONS)
  @AuthRule(permissionType = MANAGE_APPLICATIONS)
  public RestResponse<Application> updateApp(@PathParam("appId") String appId, YamlPayload yamlPayload,
      @QueryParam("deleteEnabled") @DefaultValue("false") boolean deleteEnabled) {
    return appYamlResourceService.updateApp(appId, yamlPayload, deleteEnabled);
  }

  /**
   * Gets the config as code directory by accountId
   *
   * @param accountId the account id
   * @return the rest response
   */
  @GET
  @Path("/directory")
  @Timed
  @ExceptionMetered
  @ApiKeyAuthorized(permissionType = LOGGED_IN)
  public RestResponse<DirectoryNode> getDirectory(
      @QueryParam("accountId") String accountId, @QueryParam("appId") String appId) {
    return new RestResponse<>(yamlDirectoryService.getDirectory(accountId, appId));
  }

  @GET
  @Path("/manifest")
  @Timed
  @ExceptionMetered
  @ApiKeyAuthorized(permissionType = SERVICE, action = Action.READ)
  @AuthRule(permissionType = SERVICE, action = Action.READ)
  public RestResponse<DirectoryNode> getApplicationManifestForService(@QueryParam("accountId") String accountId,
      @QueryParam("appId") String appId, @QueryParam("serviceId") String serviceId) {
    return new RestResponse<>(yamlDirectoryService.getApplicationManifestYamlFolderNode(accountId, appId, serviceId));
  }

  /**
   * Gets the Application config as code by AppId
   *
   * @param accountId the account id
   * @return the rest response
   */
  @GET
  @Path("/application")
  @Timed
  @ExceptionMetered
  public RestResponse<DirectoryNode> getApplication(
      @QueryParam("accountId") String accountId, @QueryParam("appId") String appId) {
    return new RestResponse<>(yamlDirectoryService.getApplicationYamlFolderNode(accountId, appId));
  }

  /**
   * Push directory rest response.
   *
   * @param accountId the account id
   * @return the rest response
   */
  @POST
  @Path("full-sync/{entityId}")
  @Timed
  @ExceptionMetered
  @ApiKeyAuthorized(permissionType = MANAGE_CONFIG_AS_CODE)
  @AuthRule(permissionType = MANAGE_CONFIG_AS_CODE)
  public RestResponse pushDirectory(@PathParam("entityId") String entityId, @QueryParam("accountId") String accountId,
      @QueryParam("entityType") EntityType entityType) {
    yamlGitService.fullSync(accountId, entityId, entityType, true);
    return new RestResponse<>();
  }

  /**
   * Gets artifact stream.
   *
   * @param appId            the app id
   * @param artifactStreamId the artifact stream id
   * @return the artifact stream
   */
  @GET
  @Path("/artifact-streams/{artifactStreamId}")
  @Timed
  @ExceptionMetered
  public RestResponse<YamlPayload> getArtifactStream(
      @QueryParam("appId") String appId, @PathParam("artifactStreamId") String artifactStreamId) {
    return yamlArtifactStreamService.getArtifactStreamYaml(appId, artifactStreamId);
  }

  /**
   * Update a config file that is sent as Yaml (in a JSON "wrapper")
   *
   * @param appId            app id
   * @param artifactStreamId the artifact stream id
   * @param yamlPayload      the yaml version of configFile
   * @param deleteEnabled    the delete enabled
   * @return rest response
   */
  @PUT
  @Path("/artifact-streams/{artifactStreamId}")
  @Timed
  @ExceptionMetered
  @AuthRule(permissionType = SERVICE, action = Action.UPDATE, skipAuth = true)
  public RestResponse<Base> updateArtifactStream(@QueryParam("appId") String appId,
      @PathParam("artifactStreamId") String artifactStreamId, YamlPayload yamlPayload,
      @QueryParam("deleteEnabled") @DefaultValue("false") boolean deleteEnabled) {
    return yamlArtifactStreamService.updateArtifactStream(appId, artifactStreamId, yamlPayload, deleteEnabled);
  }

  @GET
  @Path("/infrastructuremappings/{infraMappingId}")
  @Timed
  @ExceptionMetered
  public RestResponse<YamlPayload> getInfraMapping(@QueryParam("appId") String appId,
      @QueryParam("accountId") String accountId, @PathParam("infraMappingId") String infraMappingId) {
    return yamlResourceService.getInfraMapping(accountId, appId, infraMappingId);
  }

  @PUT
  @Path("/infrastructuremappings/{infraMappingId}")
  @Timed
  @ExceptionMetered
  @ApiKeyAuthorized(permissionType = ENV, action = Action.UPDATE)
  @AuthRule(permissionType = ENV, action = Action.UPDATE)
  public RestResponse<Base> updateInfraMapping(@QueryParam("appId") String appId,
      @QueryParam("accountId") String accountId, YamlPayload yamlPayload,
      @QueryParam("deleteEnabled") @DefaultValue("false") boolean deleteEnabled,
      @PathParam("infraMappingId") String infraMappingId) {
    return yamlService.update(yamlPayload, accountId, infraMappingId);
  }

  @GET
  @Path("infrastructuredefinitions/{infraDefinitionId}")
  @Timed
  @ExceptionMetered
  public RestResponse<YamlPayload> getInfraDefintion(@QueryParam("appId") String appId,
      @QueryParam("accountId") String accountId, @PathParam("infraDefinitionId") String infraDefinitionId) {
    return yamlResourceService.getInfraDefinition(appId, infraDefinitionId);
  }

  @PUT
  @Path("infrastructuredefinitions/{infraDefinitionId}")
  @Timed
  @ExceptionMetered
  @AuthRule(permissionType = ENV, action = Action.UPDATE, skipAuth = true)
  public RestResponse<InfrastructureDefinition> updateInfraDefinition(@QueryParam("appId") String appId,
      @QueryParam("accountId") String accountId, YamlPayload yamlPayload,
      @QueryParam("deleteEnabled") @DefaultValue("false") boolean deleteEnabled,
      @PathParam("infraDefinitionId") String infraDefinitionId) {
    return yamlService.update(yamlPayload, accountId, infraDefinitionId);
  }

  @GET
  @Path("/container-tasks/{containerTaskId}")
  @Timed
  @ExceptionMetered
  public RestResponse<YamlPayload> getContainerTask(@QueryParam("appId") String appId,
      @QueryParam("accountId") String accountId, @PathParam("containerTaskId") String containerTaskId) {
    return yamlResourceService.getContainerTask(accountId, appId, containerTaskId);
  }

  @GET
  @Path("/ecs-service-spec/{ecsServiceSpecificationId}")
  @Timed
  @ExceptionMetered
  public RestResponse<YamlPayload> getEcsSErviceSpecification(@QueryParam("appId") String appId,
      @QueryParam("accountId") String accountId,
      @PathParam("ecsServiceSpecificationId") String ecsServiceSpecificationId) {
    return yamlResourceService.getEcsServiceSpecification(accountId, appId, ecsServiceSpecificationId);
  }

  @PUT
  @Path("/ecs-service-spec/{ecsServiceSpecificationId}")
  @Timed
  @ExceptionMetered
  @AuthRule(permissionType = SERVICE, action = Action.UPDATE, skipAuth = true)
  public RestResponse<Base> updateEcsServiceSpecification(@QueryParam("appId") String appId,
      @QueryParam("accountId") String accountId, YamlPayload yamlPayload,
      @QueryParam("deleteEnabled") @DefaultValue("false") boolean deleteEnabled,
      @PathParam("ecsServiceSpecificationId") String ecsServiceSpecificationId) {
    return yamlService.update(yamlPayload, accountId, ecsServiceSpecificationId);
  }

  @PUT
  @Path("/container-tasks/{containerTaskId}")
  @Timed
  @ExceptionMetered
  @AuthRule(permissionType = SERVICE, action = Action.UPDATE, skipAuth = true)
  public RestResponse<Base> updateContainerTask(@QueryParam("appId") String appId,
      @QueryParam("accountId") String accountId, YamlPayload yamlPayload,
      @QueryParam("deleteEnabled") @DefaultValue("false") boolean deleteEnabled,
      @PathParam("containerTaskId") String containerTaskId) {
    return yamlService.update(yamlPayload, accountId, containerTaskId);
  }

  @GET
  @Path("/pcfservicespecifications/{pcfservicespecificationId}")
  @Timed
  @ExceptionMetered
  public RestResponse<YamlPayload> getPcfservicespecificationId(@QueryParam("appId") String appId,
      @QueryParam("accountId") String accountId,
      @PathParam("pcfservicespecificationId") String pcfservicespecificationId) {
    return yamlResourceService.getPcfServiceSpecification(accountId, appId, pcfservicespecificationId);
  }

  @PUT
  @Path("/pcfservicespecifications/{pcfservicespecificationId}")
  @Timed
  @ExceptionMetered
  @AuthRule(permissionType = SERVICE, action = Action.UPDATE, skipAuth = true)
  public RestResponse<Base> updatePcfServiceSpecification(@QueryParam("appId") String appId,
      @QueryParam("accountId") String accountId, YamlPayload yamlPayload,
      @QueryParam("deleteEnabled") @DefaultValue("false") boolean deleteEnabled,
      @PathParam("pcfservicespecificationId") String pcfservicespecificationId) {
    return yamlService.update(yamlPayload, accountId, pcfservicespecificationId);
  }

  @GET
  @Path("/helm-charts/{helmChartSpecificationId}")
  @Timed
  @ExceptionMetered
  public RestResponse<YamlPayload> getHelmChartSpecification(@QueryParam("appId") String appId,
      @QueryParam("accountId") String accountId,
      @PathParam("helmChartSpecificationId") String helmChartSpecificationId) {
    return yamlResourceService.getHelmChartSpecification(accountId, appId, helmChartSpecificationId);
  }

  @PUT
  @Path("/helm-charts/{helmChartSpecificationId}")
  @Timed
  @ExceptionMetered
  @AuthRule(permissionType = SERVICE, action = Action.UPDATE, skipAuth = true)
  public RestResponse<Base> updateHelmChartSpecification(@QueryParam("appId") String appId,
      @QueryParam("accountId") String accountId, YamlPayload yamlPayload,
      @QueryParam("deleteEnabled") @DefaultValue("false") boolean deleteEnabled,
      @PathParam("helmChartSpecificationId") String helmChartSpecificationId) {
    return yamlService.update(yamlPayload, accountId, helmChartSpecificationId);
  }

  @GET
  @Path("/lambda-specs/{lambdaSpecId}")
  @Timed
  @ExceptionMetered
  public RestResponse<YamlPayload> getLamdbaSpec(@QueryParam("appId") String appId,
      @QueryParam("accountId") String accountId, @PathParam("lambdaSpecId") String lambdaSpecId) {
    return yamlResourceService.getLambdaSpec(accountId, appId, lambdaSpecId);
  }

  @PUT
  @Path("/user-data-specs/{userDataSpecId}")
  @Timed
  @ExceptionMetered
  @ApiKeyAuthorized(permissionType = SERVICE, action = Action.UPDATE)
  @AuthRule(permissionType = SERVICE, action = Action.UPDATE)
  public RestResponse<Base> updateUserDataSpec(@QueryParam("appId") String appId,
      @QueryParam("accountId") String accountId, YamlPayload yamlPayload,
      @QueryParam("deleteEnabled") @DefaultValue("false") boolean deleteEnabled,
      @PathParam("userDataSpecId") String userDataSpecId) {
    return yamlService.update(yamlPayload, accountId, userDataSpecId);
  }

  @GET
  @Path("/user-data-specs/{userDataSpecId}")
  @Timed
  @ExceptionMetered
  public RestResponse<YamlPayload> getUserDataSpec(@QueryParam("appId") String appId,
      @QueryParam("accountId") String accountId, @PathParam("userDataSpecId") String userDataSpecId) {
    return yamlResourceService.getUserDataSpec(accountId, appId, userDataSpecId);
  }

  @PUT
  @Path("/lambda-specs/{lambdaSpecId}")
  @Timed
  @ExceptionMetered
  @AuthRule(permissionType = SERVICE, action = Action.UPDATE, skipAuth = true)
  public RestResponse<Base> updateLambdaSpec(@QueryParam("appId") String appId,
      @QueryParam("accountId") String accountId, YamlPayload yamlPayload,
      @QueryParam("deleteEnabled") @DefaultValue("false") boolean deleteEnabled,
      @PathParam("lambdaSpecId") String lambdaSpecId) {
    return yamlService.update(yamlPayload, accountId, lambdaSpecId);
  }

  /**
   * Update yaml file
   *
   * @param accountId   account id
   * @param yamlPayload yaml payload with payload as string
   * @return rest response
   */
  @PUT
  @Timed
  @ExceptionMetered
  public RestResponse<Base> updateYaml(@QueryParam("accountId") String accountId, YamlPayload yamlPayload) {
    return yamlService.update(yamlPayload, accountId, null);
  }

  /**
   * Save git config rest response.
   *
   * @param accountId   the account id
   * @param yamlGitSync the yaml git sync
   * @return the rest response
   */
  @POST
  @Path("git-config")
  @Timed
  @ExceptionMetered
  @ApiKeyAuthorized(permissionType = MANAGE_CONFIG_AS_CODE)
  @AuthRule(permissionType = MANAGE_CONFIG_AS_CODE)
  public RestResponse<YamlGitConfig> saveGitConfig(
      @QueryParam("accountId") String accountId, YamlGitConfig yamlGitSync) {
    yamlGitSync.setAccountId(accountId);
    return new RestResponse<>(yamlGitService.save(yamlGitSync));
  }

  /**
   * Gets the yaml git sync info by uuid
   *
   * @param entityId  the entity id
   * @param accountId the account id
   * @return the rest response
   */
  @GET
  @Path("git-config/{entityId}")
  @Timed
  @ExceptionMetered
  public RestResponse<YamlGitConfig> get(@PathParam("entityId") String entityId,
      @QueryParam("accountId") String accountId, @QueryParam("entityType") EntityType entityType) {
    return new RestResponse<>(yamlGitService.get(accountId, entityId, entityType));
  }

  @DELETE
  @Path("git-config/{entityId}")
  @Timed
  @ExceptionMetered
  @ApiKeyAuthorized(permissionType = MANAGE_CONFIG_AS_CODE)
  @AuthRule(permissionType = MANAGE_CONFIG_AS_CODE)
  public RestResponse<YamlGitConfig> delete(@PathParam("entityId") String entityId,
      @QueryParam("accountId") String accountId, @QueryParam("entityType") EntityType entityType) {
    yamlGitService.delete(accountId, entityId, entityType);
    return new RestResponse();
  }

  /**
   * Update git config rest response.
   *
   * @param accountId   the account id
   * @param yamlGitSync the yaml git sync
   * @return the rest response
   */
  @PUT
  @Path("git-config/{entityId}")
  @Timed
  @ExceptionMetered
  @ApiKeyAuthorized(permissionType = MANAGE_CONFIG_AS_CODE)
  @AuthRule(permissionType = MANAGE_CONFIG_AS_CODE)
  public RestResponse<YamlGitConfig> updateGitConfig(
      @QueryParam("accountId") String accountId, YamlGitConfig yamlGitSync) {
    yamlGitSync.setAccountId(accountId);
    return new RestResponse<>(yamlGitService.update(yamlGitSync));
  }

  /**
   * Webhook catcher rest response.
   *
   * @param accountId          the account id
   * @param entityToken        the entity token
   * @param yamlWebHookPayload the yaml web hook payload
   * @return the rest response
   */
  @POST
  @Path("webhook/{entityToken}")
  @Consumes(APPLICATION_JSON)
  @Timed
  @ExceptionMetered
  @PublicApiWithWhitelist
  public RestResponse webhookCatcher(@QueryParam("accountId") String accountId,
      @PathParam("entityToken") String entityToken, String yamlWebHookPayload, @Context HttpHeaders httpHeaders) {
    notNullCheck("webhook token", entityToken);
    return new RestResponse<>(
        yamlGitService.validateAndQueueWebhookRequest(accountId, entityToken, yamlWebHookPayload, httpHeaders));
  }

  /**
   * Gets existing or new webhook
   *
   * @param entityId  the uuid of the entity
   * @param accountId the account id
   * @return the git sync webhook
   */
  @GET
  @Path("webhook/{entityId}")
  @Timed
  @ExceptionMetered
  public RestResponse<GitSyncWebhook> getWebhook(
      @PathParam("entityId") String entityId, @QueryParam("accountId") String accountId) {
    try {
      entityId = URLDecoder.decode(entityId, Charsets.UTF_8.name());
    } catch (UnsupportedEncodingException e) {
      log.error("", e);
    }
    return new RestResponse<>(yamlGitService.getWebhook(entityId, accountId));
  }

  /**
   * Run a full sync dry-run
   *
   * @param accountId the account id
   * @return
   */
  @GET
  @Path("full-sync-dry-run")
  @Timed
  @ExceptionMetered
  public RestResponse fullSyncDryRun(@QueryParam("accountId") String accountId, @QueryParam("token") String token,
      @QueryParam("queryAllAccounts") @DefaultValue("false") boolean queryAllAccounts) {
    validateUserAndToken(token);
    if (queryAllAccounts) {
      yamlGitService.performFullSyncDryRunOnAllAccounts();
    } else {
      yamlGitService.performFullSyncDryRun(accountId);
    }
    return new RestResponse();
  }

  private void validateUserAndToken(String token) {
    authService.validateToken(token);
    User existingUser = UserThreadLocal.get();
    if (existingUser == null) {
      throw new InvalidRequestException("Invalid User");
    }
    if (!harnessUserGroupService.isHarnessSupportUser(existingUser.getUuid())) {
      throw new InvalidRequestException("User needs to be a harness support user for this action");
    }
  }

  @GET
  @Path("get-all-yaml-errors")
  @Timed
  @ExceptionMetered
  public RestResponse<List<String>> getAllYamlErrors(@QueryParam("accountId") String accountId,
      @QueryParam("token") String token,
      @QueryParam("queryAllAccounts") @DefaultValue("false") boolean queryAllAccounts) {
    validateUserAndToken(token);
    List<String> errorLog = queryAllAccounts ? yamlGitService.getAllYamlErrorsForAllAccounts()
                                             : yamlGitService.getAllYamlErrorsForAccount(accountId);
    PageResponse pageResponse = aPageResponse().withResponse(errorLog).build();
    return RestResponse.Builder.aRestResponse().withResource(pageResponse.getResponse()).build();
  }

  @GET
  @Path("git-sync-errors")
  @Timed
  @ExceptionMetered
  public RestResponse<List<GitSyncError>> listGitSyncErrors(@QueryParam("accountId") String accountId) {
    return yamlGitService.listGitSyncErrors(accountId);
  }

  @POST
  @Path("git-sync-errors-discard-all")
  @Timed
  @ExceptionMetered
  @ApiKeyAuthorized(permissionType = MANAGE_CONFIG_AS_CODE)
  @AuthRule(permissionType = MANAGE_CONFIG_AS_CODE)
  public RestResponse discardGitSyncError(@QueryParam("accountId") String accountId) {
    return yamlGitService.discardAllGitSyncError(accountId);
  }

  @POST
  @Path("git-sync-errors-discard-selected")
  @Timed
  @ExceptionMetered
  @ApiKeyAuthorized(permissionType = MANAGE_CONFIG_AS_CODE)
  @AuthRule(permissionType = MANAGE_CONFIG_AS_CODE)
  public RestResponse discardGitSyncError(@QueryParam("accountId") String accountId, List<String> errorIds) {
    return yamlGitService.discardGitSyncErrorsForGivenIds(accountId, errorIds);
  }

  @POST
  @Path("yaml-as-zip")
  @Consumes(MULTIPART_FORM_DATA)
  @Timed
  @ExceptionMetered
  @ApiKeyAuthorized(permissionType = MANAGE_CONFIG_AS_CODE)
  @AuthRule(permissionType = MANAGE_CONFIG_AS_CODE)
  public RestResponse<String> processYamlFilesAsZip(@QueryParam("accountId") @NotEmpty String accountId,
      @QueryParam("yamlPath") @Optional String yamlPath, @FormDataParam("file") InputStream uploadedInputStream,
      @FormDataParam("file") FormDataContentDisposition fileDetail) throws IOException, YamlProcessingException {
    log.debug("accountId: {}, fileDetail: {}, yamlPath: {}", accountId, fileDetail, yamlPath);

    return yamlService.processYamlFilesAsZip(accountId,
        new BoundedInputStream(uploadedInputStream, configuration.getFileUploadLimits().getAppContainerLimit()),
        yamlPath);
  }

  @POST
  @Path("clone")
  @Timed
  @ExceptionMetered
  public RestResponse clone(@QueryParam("accountId") String accountId, @QueryParam("appId") String appId,
      @QueryParam("entityType") String entityType, @QueryParam("entityId") String entityId,
      @QueryParam("newEntityName") String newEntityName) {
    return yamlCloneService.cloneEntityUsingYaml(accountId, appId, false, entityType, entityId, newEntityName);
  }

  @GET
  @Path("/cvconfigurations/{cvConfigId}")
  @Timed
  @ExceptionMetered
  @ApiKeyAuthorized(permissionType = LOGGED_IN)
  @AuthRule(permissionType = LOGGED_IN)
  public RestResponse<YamlPayload> getCVConfiguration(
      @QueryParam("appId") String appId, @PathParam("cvConfigId") String cvConfigId) {
    return yamlResourceService.getCVConfiguration(appId, cvConfigId);
  }

  @PUT
  @Path("/cvconfigurations/{cvConfigId}")
  @Timed
  @ExceptionMetered
  @ApiKeyAuthorized(permissionType = LOGGED_IN)
  @AuthRule(permissionType = LOGGED_IN)
  public RestResponse<Base> updateCVConfiguration(@PathParam("cvConfigId") String cvConfigId,
      @QueryParam("appId") String appId, @QueryParam("accountId") String accountId, YamlPayload yamlPayload) {
    return yamlService.update(yamlPayload, accountId, cvConfigId);
  }

  @POST
  @Path("full-sync-account")
  @Timed
  @ExceptionMetered
  @ApiKeyAuthorized(permissionType = MANAGE_CONFIG_AS_CODE)
  @AuthRule(permissionType = MANAGE_CONFIG_AS_CODE)
  public RestResponse fullSyncAccount(@QueryParam("accountId") String accountId) {
    yamlGitService.asyncFullSyncForEntireAccount(accountId);
    return new RestResponse<>("Triggered async full git sync");
  }

  @GET
  @Path("/tags/{uuid}")
  @Timed
  @ExceptionMetered
  @ApiKeyAuthorized(permissionType = MANAGE_TAGS)
  @AuthRule(permissionType = MANAGE_TAGS)
  public RestResponse<YamlPayload> getTags(@QueryParam("accountId") String accountId) {
    return yamlResourceService.getHarnessTags(accountId);
  }

  @PUT
  @Path("/tags/{uuid}")
  @Timed
  @ExceptionMetered
  @ApiKeyAuthorized(permissionType = MANAGE_TAGS)
  @AuthRule(permissionType = MANAGE_TAGS)
  public RestResponse<ServiceCommand> updateTags(
      @QueryParam("accountId") String accountId, YamlPayload yamlPayload, @PathParam("uuid") String uuid) {
    return yamlService.update(yamlPayload, accountId, uuid);
  }

  @GET
  @Path("/internal/full-sync-account")
  @Timed
  @ExceptionMetered
  public RestResponse fullSyncAccountInternal(@QueryParam("accountId") String accountId) {
    if (!harnessUserGroupService.isHarnessSupportUser(UserThreadLocal.get().getUuid())) {
      throw new UnauthorizedException("You don't have the permissions to perform this action.", WingsException.USER);
    }

    yamlGitService.asyncFullSyncForEntireAccount(accountId);
    return new RestResponse<>("Triggered async full git sync");
  }

  @POST
  @Path("/internal/template-yaml-sync")
  @Timed
  @ExceptionMetered
  public RestResponse templateYamlSync(@QueryParam("accountId") String accountId) {
    if (!harnessUserGroupService.isHarnessSupportUser(UserThreadLocal.get().getUuid())) {
      throw new UnauthorizedException("You don't have the permissions to perform this action.", WingsException.USER);
    }
    yamlService.syncYamlTemplate(accountId);
    return new RestResponse<>("Triggered async template git sync");
  }

  @GET
  @Path("/yaml-content")
  @Timed
  @ExceptionMetered
  @ApiKeyAuthorized(permissionType = LOGGED_IN)
  public RestResponse<YamlPayload> getYamlForFilePath(@QueryParam("accountId") String accountId,
      @QueryParam("yamlFilePath") String yamlFilePath, @QueryParam("yamlSubType") String yamlSubType,
      @QueryParam("applicationId") String applicationId) {
    BaseYaml yamlForFilePath = yamlService.getYamlForFilePath(accountId, yamlFilePath, yamlSubType, applicationId);
    if (yamlForFilePath != null) {
      return YamlHelper.getYamlRestResponse(
          yamlService.getYamlForFilePath(accountId, yamlFilePath, yamlSubType, applicationId), "");
    } else {
      YamlPayload yamlPayload = new YamlPayload();
      yamlPayload.setYamlPayload("");
      return new RestResponse<>(yamlPayload);
    }
  }

  @POST
  @Path("upsert-entities")
  @Consumes(MULTIPART_FORM_DATA)
  @Timed
  @ExceptionMetered
  public RestResponse<YamlOperationResponse> upsertYAMLEntities(@QueryParam("accountId") @NotEmpty String accountId,
      @FormDataParam("file") InputStream uploadedInputStream) throws IOException {
    return new RestResponse<>(yamlService.upsertYAMLFilesAsZip(accountId,
        new BoundedInputStream(uploadedInputStream, configuration.getFileUploadLimits().getAppContainerLimit())));
  }

  @POST
  @Path("upsert-entity")
  @Consumes(MULTIPART_FORM_DATA)
  @Timed
  @ExceptionMetered
  public RestResponse<FileOperationStatus> upsertYAMLEntity(@QueryParam("accountId") @NotEmpty String accountId,
      @QueryParam("yamlFilePath") @NotEmpty String yamlFilePath, @FormDataParam("yamlContent") String yamlContent) {
    return new RestResponse<>(yamlService.upsertYAMLFile(accountId, yamlFilePath, yamlContent));
  }

  @DELETE
  @Path("delete-entities")
  @Timed
  @ExceptionMetered
  public RestResponse<YamlOperationResponse> deleteYAMLEntities(
      @QueryParam("accountId") @NotEmpty String accountId, @QueryParam("filePaths") @NotEmpty List<String> filePaths) {
    return new RestResponse<>(yamlService.deleteYAMLByPaths(accountId, filePaths));
  }

  @DELETE
  @Path("delete-entities-v2")
  @Timed
  @ExceptionMetered
  public RestResponse<YamlOperationResponse> deleteYAMLEntitiesV2(
      @QueryParam("accountId") @NotEmpty String accountId, @NotEmpty List<EntityInformation> entityInformations) {
    return new RestResponse<>(yamlService.deleteYAMLByPathsV2(accountId, entityInformations));
  }

  /**
   * Gets the yaml version of a Governance Config by accountId
   *
   * @param accountId the accountId
   * @return the rest response
   */
  @GET
  @Path("/compliance-config/{accountId}")
  @Timed
  @ExceptionMetered
  public RestResponse<YamlPayload> getGovernanceConfig(@QueryParam("accountId") String accountId) {
    return yamlResourceService.getGovernanceConfig(accountId);
  }

  /**
   * Update the Governance Config that is sent as Yaml (in a JSON "wrapper")
   *
   * @param accountId   the account id
   * @param yamlPayload the yaml version of the Freeze Config
   * @param governanceConfigId governanceConfigId
   * @return the rest response
   */
  @PUT
  @Path("/compliance-config/{governanceConfigId}")
  @Timed
  @ExceptionMetered
  @ApiKeyAuthorized(permissionType = MANAGE_DEPLOYMENT_FREEZES)
  @AuthRule(permissionType = MANAGE_DEPLOYMENT_FREEZES)
  public RestResponse<YamlPayload> updateGovernanceConfig(@QueryParam("accountId") String accountId,
      YamlPayload yamlPayload, @PathParam("governanceConfigId") String governanceConfigId) {
    return yamlService.update(yamlPayload, accountId, governanceConfigId);
  }
}
