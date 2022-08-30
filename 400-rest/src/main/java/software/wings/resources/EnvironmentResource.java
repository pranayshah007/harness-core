/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.resources;

import static io.harness.beans.SearchFilter.Operator.IN;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static software.wings.security.PermissionAttribute.PermissionType.ENV;
import static software.wings.security.PermissionAttribute.ResourceType.APPLICATION;
import static software.wings.security.PermissionAttribute.ResourceType.ENVIRONMENT;

import io.harness.beans.EnvironmentType;
import io.harness.beans.PageRequest;
import io.harness.beans.PageResponse;
import io.harness.rest.RestResponse;

import software.wings.beans.Environment;
import software.wings.beans.Environment.EnvironmentKeys;
import software.wings.beans.Service;
import software.wings.beans.appmanifest.AppManifestKind;
import software.wings.beans.appmanifest.ApplicationManifest;
import software.wings.beans.appmanifest.ManifestFile;
import software.wings.beans.container.KubernetesPayload;
import software.wings.beans.stats.CloneMetadata;
import software.wings.security.PermissionAttribute.Action;
import software.wings.security.PermissionAttribute.PermissionType;
import software.wings.security.annotations.AuthRule;
import software.wings.security.annotations.ListAPI;
import software.wings.security.annotations.Scope;
import software.wings.service.intfc.ApplicationManifestService;
import software.wings.service.intfc.AuthService;
import software.wings.service.intfc.EnvironmentService;

import com.codahale.metrics.annotation.ExceptionMetered;
import com.codahale.metrics.annotation.Timed;
import com.google.inject.Inject;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiParam;
import java.util.List;
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

/**
 * Created by anubhaw on 4/1/16.
 */
@Api("environments")
@Path("/environments")
@Produces("application/json")
@Consumes("application/json")
@Scope(APPLICATION)
@AuthRule(permissionType = ENV)
public class EnvironmentResource {
  @Inject private EnvironmentService environmentService;
  @Inject private AuthService authService;
  @Inject private ApplicationManifestService applicationManifestService;

  @Inject
  public EnvironmentResource(EnvironmentService environmentService, AuthService authService,
      ApplicationManifestService applicationManifestService) {
    this.environmentService = environmentService;
    this.authService = authService;
    this.applicationManifestService = applicationManifestService;
  }

  /**
   * List.
   *
   * @param appIds      the app ids
   * @param pageRequest the page request
   * @return the rest response
   */
  @GET
  @ListAPI(ENVIRONMENT)
  @Timed
  @ExceptionMetered
  public RestResponse<PageResponse<Environment>> list(@QueryParam("accountId") String accountId,
      @QueryParam("appId") List<String> appIds, @BeanParam PageRequest<Environment> pageRequest,
      @QueryParam("details") @DefaultValue("true") boolean details, @QueryParam("tagFilter") String tagFilter,
      @QueryParam("withTags") @DefaultValue("false") boolean withTags) {
    if (isNotEmpty(appIds)) {
      pageRequest.addFilter(EnvironmentKeys.appId, IN, appIds.toArray());
    }
    if (details) {
      return new RestResponse<>(environmentService.listWithSummary(pageRequest, withTags, tagFilter, appIds));
    } else {
      return new RestResponse<>(environmentService.list(pageRequest, withTags, tagFilter));
    }
  }

  /**
   * Save.
   *
   * @param appId       the app id
   * @param environment the environment
   * @return the rest response
   */
  @POST
  @Timed
  @ExceptionMetered
  public RestResponse<Environment> save(@QueryParam("appId") String appId, Environment environment) {
    authService.checkIfUserCanCreateEnv(appId, environment.getEnvironmentType());
    environment.setAppId(appId);
    return new RestResponse<>(environmentService.save(environment));
  }

  @GET
  @Path("{envId}")
  @Timed
  @ExceptionMetered
  public RestResponse<Environment> get(@QueryParam("appId") String appId, @PathParam("envId") String envId) {
    try {
      return new RestResponse<>(environmentService.get(appId, envId, false));
    } catch (Exception e) {
      return new RestResponse<>();
    }
  }

  /**
   * List.
   *
   * @param appId  the app id
   * @param envId  the env id
   * @return the rest response
   */
  @GET
  @Path("{envId}/services")
  @Timed
  @ExceptionMetered
  public RestResponse<List<Service>> getServicesWithOverrides(
      @QueryParam("appId") String appId, @PathParam("envId") String envId) {
    return new RestResponse(environmentService.getServicesWithOverrides(appId, envId));
  }

  /**
   * Update.
   *
   * @param appId       the app id
   * @param envId       the env id
   * @param environment the environment
   * @return the rest response
   */
  @PUT
  @Path("{envId}")
  @Timed
  @ExceptionMetered
  public RestResponse<Environment> update(
      @QueryParam("appId") String appId, @PathParam("envId") String envId, Environment environment) {
    environment.setUuid(envId);
    environment.setAppId(appId);
    return new RestResponse<>(environmentService.update(environment));
  }

  /**
   * Delete.
   *
   * @param appId the app id
   * @param envId the env id
   * @return the rest response
   */
  @DELETE
  @Path("{envId}")
  @Timed
  @ExceptionMetered
  public RestResponse delete(@QueryParam("appId") String appId, @PathParam("envId") String envId) {
    environmentService.delete(appId, envId);
    return new RestResponse();
  }

  /**
   * Clone environment rest response.
   *
   * @param appId      the app id
   * @param envId the workflow id
   * @param cloneMetadata   the clone metadata
   * @return the rest response
   */
  @POST
  @Path("{envId}/clone")
  @Timed
  @ExceptionMetered
  public RestResponse<Environment> cloneEnvironment(
      @QueryParam("appId") String appId, @PathParam("envId") String envId, CloneMetadata cloneMetadata) {
    Environment environment = environmentService.get(appId, envId, false);
    EnvironmentType envType = environment.getEnvironmentType();
    authService.checkIfUserCanCreateEnv(appId, envType);
    return new RestResponse<>(environmentService.cloneEnvironment(appId, envId, cloneMetadata));
  }

  @POST
  @Path("{envId}/config-map-yaml")
  @Timed
  @ExceptionMetered
  @AuthRule(permissionType = PermissionType.ENV, action = Action.UPDATE)
  public RestResponse<Environment> setConfigMapYaml(
      @ApiParam(name = "appId", required = true) @QueryParam("appId") String appId,
      @ApiParam(name = "envId", required = true) @PathParam("envId") String envId,
      KubernetesPayload kubernetesPayload) {
    return new RestResponse<>(environmentService.setConfigMapYaml(appId, envId, kubernetesPayload));
  }

  @PUT
  @Path("{envId}/config-map-yaml")
  @Timed
  @ExceptionMetered
  public RestResponse<Environment> updateConfigMapYaml(
      @ApiParam(name = "appId", required = true) @QueryParam("appId") String appId,
      @ApiParam(name = "envId", required = true) @PathParam("envId") String envId,
      KubernetesPayload kubernetesPayload) {
    return new RestResponse<>(environmentService.setConfigMapYaml(appId, envId, kubernetesPayload));
  }

  @DELETE
  @Path("{envId}/config-map-yaml")
  @Timed
  @ExceptionMetered
  public RestResponse<Environment> deleteConfigMapYaml(
      @ApiParam(name = "appId", required = true) @QueryParam("appId") String appId,
      @ApiParam(name = "envId", required = true) @PathParam("envId") String envId) {
    return new RestResponse<>(environmentService.setConfigMapYaml(appId, envId, new KubernetesPayload()));
  }

  @POST
  @Path("{envId}/config-map-yaml/{templateId}")
  @Timed
  @ExceptionMetered
  @AuthRule(permissionType = PermissionType.ENV, action = Action.UPDATE)
  public RestResponse<Environment> setConfigMapYamlForService(
      @ApiParam(name = "appId", required = true) @QueryParam("appId") String appId,
      @ApiParam(name = "envId", required = true) @PathParam("envId") String envId,
      @ApiParam(name = "templateId", required = true) @PathParam("templateId") String templateId,
      KubernetesPayload kubernetesPayload) {
    return new RestResponse<>(
        environmentService.setConfigMapYamlForService(appId, envId, templateId, kubernetesPayload));
  }

  @PUT
  @Path("{envId}/config-map-yaml/{templateId}")
  @Timed
  @ExceptionMetered
  public RestResponse<Environment> updateConfigMapYamlForService(
      @ApiParam(name = "appId", required = true) @QueryParam("appId") String appId,
      @ApiParam(name = "envId", required = true) @PathParam("envId") String envId,
      @ApiParam(name = "templateId", required = true) @PathParam("templateId") String templateId,
      KubernetesPayload kubernetesPayload) {
    return new RestResponse<>(
        environmentService.setConfigMapYamlForService(appId, envId, templateId, kubernetesPayload));
  }

  @DELETE
  @Path("{envId}/config-map-yaml/{templateId}")
  @Timed
  @ExceptionMetered
  public RestResponse<Environment> deleteConfigMapYamlForService(
      @ApiParam(name = "appId", required = true) @QueryParam("appId") String appId,
      @ApiParam(name = "envId", required = true) @PathParam("envId") String envId,
      @ApiParam(name = "templateId", required = true) @PathParam("templateId") String templateId) {
    return new RestResponse<>(
        environmentService.setConfigMapYamlForService(appId, envId, templateId, new KubernetesPayload()));
  }

  @POST
  @Path("{envId}/helm-value-yaml")
  @Timed
  @ExceptionMetered
  @AuthRule(permissionType = PermissionType.ENV, action = Action.UPDATE)
  public RestResponse<Environment> setHelmValueYaml(
      @ApiParam(name = "appId", required = true) @QueryParam("appId") String appId,
      @ApiParam(name = "envId", required = true) @PathParam("envId") String envId,
      KubernetesPayload kubernetesPayload) {
    return new RestResponse<>(environmentService.setHelmValueYaml(appId, envId, null, kubernetesPayload));
  }

  @PUT
  @Path("{envId}/helm-value-yaml")
  @Timed
  @ExceptionMetered
  public RestResponse<Environment> updateHelmValueYaml(
      @ApiParam(name = "appId", required = true) @QueryParam("appId") String appId,
      @ApiParam(name = "envId", required = true) @PathParam("envId") String envId,
      KubernetesPayload kubernetesPayload) {
    return new RestResponse<>(environmentService.setHelmValueYaml(appId, envId, null, kubernetesPayload));
  }

  @DELETE
  @Path("{envId}/helm-value-yaml")
  @Timed
  @ExceptionMetered
  public RestResponse<Environment> deleteHelmValueYaml(
      @ApiParam(name = "appId", required = true) @QueryParam("appId") String appId,
      @ApiParam(name = "envId", required = true) @PathParam("envId") String envId) {
    return new RestResponse<>(environmentService.deleteHelmValueYaml(appId, envId, null));
  }

  @POST
  @Path("{envId}/helm-value-yaml/{templateId}")
  @Timed
  @ExceptionMetered
  @AuthRule(permissionType = PermissionType.ENV, action = Action.UPDATE)
  public RestResponse<Environment> setHelmValueYamlForService(
      @ApiParam(name = "appId", required = true) @QueryParam("appId") String appId,
      @ApiParam(name = "envId", required = true) @PathParam("envId") String envId,
      @ApiParam(name = "templateId", required = true) @PathParam("templateId") String templateId,
      KubernetesPayload kubernetesPayload) {
    return new RestResponse<>(environmentService.setHelmValueYaml(appId, envId, templateId, kubernetesPayload));
  }

  @PUT
  @Path("{envId}/helm-value-yaml/{templateId}")
  @Timed
  @ExceptionMetered
  public RestResponse<Environment> updateHelmValueYamlForService(
      @ApiParam(name = "appId", required = true) @QueryParam("appId") String appId,
      @ApiParam(name = "envId", required = true) @PathParam("envId") String envId,
      @ApiParam(name = "templateId", required = true) @PathParam("templateId") String templateId,
      KubernetesPayload kubernetesPayload) {
    return new RestResponse<>(environmentService.setHelmValueYaml(appId, envId, templateId, kubernetesPayload));
  }

  @DELETE
  @Path("{envId}/helm-value-yaml/{templateId}")
  @Timed
  @ExceptionMetered
  public RestResponse<Environment> deleteHelmValueYamlForService(
      @ApiParam(name = "appId", required = true) @QueryParam("appId") String appId,
      @ApiParam(name = "envId", required = true) @PathParam("envId") String envId,
      @ApiParam(name = "templateId", required = true) @PathParam("templateId") String templateId) {
    return new RestResponse<>(environmentService.deleteHelmValueYaml(appId, envId, templateId));
  }

  @POST
  @Path("{envId}/values")
  @Timed
  @ExceptionMetered
  @AuthRule(permissionType = PermissionType.ENV, action = Action.UPDATE)
  public RestResponse<ManifestFile> createValues(@QueryParam("appId") String appId,
      @QueryParam("kind") AppManifestKind kind, @PathParam("envId") String envId, ManifestFile manifestFile) {
    return new RestResponse<>(environmentService.createValues(appId, envId, null, manifestFile, kind));
  }

  @PUT
  @Path("{envId}/values/{manifestFileId}")
  @Timed
  @ExceptionMetered
  @AuthRule(permissionType = PermissionType.ENV, action = Action.UPDATE)
  public RestResponse<ManifestFile> updateValues(@QueryParam("appId") String appId,
      @QueryParam("kind") AppManifestKind kind, @PathParam("envId") String envId,
      @PathParam("manifestFileId") String manifestFileId, ManifestFile manifestFile) {
    manifestFile.setUuid(manifestFileId);
    return new RestResponse<>(environmentService.updateValues(appId, envId, null, manifestFile, kind));
  }

  @GET
  @Path("{envId}/values/{manifestFileId}")
  @Timed
  @ExceptionMetered
  @AuthRule(permissionType = PermissionType.ENV, action = Action.READ)
  public RestResponse<ManifestFile> getValues(@QueryParam("appId") String appId, @PathParam("envId") String envId,
      @PathParam("manifestFileId") String manifestFileId) {
    return new RestResponse<>(applicationManifestService.getManifestFileById(appId, manifestFileId));
  }

  @DELETE
  @Path("{envId}/values/{manifestFileId}")
  @Timed
  @ExceptionMetered
  @AuthRule(permissionType = PermissionType.ENV, action = Action.UPDATE)
  public RestResponse<ManifestFile> deleteValues(@QueryParam("appId") String appId, @PathParam("envId") String envId,
      @PathParam("manifestFileId") String manifestFileId) {
    applicationManifestService.deleteManifestFileById(appId, manifestFileId);
    return new RestResponse();
  }

  @POST
  @Path("{envId}/service/{serviceId}/values")
  @Timed
  @ExceptionMetered
  @AuthRule(permissionType = PermissionType.ENV, action = Action.UPDATE)
  public RestResponse<ManifestFile> createValuesForService(@QueryParam("appId") String appId,
      @QueryParam("kind") AppManifestKind kind, @PathParam("envId") String envId,
      @PathParam("serviceId") String serviceId, ManifestFile manifestFile) {
    return new RestResponse<>(environmentService.createValues(appId, envId, serviceId, manifestFile, kind));
  }

  @PUT
  @Path("{envId}/service/{serviceId}/values/{manifestFileId}")
  @Timed
  @ExceptionMetered
  @AuthRule(permissionType = PermissionType.ENV, action = Action.UPDATE)
  public RestResponse<ManifestFile> updateValuesForService(@QueryParam("appId") String appId,
      @QueryParam("kind") AppManifestKind kind, @PathParam("envId") String envId,
      @PathParam("serviceId") String serviceId, @PathParam("manifestFileId") String manifestFileId,
      ManifestFile manifestFile) {
    manifestFile.setUuid(manifestFileId);
    return new RestResponse<>(environmentService.updateValues(appId, envId, serviceId, manifestFile, kind));
  }

  @GET
  @Path("{envId}/service/{serviceId}/values/{manifestFileId}")
  @Timed
  @ExceptionMetered
  @AuthRule(permissionType = PermissionType.ENV, action = Action.READ)
  public RestResponse<ManifestFile> getValuesForService(@QueryParam("appId") String appId,
      @PathParam("envId") String envId, @PathParam("serviceId") String serviceId,
      @PathParam("manifestFileId") String manifestFileId) {
    return new RestResponse<>(applicationManifestService.getManifestFileById(appId, manifestFileId));
  }

  @DELETE
  @Path("{envId}/service/{serviceId}/values/{manifestFileId}")
  @Timed
  @ExceptionMetered
  @AuthRule(permissionType = PermissionType.ENV, action = Action.UPDATE)
  public RestResponse<ManifestFile> deleteValuesForService(@QueryParam("appId") String appId,
      @PathParam("envId") String envId, @PathParam("serviceId") String serviceId,
      @PathParam("manifestFileId") String manifestFileId) {
    applicationManifestService.deleteManifestFileById(appId, manifestFileId);
    return new RestResponse();
  }

  @POST
  @Path("{envId}/values/app-manifest")
  @Timed
  @ExceptionMetered
  @AuthRule(permissionType = PermissionType.ENV, action = Action.UPDATE)
  public RestResponse<ApplicationManifest> createValuesAppManifest(
      @QueryParam("appId") String appId, @PathParam("envId") String envId, ApplicationManifest applicationManifest) {
    applicationManifest.setAppId(appId);
    applicationManifest.setEnvId(envId);
    return new RestResponse<>(applicationManifestService.create(applicationManifest));
  }

  @GET
  @Path("{envId}/values/app-manifest/{appManifestId}")
  @Timed
  @ExceptionMetered
  @AuthRule(permissionType = PermissionType.ENV, action = Action.READ)
  public RestResponse<ApplicationManifest> getValuesAppManifest(@QueryParam("appId") String appId,
      @PathParam("envId") String envId, @PathParam("appManifestId") String appManifestId) {
    return new RestResponse<>(applicationManifestService.getById(appId, appManifestId));
  }

  @PUT
  @Path("{envId}/values/app-manifest/{appManifestId}")
  @Timed
  @ExceptionMetered
  @AuthRule(permissionType = PermissionType.ENV, action = Action.UPDATE)
  public RestResponse<ApplicationManifest> updateValuesAppManifest(@QueryParam("appId") String appId,
      @PathParam("envId") String envId, @PathParam("appManifestId") String appManifestId,
      ApplicationManifest applicationManifest) {
    applicationManifest.setAppId(appId);
    applicationManifest.setEnvId(envId);
    applicationManifest.setUuid(appManifestId);
    return new RestResponse<>(applicationManifestService.update(applicationManifest));
  }

  @DELETE
  @Path("{envId}/values/app-manifest/{appManifestId}")
  @Timed
  @ExceptionMetered
  @AuthRule(permissionType = PermissionType.ENV, action = Action.UPDATE)
  public RestResponse<ApplicationManifest> deleteValuesAppManifest(@QueryParam("appId") String appId,
      @PathParam("envId") String envId, @PathParam("appManifestId") String appManifestId) {
    applicationManifestService.deleteAppManifest(appId, appManifestId);
    return new RestResponse();
  }

  @POST
  @Path("{envId}/service/{serviceId}/values/app-manifest")
  @Timed
  @ExceptionMetered
  @AuthRule(permissionType = PermissionType.ENV, action = Action.UPDATE)
  public RestResponse<ApplicationManifest> createValuesAppManifestForService(@QueryParam("appId") String appId,
      @PathParam("envId") String envId, @PathParam("serviceId") String serviceId,
      ApplicationManifest applicationManifest) {
    applicationManifest.setAppId(appId);
    applicationManifest.setEnvId(envId);
    applicationManifest.setServiceId(serviceId);
    return new RestResponse<>(applicationManifestService.create(applicationManifest));
  }

  @GET
  @Path("{envId}/service/{serviceId}/values/app-manifest/{appManifestId}")
  @Timed
  @ExceptionMetered
  @AuthRule(permissionType = PermissionType.ENV, action = Action.READ)
  public RestResponse<ApplicationManifest> getValuesAppManifestForService(@QueryParam("appId") String appId,
      @PathParam("envId") String envId, @PathParam("serviceId") String serviceId,
      @PathParam("appManifestId") String appManifestId) {
    return new RestResponse<>(applicationManifestService.getById(appId, appManifestId));
  }

  @PUT
  @Path("{envId}/service/{serviceId}/values/app-manifest/{appManifestId}")
  @Timed
  @ExceptionMetered
  @AuthRule(permissionType = PermissionType.ENV, action = Action.UPDATE)
  public RestResponse<ApplicationManifest> updateValuesAppManifestForService(@QueryParam("appId") String appId,
      @PathParam("envId") String envId, @PathParam("serviceId") String serviceId,
      @PathParam("appManifestId") String appManifestId, ApplicationManifest applicationManifest) {
    applicationManifest.setAppId(appId);
    applicationManifest.setEnvId(envId);
    applicationManifest.setServiceId(serviceId);
    applicationManifest.setUuid(appManifestId);
    return new RestResponse<>(applicationManifestService.update(applicationManifest));
  }

  @DELETE
  @Path("{envId}/service/{serviceId}/values/app-manifest/{appManifestId}")
  @Timed
  @ExceptionMetered
  @AuthRule(permissionType = PermissionType.ENV, action = Action.UPDATE)
  public RestResponse<ApplicationManifest> deleteValuesAppManifestForService(@QueryParam("appId") String appId,
      @PathParam("envId") String envId, @PathParam("serviceId") String serviceId,
      @PathParam("appManifestId") String appManifestId) {
    applicationManifestService.deleteAppManifest(appId, appManifestId);
    return new RestResponse();
  }

  @GET
  @Path("{envId}/values-app-manifest")
  @Timed
  @ExceptionMetered
  @AuthRule(permissionType = PermissionType.ENV, action = Action.READ)
  public RestResponse<ApplicationManifest> getValuesAppManifest(
      @QueryParam("appId") String appId, @PathParam("envId") String envId) {
    return new RestResponse<>(applicationManifestService.getByEnvId(appId, envId, AppManifestKind.VALUES));
  }

  @GET
  @Path("{envId}/values-manifest-file")
  @Timed
  @ExceptionMetered
  @AuthRule(permissionType = PermissionType.ENV, action = Action.READ)
  public RestResponse<ManifestFile> getValuesManifestFile(
      @QueryParam("appId") String appId, @PathParam("envId") String envId) {
    return new RestResponse<>(applicationManifestService.getManifestFileByEnvId(appId, envId, AppManifestKind.VALUES));
  }

  @GET
  @Path("{envId}/manifest-files")
  @Timed
  @ExceptionMetered
  @AuthRule(permissionType = PermissionType.ENV, action = Action.READ)
  public RestResponse<List<ManifestFile>> getLocalOverrideManifestFiles(
      @QueryParam("appId") String appId, @PathParam("envId") String envId) {
    return new RestResponse<>(applicationManifestService.getOverrideManifestFilesByEnvId(appId, envId));
  }
}
