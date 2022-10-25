/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.resources;

import static software.wings.beans.ConfigFile.DEFAULT_TEMPLATE_ID;
import static software.wings.beans.EntityType.SERVICE;

import static javax.ws.rs.core.MediaType.MULTIPART_FORM_DATA;

import io.harness.beans.PageRequest;
import io.harness.beans.PageResponse;
import io.harness.rest.RestResponse;
import io.harness.serializer.JsonUtils;
import io.harness.stream.BoundedInputStream;
import io.harness.validation.PersistenceValidator;

import software.wings.app.MainConfiguration;
import software.wings.beans.Application;
import software.wings.beans.ConfigFile;
import software.wings.beans.EntityType;
import software.wings.beans.EntityVersion;
import software.wings.security.PermissionAttribute.ResourceType;
import software.wings.security.annotations.Scope;
import software.wings.service.impl.security.auth.ConfigFileAuthHandler;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.ConfigService;

import com.codahale.metrics.annotation.ExceptionMetered;
import com.codahale.metrics.annotation.Timed;
import com.fasterxml.jackson.core.type.TypeReference;
import com.google.inject.Inject;
import io.swagger.annotations.Api;
import java.io.File;
import java.io.InputStream;
import java.util.Map;
import javax.ws.rs.BeanParam;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.Encoded;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;
import org.glassfish.jersey.media.multipart.FormDataContentDisposition;
import org.glassfish.jersey.media.multipart.FormDataParam;

/**
 * Application Resource class.
 *
 * @author Rishi
 */
@Api("configs")
@Path("/configs")
@Produces("application/json")
@Scope(ResourceType.APPLICATION)
// ToBeRevisited, this resource would be used from both service and env overrides ui.
// Need to find out which auth rule to apply since its only determined at runtime
public class ConfigResource {
  @Inject private ConfigService configService;
  @Inject private MainConfiguration configuration;
  @Inject private AppService appService;
  @Inject private ConfigFileAuthHandler configFileAuthHandler;

  /**
   * List.
   *
   * @param pageRequest the page request
   * @return the rest response
   */
  @GET
  @Timed
  @ExceptionMetered
  public RestResponse<PageResponse<ConfigFile>> list(@BeanParam PageRequest<ConfigFile> pageRequest) {
    return new RestResponse<>(configService.list(pageRequest));
  }

  /**
   * Save.
   *
   * @param appId               the app id
   * @param entityId            the entity id
   * @param entityType          the entity type
   * @param uploadedInputStream the uploaded input stream
   * @param fileName            the file name
   * @param configFile          the config file
   * @return the rest response
   */
  @POST
  @Consumes(MULTIPART_FORM_DATA)
  @Timed
  @ExceptionMetered
  public RestResponse<String> save(@QueryParam("appId") String appId, @QueryParam("entityId") String entityId,
      @QueryParam("entityType") EntityType entityType, @FormDataParam("file") InputStream uploadedInputStream,
      @FormDataParam("fileName") String fileName, @BeanParam ConfigFile configFile) {
    Application application = appService.get(appId);
    configFile.setAppId(application.getAppId());
    configFile.setAccountId(application.getAccountId());
    configFile.setEntityId(entityId);
    configFile.setEntityType(entityType == null ? SERVICE : entityType);
    configFile.setFileName(fileName);
    configFileAuthHandler.authorize(configFile);
    if (configFile.getEnvIdVersionMapString() != null) {
      try {
        Map<String, EntityVersion> envIdVersionMap = JsonUtils.asObject(
            configFile.getEnvIdVersionMapString(), new TypeReference<Map<String, EntityVersion>>() {});
        configFile.setEnvIdVersionMap(envIdVersionMap);
      } catch (Exception e) {
        // Ignore
      }
    }

    String fileId = PersistenceValidator.duplicateCheck(
        ()
            -> configService.save(configFile,
                new BoundedInputStream(uploadedInputStream, configuration.getFileUploadLimits().getConfigFileLimit())),
        "name", configFile.getRelativeFilePath());
    return new RestResponse<>(fileId);
  }

  /**
   * Gets the.
   *
   * @param appId    the app id
   * @param configId the config id
   * @return the rest response
   */
  @GET
  @Path("{configId}")
  @Timed
  @ExceptionMetered
  public RestResponse<ConfigFile> get(@QueryParam("appId") String appId, @PathParam("configId") String configId) {
    return new RestResponse<>(configService.get(appId, configId));
  }

  /**
   * Update.
   *
   * @param appId               the app id
   * @param configId            the config id
   * @param uploadedInputStream the uploaded input stream
   * @param fileDetail          the file detail
   * @param configFile          the config file
   * @return the rest response
   */
  @PUT
  @Path("{configId}")
  @Consumes(MULTIPART_FORM_DATA)
  @Timed
  @ExceptionMetered
  public RestResponse update(@QueryParam("appId") String appId, @PathParam("configId") String configId,
      @FormDataParam("file") InputStream uploadedInputStream,
      @FormDataParam("file") FormDataContentDisposition fileDetail, @BeanParam ConfigFile configFile) {
    Application application = appService.get(appId);
    configFile.setAppId(application.getAppId());
    configFile.setAccountId(application.getAccountId());
    configFile.setUuid(configId);
    try {
      Map<String, EntityVersion> envIdVersionMap =
          JsonUtils.asObject(configFile.getEnvIdVersionMapString(), new TypeReference<Map<String, EntityVersion>>() {});
      configFile.setEnvIdVersionMap(envIdVersionMap);
    } catch (Exception e) {
      // Ignore
    }
    if (fileDetail != null && fileDetail.getFileName() != null) {
      configFile.setFileName(new File(fileDetail.getFileName()).getName());
    }
    configFileAuthHandler.authorize(appId, configFile.getUuid());
    configService.update(configFile,
        uploadedInputStream == null
            ? null
            : new BoundedInputStream(uploadedInputStream, configuration.getFileUploadLimits().getConfigFileLimit()));
    return new RestResponse();
  }

  /**
   * Delete.
   *
   * @param appId    the app id
   * @param configId the config id
   * @return the rest response
   */
  @DELETE
  @Path("{configId}")
  @Timed
  @ExceptionMetered
  public RestResponse delete(@QueryParam("appId") String appId, @PathParam("configId") String configId) {
    configFileAuthHandler.authorize(appId, configId);
    configService.delete(appId, configId);
    return new RestResponse();
  }

  /**
   * Export configFile.
   *
   * @param appId    the app id
   * @param configId the config id
   * @return the response
   */
  @GET
  @Path("{configId}/download")
  @Encoded
  @Timed
  @ExceptionMetered
  public Response downloadConfig(@QueryParam("appId") String appId, @PathParam("configId") String configId,
      @QueryParam("version") Integer version) {
    ConfigFile configFile = configService.get(appId, configId);
    if (configFile.isEncrypted()) {
      return Response.noContent().build();
    } else {
      File file = configService.download(appId, configId, version);
      ResponseBuilder response = Response.ok(file, "application/x-unknown");
      response.header("Content-Disposition", "attachment; filename=" + configFile.getName());
      return response.build();
    }
  }

  /**
   * Delete by entity rest response.
   *
   * @param appId      the app id
   * @param templateId the template id
   * @param entityId   the entity id
   * @return the rest response
   */
  @DELETE
  @Path("/entity/{entityId}")
  @Timed
  @ExceptionMetered
  public RestResponse deleteByEntity(@QueryParam("appId") String appId,
      @DefaultValue(DEFAULT_TEMPLATE_ID) @QueryParam("templateId") String templateId,
      @PathParam("entityId") String entityId) {
    configService.deleteByEntityId(appId, templateId, entityId);
    return new RestResponse();
  }
}
