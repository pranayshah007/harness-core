/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.resources;

import static io.harness.beans.SearchFilter.Operator.EQ;
import static io.harness.beans.SearchFilter.Operator.IN;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.exception.WingsException.USER;

import static software.wings.security.PermissionAttribute.PermissionType.LOGGED_IN;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.PageRequest;
import io.harness.beans.PageResponse;
import io.harness.exception.InvalidRequestException;
import io.harness.k8s.model.HelmVersion;
import io.harness.rest.RestResponse;

import software.wings.beans.CommandCategory;
import software.wings.beans.LambdaSpecification;
import software.wings.beans.Service;
import software.wings.beans.Setup.SetupStatus;
import software.wings.beans.appmanifest.AppManifestKind;
import software.wings.beans.appmanifest.ApplicationManifest;
import software.wings.beans.appmanifest.HelmChart;
import software.wings.beans.appmanifest.ManifestFile;
import software.wings.beans.appmanifest.StoreType;
import software.wings.beans.artifact.ArtifactStream;
import software.wings.beans.artifact.ArtifactStreamBinding;
import software.wings.beans.command.ServiceCommand;
import software.wings.beans.container.ContainerTask;
import software.wings.beans.container.EcsServiceSpecification;
import software.wings.beans.container.HelmChartSpecification;
import software.wings.beans.container.KubernetesPayload;
import software.wings.beans.container.PcfServiceSpecification;
import software.wings.beans.container.UserDataSpecification;
import software.wings.beans.helm.HelmCommandFlagConstants.HelmSubCommand;
import software.wings.security.PermissionAttribute.Action;
import software.wings.security.PermissionAttribute.PermissionType;
import software.wings.security.PermissionAttribute.ResourceType;
import software.wings.security.annotations.ApiKeyAuthorized;
import software.wings.security.annotations.AuthRule;
import software.wings.security.annotations.ListAPI;
import software.wings.security.annotations.Scope;
import software.wings.service.intfc.ApplicationManifestService;
import software.wings.service.intfc.ArtifactStreamServiceBindingService;
import software.wings.service.intfc.ServiceResourceService;
import software.wings.service.intfc.applicationmanifest.HelmChartService;
import software.wings.stencils.Stencil;

import com.codahale.metrics.annotation.ExceptionMetered;
import com.codahale.metrics.annotation.Timed;
import com.google.inject.Inject;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiParam;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.validation.constraints.NotNull;
import javax.ws.rs.BeanParam;
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
import org.hibernate.validator.constraints.NotEmpty;

/**
 * Created by anubhaw on 3/25/16.
 */
@Api("services")
@Path("services")
@Consumes(APPLICATION_JSON)
@Produces(APPLICATION_JSON)
@Scope(ResourceType.APPLICATION)
@AuthRule(permissionType = PermissionType.SERVICE)
@OwnedBy(HarnessTeam.CDC)
public class ServiceResource {
  private ServiceResourceService serviceResourceService;
  @Inject ApplicationManifestService applicationManifestService;
  @Inject ArtifactStreamServiceBindingService artifactStreamServiceBindingService;
  @Inject HelmChartService helmChartService;

  /**
   * Instantiates a new service resource.
   *
   * @param serviceResourceService the service resource service
   */
  @Inject
  public ServiceResource(ServiceResourceService serviceResourceService) {
    this.serviceResourceService = serviceResourceService;
  }

  /**
   * List.
   *
   * @param appIds       the app ids
   * @param pageRequest the page request
   * @return the rest response
   */
  @GET
  @Timed
  @ExceptionMetered
  @ListAPI(ResourceType.SERVICE)
  @ApiKeyAuthorized(permissionType = LOGGED_IN)
  public RestResponse<PageResponse<Service>> list(@QueryParam("accountId") String accountId,
      @QueryParam("appId") List<String> appIds, @QueryParam("tagFilter") String tagFilter,
      @QueryParam("withTags") @DefaultValue("false") boolean withTags, @BeanParam PageRequest<Service> pageRequest,
      @QueryParam("details") @DefaultValue("true") boolean details) {
    if (isNotEmpty(appIds)) {
      pageRequest.addFilter("appId", IN, appIds.toArray());
    }
    if (!details) {
      return new RestResponse<>(serviceResourceService.list(pageRequest, false, false, withTags, tagFilter));
    }
    return new RestResponse<>(serviceResourceService.list(pageRequest, true, true, withTags, tagFilter));
  }

  /**
   * Gets the.
   *
   * @param appId     the app id
   * @param serviceId the service id
   * @param status    the status
   * @return the rest response
   */
  @GET
  @Path("{serviceId}")
  @Timed
  @ExceptionMetered
  @ApiKeyAuthorized(permissionType = PermissionType.SERVICE, action = Action.READ)
  @AuthRule(permissionType = PermissionType.SERVICE, action = Action.READ)
  public RestResponse<Service> get(@QueryParam("appId") String appId, @PathParam("serviceId") String serviceId,
      @QueryParam("status") SetupStatus status) {
    if (status == null) {
      status = SetupStatus.COMPLETE;
    }
    return new RestResponse<>(serviceResourceService.getWithHelmValues(appId, serviceId, status));
  }

  /**
   * Save.
   *
   * @param appId   the app id
   * @param service the service
   * @return the rest response
   */
  @POST
  @Timed
  @ExceptionMetered
  public RestResponse<Service> save(@QueryParam("appId") String appId, Service service) {
    service.setAppId(appId);
    return new RestResponse<>(serviceResourceService.save(service));
  }

  /**
   * Update.
   *
   * @param appId     the app id
   * @param serviceId the service id
   * @param service   the service
   * @return the rest response
   */
  @PUT
  @Path("{serviceId}")
  @Timed
  @ExceptionMetered
  public RestResponse<Service> update(
      @QueryParam("appId") String appId, @PathParam("serviceId") String serviceId, Service service) {
    service.setUuid(serviceId);
    service.setAppId(appId);
    return new RestResponse<>(serviceResourceService.updateWithHelmValues(service));
  }

  @POST
  @Path("{serviceId}/clone")
  @Timed
  @ExceptionMetered
  public RestResponse<Service> clone(
      @QueryParam("appId") String appId, @PathParam("serviceId") String serviceId, Service service) {
    service.setAppId(appId);
    return new RestResponse<>(serviceResourceService.clone(appId, serviceId, service));
  }

  /**
   * Delete.
   *
   * @param appId     the app id
   * @param serviceId the service id
   * @return the rest response
   */
  @DELETE
  @Path("{serviceId}")
  @Timed
  @ExceptionMetered
  public RestResponse delete(@QueryParam("appId") String appId, @PathParam("serviceId") String serviceId) {
    serviceResourceService.delete(appId, serviceId);
    return new RestResponse();
  }

  /**
   * Save command.
   *
   * @param appId     the app id
   * @param serviceId the service id
   * @param command   the command
   * @return the rest response
   */
  @POST
  @Path("{serviceId}/commands")
  @Timed
  @ExceptionMetered
  @AuthRule(permissionType = PermissionType.SERVICE, action = Action.UPDATE)
  public RestResponse<Service> saveCommand(@ApiParam(name = "appId", required = true) @QueryParam("appId") String appId,
      @ApiParam(name = "serviceId", required = true) @PathParam("serviceId") String serviceId,
      @ApiParam(name = "command", required = true) ServiceCommand command) {
    return new RestResponse<>(serviceResourceService.addCommand(appId, serviceId, command, true));
  }

  /**
   * Save command.
   *
   * @param appId     the app id
   * @param serviceId the service id
   * @return the rest response
   */
  @PUT
  @Path("{serviceId}/commands")
  @Timed
  @ExceptionMetered
  public RestResponse<Service> updateCommands(
      @ApiParam(name = "appId", required = true) @QueryParam("appId") String appId,
      @ApiParam(name = "serviceId", required = true) @PathParam("serviceId") String serviceId,
      @ApiParam(name = "command", required = true) List<ServiceCommand> commands) {
    return new RestResponse<>(serviceResourceService.updateCommandsOrder(appId, serviceId, commands));
  }

  @GET
  @Path("{serviceId}/commands/{commandName}")
  @Timed
  @ExceptionMetered
  @ApiKeyAuthorized(permissionType = LOGGED_IN)
  public RestResponse<ServiceCommand> getCommand(@QueryParam("appId") String appId,
      @PathParam("serviceId") String serviceId, @PathParam("commandName") String commandName,
      @QueryParam("version") int version) {
    return new RestResponse<>(
        serviceResourceService.getCommandByNameAndVersion(appId, serviceId, commandName, version));
  }

  /**
   * Update command.
   *
   * @param appId     the app id
   * @param serviceId the service id
   * @param command   serviceCommand
   * @return the rest response
   */
  @PUT
  @Path("{serviceId}/commands/{commandName}")
  @Timed
  @ExceptionMetered
  public RestResponse<Service> updateCommand(
      @QueryParam("appId") String appId, @PathParam("serviceId") String serviceId, ServiceCommand command) {
    return new RestResponse<>(serviceResourceService.updateCommand(appId, serviceId, command));
  }

  @POST
  @Path("{serviceId}/commands/{commandName}/clone")
  @Timed
  @ExceptionMetered
  @AuthRule(permissionType = PermissionType.SERVICE, action = Action.UPDATE)
  public RestResponse<Service> cloneCommand(@QueryParam("appId") String appId, @PathParam("serviceId") String serviceId,
      @PathParam("commandName") String commandName, ServiceCommand command) {
    return new RestResponse<>(serviceResourceService.cloneCommand(appId, serviceId, commandName, command));
  }

  /**
   * Delete command.
   *
   * @param appId            the app id
   * @param serviceId        the service id
   * @param serviceCommandId the command name
   * @return the rest response
   */
  @DELETE
  @Path("{serviceId}/commands/{serviceCommandId}")
  @Timed
  @ExceptionMetered
  @AuthRule(permissionType = PermissionType.SERVICE, action = Action.UPDATE)
  public RestResponse<Service> deleteCommand(@QueryParam("appId") String appId,
      @PathParam("serviceId") String serviceId, @PathParam("serviceCommandId") String serviceCommandId) {
    return new RestResponse<>(serviceResourceService.deleteCommand(appId, serviceId, serviceCommandId));
  }

  /**
   * Stencils rest response.
   *
   * @param appId       the app id
   * @param serviceId   the service id
   * @param commandName the command name
   * @return the rest response
   */
  @GET
  @Path("{serviceId}/commands/stencils")
  @Timed
  @ExceptionMetered
  @AuthRule(permissionType = LOGGED_IN)
  @ApiKeyAuthorized(permissionType = LOGGED_IN)
  public RestResponse<List<Stencil>> stencils(@QueryParam("appId") String appId,
      @PathParam("serviceId") String serviceId, @QueryParam("filterCommand") String commandName) {
    return new RestResponse<>(serviceResourceService.getCommandStencils(appId, serviceId, commandName, true));
  }

  @POST
  @Path("{serviceId}/containers/tasks")
  @Timed
  @ExceptionMetered
  @AuthRule(permissionType = PermissionType.SERVICE, action = Action.UPDATE)
  public RestResponse<ContainerTask> createContainerTask(@QueryParam("appId") String appId,
      @QueryParam("advanced") boolean advanced, @PathParam("serviceId") String serviceId, ContainerTask containerTask) {
    containerTask.setAppId(appId);
    containerTask.setServiceId(serviceId);
    return new RestResponse<>(serviceResourceService.createContainerTask(containerTask, advanced));
  }

  @GET
  @Path("{serviceId}/containers/tasks")
  @Timed
  @ExceptionMetered
  @ApiKeyAuthorized(permissionType = LOGGED_IN)
  public RestResponse<PageResponse<ContainerTask>> listContainerTask(@QueryParam("appId") String appId,
      @PathParam("serviceId") String serviceId, @BeanParam PageRequest<ContainerTask> pageRequest) {
    pageRequest.addFilter("appId", EQ, appId);
    pageRequest.addFilter("serviceId", EQ, serviceId);
    return new RestResponse<>(serviceResourceService.listContainerTasks(pageRequest));
  }

  @PUT
  @Path("{serviceId}/containers/tasks/{taskId}")
  @Timed
  @ExceptionMetered
  public RestResponse<ContainerTask> updateContainerTask(@QueryParam("appId") String appId,
      @QueryParam("advanced") boolean advanced, @PathParam("serviceId") String serviceId,
      @PathParam("taskId") String taskId, ContainerTask containerTask) {
    containerTask.setAppId(appId);
    containerTask.setServiceId(serviceId);
    containerTask.setUuid(taskId);
    return new RestResponse<>(serviceResourceService.updateContainerTask(containerTask, advanced));
  }

  @PUT
  @Path("{serviceId}/containers/tasks/{taskId}/advanced")
  @Timed
  @ExceptionMetered
  public RestResponse<ContainerTask> updateContainerTaskAdvanced(@QueryParam("appId") String appId,
      @QueryParam("reset") boolean reset, @PathParam("serviceId") String serviceId, @PathParam("taskId") String taskId,
      KubernetesPayload kubernetesPayload) {
    return new RestResponse<>(
        serviceResourceService.updateContainerTaskAdvanced(appId, serviceId, taskId, kubernetesPayload, reset));
  }

  @GET
  @Path("{serviceId}/containers/tasks/stencils")
  @Timed
  @ExceptionMetered
  @AuthRule(permissionType = LOGGED_IN)
  @ApiKeyAuthorized(permissionType = LOGGED_IN)
  public RestResponse<List<Stencil>> listTaskStencils(
      @QueryParam("appId") String appId, @PathParam("serviceId") String serviceId) {
    return new RestResponse<>(serviceResourceService.getContainerTaskStencils(appId, serviceId));
  }

  @POST
  @Path("{serviceId}/containers/charts")
  @Timed
  @ExceptionMetered
  @AuthRule(permissionType = PermissionType.SERVICE, action = Action.UPDATE)
  public RestResponse<HelmChartSpecification> createHelmChartSpecification(@QueryParam("appId") String appId,
      @PathParam("serviceId") String serviceId, HelmChartSpecification helmChartSpecification) {
    helmChartSpecification.setAppId(appId);
    helmChartSpecification.setServiceId(serviceId);
    return new RestResponse<>(serviceResourceService.createHelmChartSpecification(helmChartSpecification));
  }

  @GET
  @Path("{serviceId}/containers/charts")
  @Timed
  @ExceptionMetered
  @ApiKeyAuthorized(permissionType = LOGGED_IN)
  public RestResponse<PageResponse<HelmChartSpecification>> listHelmChartSpecification(
      @QueryParam("appId") String appId, @PathParam("serviceId") String serviceId,
      @BeanParam PageRequest<HelmChartSpecification> pageRequest) {
    pageRequest.addFilter("appId", EQ, appId);
    pageRequest.addFilter("serviceId", EQ, serviceId);
    return new RestResponse<>(serviceResourceService.listHelmChartSpecifications(pageRequest));
  }

  @PUT
  @Path("{serviceId}/containers/charts/{taskId}")
  @Timed
  @ExceptionMetered
  public RestResponse<HelmChartSpecification> updateHelmChartSpecification(@QueryParam("appId") String appId,
      @PathParam("serviceId") String serviceId, @PathParam("taskId") String taskId,
      HelmChartSpecification helmChartSpecification) {
    helmChartSpecification.setAppId(appId);
    helmChartSpecification.setServiceId(serviceId);
    helmChartSpecification.setUuid(taskId);
    return new RestResponse<>(serviceResourceService.updateHelmChartSpecification(helmChartSpecification));
  }

  @POST
  @Path("{serviceId}/pcfspecification")
  @Timed
  @ExceptionMetered
  @AuthRule(permissionType = PermissionType.SERVICE, action = Action.CREATE)
  public RestResponse<PcfServiceSpecification> createPcfServiceSpecification(@QueryParam("appId") String appId,
      @PathParam("serviceId") String serviceId, PcfServiceSpecification pcfServiceSpecification) {
    pcfServiceSpecification.setAppId(appId);
    pcfServiceSpecification.setServiceId(serviceId);
    return new RestResponse<>(serviceResourceService.createPcfServiceSpecification(pcfServiceSpecification));
  }

  @GET
  @Path("{serviceId}/pcfspecification")
  @Timed
  @ExceptionMetered
  @ApiKeyAuthorized(permissionType = LOGGED_IN)
  public RestResponse<PcfServiceSpecification> getPcfServiceSpecification(@QueryParam("appId") String appId,
      @PathParam("serviceId") String serviceId, @BeanParam PageRequest<PcfServiceSpecification> pageRequest) {
    return new RestResponse<>(serviceResourceService.getExistingOrDefaultPcfServiceSpecification(appId, serviceId));
  }

  @PUT
  @Path("{serviceId}/pcfspecification/{pcfSpecificationId}")
  @Timed
  @ExceptionMetered
  @AuthRule(permissionType = PermissionType.SERVICE, action = Action.UPDATE)
  public RestResponse<PcfServiceSpecification> updatePcfServiceSpecification(@QueryParam("appId") String appId,
      @PathParam("serviceId") String serviceId, @PathParam("pcfSpecificationId") String pcfSpecificationId,
      PcfServiceSpecification pcfServiceSpecification) {
    pcfServiceSpecification.setAppId(appId);
    pcfServiceSpecification.setServiceId(serviceId);
    pcfServiceSpecification.setUuid(pcfSpecificationId);
    return new RestResponse<>(serviceResourceService.updatePcfServiceSpecification(pcfServiceSpecification));
  }

  @PUT
  @Path("{serviceId}/pcfspecification/reset")
  @Timed
  @ExceptionMetered
  @AuthRule(permissionType = PermissionType.SERVICE, action = Action.UPDATE)
  public RestResponse<PcfServiceSpecification> resetToDefaultPcfServiceSpecification(@QueryParam("appId") String appId,
      @PathParam("serviceId") String serviceId, PcfServiceSpecification pcfServiceSpecification) {
    pcfServiceSpecification.setAppId(appId);
    pcfServiceSpecification.setServiceId(serviceId);
    return new RestResponse<>(serviceResourceService.resetToDefaultPcfServiceSpecification(pcfServiceSpecification));
  }

  @POST
  @Path("{serviceId}/ecsSpecification")
  @Timed
  @ExceptionMetered
  @AuthRule(permissionType = PermissionType.SERVICE, action = Action.CREATE)
  public RestResponse<EcsServiceSpecification> createEcsServiceSpecification(@QueryParam("appId") String appId,
      @PathParam("serviceId") String serviceId, EcsServiceSpecification ecsServiceSpecification) {
    ecsServiceSpecification.setAppId(appId);
    ecsServiceSpecification.setServiceId(serviceId);
    return new RestResponse<>(serviceResourceService.createEcsServiceSpecification(ecsServiceSpecification));
  }

  @GET
  @Path("{serviceId}/ecsSpecification")
  @Timed
  @ExceptionMetered
  @ApiKeyAuthorized(permissionType = LOGGED_IN)
  public RestResponse<EcsServiceSpecification> getEcsServiceSpecification(@QueryParam("appId") String appId,
      @PathParam("serviceId") String serviceId, @BeanParam PageRequest<EcsServiceSpecification> pageRequest) {
    return new RestResponse<>(serviceResourceService.getExistingOrDefaultEcsServiceSpecification(appId, serviceId));
  }

  @PUT
  @Path("{serviceId}/ecsSpecification/{ecsSpecificationId}")
  @Timed
  @ExceptionMetered
  @AuthRule(permissionType = PermissionType.SERVICE, action = Action.UPDATE)
  public RestResponse<EcsServiceSpecification> updateEcsServiceSpecification(@QueryParam("appId") String appId,
      @PathParam("serviceId") String serviceId, @PathParam("ecsSpecificationId") String ecsSpecificationId,
      EcsServiceSpecification ecsServiceSpecification) {
    ecsServiceSpecification.setAppId(appId);
    ecsServiceSpecification.setServiceId(serviceId);
    return new RestResponse<>(serviceResourceService.updateEcsServiceSpecification(ecsServiceSpecification));
  }

  @PUT
  @Path("{serviceId}/ecsSpecification/reset")
  @Timed
  @ExceptionMetered
  @AuthRule(permissionType = PermissionType.SERVICE, action = Action.UPDATE)
  public RestResponse<EcsServiceSpecification> resetToDefaultEcsServiceSpecification(
      @QueryParam("appId") String appId, @PathParam("serviceId") String serviceId) {
    return new RestResponse<>(serviceResourceService.resetToDefaultEcsServiceSpecification(appId, serviceId));
  }

  @POST
  @Path("{serviceId}/lambda-specifications")
  @Timed
  @ExceptionMetered
  @AuthRule(permissionType = PermissionType.SERVICE, action = Action.UPDATE)
  public RestResponse<LambdaSpecification> createLambdaSpecification(@QueryParam("appId") String appId,
      @PathParam("serviceId") String serviceId, LambdaSpecification lambdaSpecification) {
    lambdaSpecification.setAppId(appId);
    lambdaSpecification.setServiceId(serviceId);
    return new RestResponse<>(serviceResourceService.createLambdaSpecification(lambdaSpecification));
  }

  @PUT
  @Path("{serviceId}/lambda-specifications/{lambdaSpecificationId}")
  @Timed
  @ExceptionMetered
  public RestResponse<LambdaSpecification> updateLambdaSpecification(@QueryParam("appId") String appId,
      @PathParam("serviceId") String serviceId, @PathParam("lambdaSpecificationId") String lambdaSpecificationId,
      LambdaSpecification lambdaSpecification) {
    lambdaSpecification.setAppId(appId);
    lambdaSpecification.setServiceId(serviceId);
    lambdaSpecification.setUuid(lambdaSpecificationId);
    return new RestResponse<>(serviceResourceService.updateLambdaSpecification(lambdaSpecification));
  }

  @GET
  @Path("{serviceId}/lambda-specifications")
  @Timed
  @ExceptionMetered
  @ApiKeyAuthorized(permissionType = LOGGED_IN)
  public RestResponse<PageResponse<LambdaSpecification>> listLambdaSpecification(@QueryParam("appId") String appId,
      @PathParam("serviceId") String serviceId, @BeanParam PageRequest<LambdaSpecification> pageRequest) {
    pageRequest.addFilter("appId", EQ, appId);
    pageRequest.addFilter("serviceId", EQ, serviceId);
    return new RestResponse<>(serviceResourceService.listLambdaSpecification(pageRequest));
  }

  @POST
  @Path("{serviceId}/user-data-specifications")
  @Timed
  @ExceptionMetered
  @AuthRule(permissionType = PermissionType.SERVICE, action = Action.UPDATE)
  public RestResponse<UserDataSpecification> createUserDataSpecification(@QueryParam("appId") String appId,
      @PathParam("serviceId") String serviceId, @NotNull UserDataSpecification userDataSpecification) {
    userDataSpecification.setAppId(appId);
    userDataSpecification.setServiceId(serviceId);
    return new RestResponse<>(serviceResourceService.createUserDataSpecification(userDataSpecification));
  }

  @PUT
  @Path("{serviceId}/user-data-specifications/{userDataSpecificationId}")
  @Timed
  @ExceptionMetered
  public RestResponse<UserDataSpecification> updateUserDataSpecification(@QueryParam("appId") String appId,
      @PathParam("serviceId") String serviceId, @PathParam("userDataSpecificationId") String userDataSpecificationId,
      @NotNull UserDataSpecification userDataSpecification) {
    userDataSpecification.setAppId(appId);
    userDataSpecification.setServiceId(serviceId);
    userDataSpecification.setUuid(userDataSpecificationId);
    return new RestResponse<>(serviceResourceService.updateUserDataSpecification(userDataSpecification));
  }

  @GET
  @Path("{serviceId}/user-data-specifications")
  @Timed
  @ExceptionMetered
  @ApiKeyAuthorized(permissionType = LOGGED_IN)
  public RestResponse<PageResponse<UserDataSpecification>> listUserDataSpecification(@QueryParam("appId") String appId,
      @PathParam("serviceId") String serviceId, @BeanParam PageRequest<UserDataSpecification> pageRequest) {
    pageRequest.addFilter("appId", EQ, appId);
    pageRequest.addFilter("serviceId", EQ, serviceId);
    return new RestResponse<>(serviceResourceService.listUserDataSpecification(pageRequest));
  }

  @POST
  @Path("{serviceId}/config-map-yaml")
  @Timed
  @ExceptionMetered
  @AuthRule(permissionType = PermissionType.SERVICE, action = Action.UPDATE)
  public RestResponse<Service> setConfigMapYaml(
      @ApiParam(name = "appId", required = true) @QueryParam("appId") String appId,
      @ApiParam(name = "serviceId", required = true) @PathParam("serviceId") String serviceId,
      KubernetesPayload kubernetesPayload) {
    return new RestResponse<>(serviceResourceService.setConfigMapYaml(appId, serviceId, kubernetesPayload));
  }

  @PUT
  @Path("{serviceId}/config-map-yaml")
  @Timed
  @ExceptionMetered
  public RestResponse<Service> updateConfigMapYaml(
      @ApiParam(name = "appId", required = true) @QueryParam("appId") String appId,
      @ApiParam(name = "serviceId", required = true) @PathParam("serviceId") String serviceId,
      KubernetesPayload kubernetesPayload) {
    return new RestResponse<>(serviceResourceService.setConfigMapYaml(appId, serviceId, kubernetesPayload));
  }

  @DELETE
  @Path("{serviceId}/config-map-yaml")
  @Timed
  @ExceptionMetered
  public RestResponse<Service> deleteConfigMapYaml(
      @ApiParam(name = "appId", required = true) @QueryParam("appId") String appId,
      @ApiParam(name = "serviceId", required = true) @PathParam("serviceId") String serviceId) {
    return new RestResponse<>(serviceResourceService.setConfigMapYaml(appId, serviceId, new KubernetesPayload()));
  }

  @POST
  @Path("{serviceId}/helm-value-yaml")
  @Timed
  @ExceptionMetered
  @AuthRule(permissionType = PermissionType.SERVICE, action = Action.UPDATE)
  public RestResponse<Service> setHelmValueYaml(
      @ApiParam(name = "appId", required = true) @QueryParam("appId") String appId,
      @ApiParam(name = "serviceId", required = true) @PathParam("serviceId") String serviceId,
      KubernetesPayload kubernetesPayload) {
    return new RestResponse<>(serviceResourceService.setHelmValueYaml(appId, serviceId, kubernetesPayload));
  }

  @PUT
  @Path("{serviceId}/helm-value-yaml")
  @Timed
  @ExceptionMetered
  public RestResponse<Service> updateHelmValueYaml(
      @ApiParam(name = "appId", required = true) @QueryParam("appId") String appId,
      @ApiParam(name = "serviceId", required = true) @PathParam("serviceId") String serviceId,
      KubernetesPayload kubernetesPayload) {
    return new RestResponse<>(serviceResourceService.setHelmValueYaml(appId, serviceId, kubernetesPayload));
  }

  @DELETE
  @Path("{serviceId}/helm-value-yaml")
  @Timed
  @ExceptionMetered
  public RestResponse<Service> deleteHelmValueYaml(
      @ApiParam(name = "appId", required = true) @QueryParam("appId") String appId,
      @ApiParam(name = "serviceId", required = true) @PathParam("serviceId") String serviceId) {
    return new RestResponse<>(serviceResourceService.deleteHelmValueYaml(appId, serviceId));
  }

  /**
   * Stencils rest response.
   *
   * @param appId       the app id
   * @param serviceId   the service id
   * @param commandName the command name
   * @return the rest response
   */
  @GET
  @Path("{serviceId}/commands/categories")
  @Timed
  @ExceptionMetered
  @AuthRule(permissionType = LOGGED_IN)
  @ApiKeyAuthorized(permissionType = LOGGED_IN)
  public RestResponse<List<CommandCategory>> getCommandUnitItems(@QueryParam("appId") String appId,
      @PathParam("serviceId") String serviceId, @QueryParam("filterCommand") String commandName) {
    return new RestResponse<>(serviceResourceService.getCommandCategories(appId, serviceId, commandName));
  }

  @GET
  @Path("{serviceId}/app-manifest")
  @Timed
  @ExceptionMetered
  @ApiKeyAuthorized(permissionType = LOGGED_IN)
  public RestResponse<ApplicationManifest> getAppManifest(
      @QueryParam("appId") String appId, @PathParam("serviceId") String serviceId) {
    return new RestResponse<>(applicationManifestService.getManifestByServiceId(appId, serviceId));
  }

  @POST
  @Path("{serviceId}/k8s-values")
  @Timed
  @ExceptionMetered
  public RestResponse<ManifestFile> createManifestFile(@QueryParam("appId") String appId,
      @PathParam("serviceId") String serviceId, @NotNull @QueryParam("appManifestKind") AppManifestKind appManifestKind,
      ManifestFile manifestFile) {
    return new RestResponse<>(
        serviceResourceService.createManifestFile(appId, serviceId, manifestFile, appManifestKind));
  }

  @GET
  @Path("{serviceId}/k8s-values/{manifestFileId}")
  @Timed
  @ExceptionMetered
  @ApiKeyAuthorized(permissionType = LOGGED_IN)
  public RestResponse<ManifestFile> getManifestFile(@QueryParam("appId") String appId,
      @PathParam("serviceId") String serviceId, @PathParam("manifestFileId") String manifestFileId) {
    return new RestResponse<>(serviceResourceService.getManifestFile(appId, serviceId, manifestFileId));
  }

  @PUT
  @Path("{serviceId}/k8s-values/{manifestFileId}")
  @Timed
  @ExceptionMetered
  public RestResponse<ManifestFile> updateManifestFile(@QueryParam("appId") String appId,
      @PathParam("serviceId") String serviceId, @PathParam("manifestFileId") String manifestFileId,
      @NotNull @QueryParam("appManifestKind") AppManifestKind appManifestKind, ManifestFile manifestFile) {
    return new RestResponse<>(
        serviceResourceService.updateManifestFile(appId, serviceId, manifestFileId, manifestFile, appManifestKind));
  }

  @DELETE
  @Path("{serviceId}/k8s-values/{manifestFileId}")
  @Timed
  @ExceptionMetered
  public RestResponse deleteManifestFIle(@QueryParam("appId") String appId, @PathParam("serviceId") String serviceId,
      @PathParam("manifestFileId") String manifestFileId) {
    serviceResourceService.deleteManifestFile(appId, serviceId, manifestFileId);
    return new RestResponse();
  }

  @POST
  @Path("{serviceId}/k8s-values/app-manifest")
  @Timed
  @ExceptionMetered
  public RestResponse<ApplicationManifest> createK8sValueAppManifest(@QueryParam("appId") String appId,
      @PathParam("serviceId") String serviceId, ApplicationManifest applicationManifest) {
    return new RestResponse<>(serviceResourceService.createValuesAppManifest(appId, serviceId, applicationManifest));
  }

  @GET
  @Path("{serviceId}/k8s-values/app-manifest/{appManifestId}")
  @Timed
  @ExceptionMetered
  @ApiKeyAuthorized(permissionType = LOGGED_IN)
  public RestResponse<ApplicationManifest> getK8sValueAppManifest(@QueryParam("appId") String appId,
      @PathParam("serviceId") String serviceId, @PathParam("appManifestId") String appManifestId) {
    return new RestResponse<>(serviceResourceService.getValuesAppManifest(appId, serviceId, appManifestId));
  }

  @PUT
  @Path("{serviceId}/k8s-values/app-manifest/{appManifestId}")
  @Timed
  @ExceptionMetered
  public RestResponse<ApplicationManifest> updateK8sValueAppManifest(@QueryParam("appId") String appId,
      @PathParam("serviceId") String serviceId, @PathParam("appManifestId") String appManifestId,
      ApplicationManifest applicationManifest) {
    return new RestResponse<>(
        serviceResourceService.updateValuesAppManifest(appId, serviceId, appManifestId, applicationManifest));
  }

  @DELETE
  @Path("{serviceId}/k8s-values/app-manifest/{appManifestId}")
  @Timed
  @ExceptionMetered
  public RestResponse deleteK8sValueAppManifest(@QueryParam("appId") String appId,
      @PathParam("serviceId") String serviceId, @PathParam("appManifestId") String appManifestId) {
    serviceResourceService.deleteValuesAppManifest(appId, serviceId, appManifestId);
    return new RestResponse();
  }

  @GET
  @Path("{serviceId}/app-manifests")
  @Timed
  @ExceptionMetered
  @ApiKeyAuthorized(permissionType = LOGGED_IN)
  public RestResponse<List<ApplicationManifest>> listAppManifests(
      @QueryParam("appId") String appId, @PathParam("serviceId") String serviceId) {
    return new RestResponse<>(applicationManifestService.listAppManifests(appId, serviceId));
  }

  @GET
  @Path("{serviceId}/artifact-streams")
  @Timed
  @ExceptionMetered
  @ApiKeyAuthorized(permissionType = LOGGED_IN)
  public RestResponse<List<ArtifactStream>> listArtifactStreams(
      @QueryParam("appId") String appId, @PathParam("serviceId") String serviceId) {
    return new RestResponse<>(artifactStreamServiceBindingService.listArtifactStreams(appId, serviceId));
  }

  @GET
  @Path("{serviceId}/artifact-stream-bindings")
  @Timed
  @ExceptionMetered
  @ApiKeyAuthorized(permissionType = LOGGED_IN)
  public RestResponse<List<ArtifactStreamBinding>> listArtifactStreamBindings(
      @QueryParam("appId") String appId, @PathParam("serviceId") String serviceId) {
    return new RestResponse<>(artifactStreamServiceBindingService.list(appId, serviceId));
  }

  @GET
  @Path("{serviceId}/artifact-stream-bindings/{name}")
  @Timed
  @ExceptionMetered
  @ApiKeyAuthorized(permissionType = LOGGED_IN)
  public RestResponse<ArtifactStreamBinding> getArtifactStreamBinding(
      @QueryParam("appId") String appId, @PathParam("serviceId") String serviceId, @PathParam("name") String name) {
    return new RestResponse<>(artifactStreamServiceBindingService.get(appId, serviceId, name));
  }

  @POST
  @Path("{serviceId}/artifact-stream-bindings")
  @Timed
  @ExceptionMetered
  public RestResponse<ArtifactStreamBinding> createArtifactStreamBinding(@QueryParam("appId") String appId,
      @PathParam("serviceId") String serviceId, @NotNull ArtifactStreamBinding artifactStreamBinding) {
    return new RestResponse<>(artifactStreamServiceBindingService.create(appId, serviceId, artifactStreamBinding));
  }

  @PUT
  @Path("{serviceId}/artifact-stream-bindings/{name}")
  @Timed
  @ExceptionMetered
  public RestResponse<ArtifactStreamBinding> updateArtifactStreamBinding(@QueryParam("appId") String appId,
      @PathParam("serviceId") String serviceId, @PathParam("name") String name,
      ArtifactStreamBinding artifactStreamBinding) {
    if (name == null || artifactStreamBinding == null) {
      throw new InvalidRequestException("Name or artifact stream bindings not provided in request body", USER);
    }
    return new RestResponse<>(
        artifactStreamServiceBindingService.update(appId, serviceId, name, artifactStreamBinding));
  }

  @DELETE
  @Path("{serviceId}/artifact-stream-bindings/{name}")
  @Timed
  @ExceptionMetered
  public RestResponse deleteArtifactStreamBinding(
      @QueryParam("appId") String appId, @PathParam("serviceId") String serviceId, @PathParam("name") String name) {
    artifactStreamServiceBindingService.delete(appId, serviceId, name);
    return new RestResponse();
  }

  @GET
  @Path("forDeployment")
  @Timed
  @ExceptionMetered
  @ApiKeyAuthorized(permissionType = LOGGED_IN)
  public RestResponse<List<Service>> getServices(@QueryParam("appId") String appId,
      @NotEmpty @QueryParam("deploymentType") String deploymentType,
      @QueryParam("deploymentTypeTemplateId") String deploymentTypeTemplateId) {
    return new RestResponse<>(
        serviceResourceService.listByDeploymentType(appId, deploymentType, deploymentTypeTemplateId));
  }

  @GET
  @Path("{serviceId}/chart-versions")
  @Timed
  @ExceptionMetered
  @AuthRule(permissionType = PermissionType.SERVICE, action = Action.READ)
  @ApiKeyAuthorized(permissionType = PermissionType.SERVICE, action = Action.READ)
  public RestResponse<Map<String, List<HelmChart>>> getHelmChartVersions(@QueryParam("appId") String appId,
      @PathParam("serviceId") String serviceId, @QueryParam("manifestSearchString") String manifestSearchString,
      @BeanParam PageRequest<HelmChart> pageRequest,
      @QueryParam("showHelmChartsForDisabledCollection") @DefaultValue(
          "true") boolean showHelmChartsForDisabledCollection) {
    return new RestResponse<>(helmChartService.listHelmChartsForService(
        appId, serviceId, manifestSearchString, pageRequest, showHelmChartsForDisabledCollection));
  }

  @PUT
  @Path("{serviceId}/helm-version")
  @Timed
  @ExceptionMetered
  public RestResponse<Service> updateHelmVersion(
      @QueryParam("appId") String appId, @PathParam("serviceId") String serviceId, Service service) {
    service.setUuid(serviceId);
    service.setAppId(appId);
    return new RestResponse<>(serviceResourceService.updateServiceWithHelmVersion(service));
  }

  @GET
  @Path("{serviceId}/helm-command-flag")
  @Timed
  @ExceptionMetered
  @ApiKeyAuthorized(permissionType = LOGGED_IN)
  public RestResponse<Set<HelmSubCommand>> getHelmCommandFlags(@QueryParam("appId") String appId,
      @QueryParam("version") HelmVersion version, @QueryParam("storeType") StoreType storeType,
      @PathParam("serviceId") String serviceId) {
    return new RestResponse<>(serviceResourceService.getHelmCommandFlags(version, appId, serviceId, storeType));
  }
}
