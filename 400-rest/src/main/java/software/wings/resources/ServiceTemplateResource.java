/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.resources;

import static io.harness.beans.SearchFilter.Operator.EQ;

import static software.wings.security.PermissionAttribute.Action.READ;
import static software.wings.security.PermissionAttribute.PermissionType.ENV;
import static software.wings.service.intfc.ServiceVariableService.EncryptedFieldMode.MASKED;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.PageRequest;
import io.harness.beans.PageResponse;
import io.harness.rest.RestResponse;
import io.harness.security.annotations.DelegateAuth;
import io.harness.serializer.KryoSerializer;

import software.wings.beans.ConfigFile;
import software.wings.beans.ConfigFileDto;
import software.wings.beans.ServiceTemplate;
import software.wings.security.PermissionAttribute.ResourceType;
import software.wings.security.annotations.AuthRule;
import software.wings.security.annotations.Scope;
import software.wings.service.intfc.ServiceTemplateService;

import com.codahale.metrics.annotation.ExceptionMetered;
import com.codahale.metrics.annotation.Timed;
import com.google.inject.Inject;
import io.swagger.annotations.Api;
import java.util.List;
import java.util.stream.Collectors;
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
 * Created by anubhaw on 4/4/16.
 */
@Api("service-templates")
@Path("/service-templates")
@Produces("application/json")
@Consumes("application/json")
@Scope(ResourceType.APPLICATION)
@OwnedBy(HarnessTeam.CDC)
public class ServiceTemplateResource {
  /**
   * The Service template service.
   */
  @Inject ServiceTemplateService serviceTemplateService;
  @Inject private KryoSerializer kryoSerializer;

  /**
   * List.
   *
   * @param envId       the env id
   * @param appId       the app id
   * @param pageRequest the page request
   * @return the rest response
   */
  @GET
  @Timed
  @ExceptionMetered
  @AuthRule(permissionType = ENV, action = READ)
  public RestResponse<PageResponse<ServiceTemplate>> list(@QueryParam("envId") String envId,
      @QueryParam("appId") String appId, @BeanParam PageRequest<ServiceTemplate> pageRequest,
      @QueryParam("details") @DefaultValue("true") boolean details) {
    pageRequest.addFilter("appId", EQ, appId);
    pageRequest.addFilter("envId", EQ, envId);
    return new RestResponse<>(
        serviceTemplateService.listWithoutServiceAndInfraMappingSummary(pageRequest, details, MASKED));
  }

  /**
   * Creates the.
   *
   * @param envId           the env id
   * @param appId           the app id
   * @param serviceTemplate the service template
   * @return the rest response
   */
  @POST
  @Timed
  @ExceptionMetered
  public RestResponse<ServiceTemplate> create(
      @QueryParam("envId") String envId, @QueryParam("appId") String appId, ServiceTemplate serviceTemplate) {
    serviceTemplate.setAppId(appId);
    serviceTemplate.setEnvId(envId);
    return new RestResponse<>(serviceTemplateService.save(serviceTemplate));
  }

  /**
   * Gets the.
   *
   * @param envId             the env id
   * @param appId             the app id
   * @param serviceTemplateId the service template id
   * @return the rest response
   */
  @GET
  @Path("{templateId}")
  @Timed
  @ExceptionMetered
  public RestResponse<ServiceTemplate> get(@QueryParam("envId") String envId, @QueryParam("appId") String appId,
      @PathParam("templateId") String serviceTemplateId) {
    return new RestResponse<>(serviceTemplateService.get(appId, envId, serviceTemplateId, true, MASKED));
  }

  /**
   * Update.
   *
   * @param envId             the env id
   * @param appId             the app id
   * @param serviceTemplateId the service template id
   * @param serviceTemplate   the service template
   * @return the rest response
   */
  @PUT
  @Path("{templateId}")
  @Timed
  @ExceptionMetered
  public RestResponse<ServiceTemplate> update(@QueryParam("envId") String envId, @QueryParam("appId") String appId,
      @PathParam("templateId") String serviceTemplateId, ServiceTemplate serviceTemplate) {
    serviceTemplate.setAppId(appId);
    serviceTemplate.setEnvId(envId);
    serviceTemplate.setUuid(serviceTemplateId);
    return new RestResponse<>(serviceTemplateService.update(serviceTemplate));
  }

  /**
   * Delete.
   *
   * @param envId             the env id
   * @param appId             the app id
   * @param serviceTemplateId the service template id
   * @return the rest response
   */
  @DELETE
  @Path("{templateId}")
  @Timed
  @ExceptionMetered
  public RestResponse delete(@QueryParam("envId") String envId, @QueryParam("appId") String appId,
      @PathParam("templateId") String serviceTemplateId) {
    serviceTemplateService.delete(appId, serviceTemplateId);
    return new RestResponse();
  }

  @DelegateAuth
  @GET
  @Path("{templateId}/compute-files")
  @Timed
  @ExceptionMetered
  public RestResponse<String> computeFiles(@PathParam("templateId") String templateId,
      @QueryParam("envId") @NotEmpty String envId, @QueryParam("appId") @NotEmpty String appId,
      @QueryParam("hostId") String hostId, @QueryParam("accountId") @NotEmpty String accountId) {
    List<ConfigFile> configFiles = serviceTemplateService.computedConfigFiles(appId, envId, templateId);

    return new RestResponse<>(kryoSerializer.asString(configFiles));
  }

  @DelegateAuth
  @GET
  @Path("{templateId}/compute-files-dto")
  @Timed
  @ExceptionMetered
  public RestResponse<List<ConfigFileDto>> computeFilesDto(@PathParam("templateId") String templateId,
      @QueryParam("envId") @NotEmpty String envId, @QueryParam("appId") @NotEmpty String appId,
      @QueryParam("hostId") String hostId, @QueryParam("accountId") @NotEmpty String accountId) {
    List<ConfigFile> configFiles = serviceTemplateService.computedConfigFiles(appId, envId, templateId);
    return new RestResponse<>(configFiles.stream().map(ConfigFile::toDto).collect(Collectors.toList()));
  }
}
