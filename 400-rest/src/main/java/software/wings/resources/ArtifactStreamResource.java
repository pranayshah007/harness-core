/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.resources;

import static io.harness.beans.SearchFilter.Operator.EQ;

import static software.wings.security.PermissionAttribute.PermissionType.SERVICE;
import static software.wings.security.PermissionAttribute.ResourceType.APPLICATION;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.PageRequest;
import io.harness.beans.PageResponse;
import io.harness.rest.RestResponse;

import software.wings.beans.artifact.ArtifactStream;
import software.wings.beans.artifact.ArtifactStreamSummary;
import software.wings.security.PermissionAttribute;
import software.wings.security.annotations.AuthRule;
import software.wings.security.annotations.Scope;
import software.wings.service.impl.security.auth.AuthHandler;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.ArtifactStreamService;

import com.codahale.metrics.annotation.ExceptionMetered;
import com.codahale.metrics.annotation.Timed;
import com.google.inject.Inject;
import io.dropwizard.jersey.PATCH;
import io.swagger.annotations.Api;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import javax.ws.rs.BeanParam;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;

/**
 * ArtifactStreamResource class.
 *
 * @author Rishi
 */
@Api("artifactstreams")
@Path("/artifactstreams")
@Produces("application/json")
@Consumes("application/json")
@Scope(APPLICATION)
@AuthRule(permissionType = SERVICE, skipAuth = true)
@OwnedBy(HarnessTeam.CDC)
public class ArtifactStreamResource {
  private ArtifactStreamService artifactStreamService;
  private AppService appService;
  @Inject private AuthHandler authHandler;

  /**
   * Instantiates a new Artifact stream resource.
   *
   * @param artifactStreamService               the artifact stream service
   * @param appService                          the app service
   */
  @Inject
  public ArtifactStreamResource(ArtifactStreamService artifactStreamService, AppService appService) {
    this.artifactStreamService = artifactStreamService;
    this.appService = appService;
  }

  /**
   * Sets artifact stream service.
   *
   * @param artifactStreamService the artifact stream service
   */
  public void setArtifactStreamService(ArtifactStreamService artifactStreamService) {
    this.artifactStreamService = artifactStreamService;
  }

  /**
   * Sets app service.
   *
   * @param appService the app service
   */
  public void setAppService(AppService appService) {
    this.appService = appService;
  }

  /**
   * List.
   *
   * @param appId       the app id
   * @param pageRequest the page request
   * @return the rest response
   */
  @GET
  @Timed
  @ExceptionMetered
  public RestResponse<PageResponse<ArtifactStream>> list(@QueryParam("appId") String appId,
      @QueryParam("accountId") String accountId, @QueryParam("withArtifactCount") boolean withArtifactCount,
      @QueryParam("artifactSearchString") String artifactSearchString,
      @BeanParam PageRequest<ArtifactStream> pageRequest) {
    pageRequest.addFilter("appId", EQ, appId);
    return new RestResponse<>(
        artifactStreamService.list(pageRequest, accountId, withArtifactCount, artifactSearchString));
  }

  /**
   * Gets the.
   *
   * @param appId    the app id
   * @param streamId the stream id
   * @return the rest response
   */
  @GET
  @Path("{streamId}")
  @Timed
  @ExceptionMetered
  public RestResponse<ArtifactStream> get(@QueryParam("appId") String appId, @PathParam("streamId") String streamId) {
    return new RestResponse<>(artifactStreamService.get(streamId));
  }

  /**
   * Save rest response.
   *
   * @param appId          the app id
   * @param artifactStream the artifact stream
   * @return the rest response
   */
  @POST
  @Timed
  @ExceptionMetered
  public RestResponse<ArtifactStream> save(@QueryParam("appId") String appId, ArtifactStream artifactStream) {
    if (!appService.exist(appId)) {
      throw new NotFoundException("application with id " + appId + " not found.");
    }

    authHandler.authorize(
        Collections.singletonList(new PermissionAttribute(SERVICE, PermissionAttribute.Action.UPDATE)),
        Collections.singletonList(appId), artifactStream.getServiceId());
    artifactStream.setAppId(appId);
    return new RestResponse<>(artifactStreamService.createWithBinding(appId, artifactStream, true));
  }

  /**
   * Update rest response.
   *
   * @param appId          the app id
   * @param streamId       the stream id
   * @param artifactStream the artifact stream
   * @return the rest response
   */
  @PUT
  @Path("{streamId}")
  @Timed
  @ExceptionMetered
  public RestResponse<ArtifactStream> update(
      @QueryParam("appId") String appId, @PathParam("streamId") String streamId, ArtifactStream artifactStream) {
    artifactStream.setUuid(streamId);
    artifactStream.setAppId(appId);
    // NOTE: should not update serviceId
    authHandler.authorize(
        Collections.singletonList(new PermissionAttribute(SERVICE, PermissionAttribute.Action.UPDATE)),
        Collections.singletonList(appId), artifactStream.getServiceId());
    return new RestResponse<>(artifactStreamService.update(artifactStream));
  }

  /**
   * Delete.
   *
   * @param appId the app id
   * @param id    the id
   * @return the rest response
   */
  @DELETE
  @Path("{id}")
  @Timed
  @ExceptionMetered
  public RestResponse delete(@QueryParam("appId") String appId, @PathParam("id") String id) {
    ArtifactStream artifactStream = artifactStreamService.get(id);
    authHandler.authorize(
        Collections.singletonList(new PermissionAttribute(SERVICE, PermissionAttribute.Action.DELETE)),
        Collections.singletonList(appId), artifactStream.getServiceId());
    artifactStreamService.deleteWithBinding(appId, id, false, false);
    return new RestResponse<>();
  }

  /**
   * Gets build source types.
   *
   * @param appId     the app id
   * @param serviceId the service id
   * @return the build source types
   */
  @GET
  @Path("buildsource-types")
  @Timed
  @ExceptionMetered
  public RestResponse<Map<String, String>> getBuildSourceTypes(
      @QueryParam("appId") String appId, @QueryParam("serviceId") String serviceId) {
    return new RestResponse<>(artifactStreamService.getSupportedBuildSourceTypes(appId, serviceId));
  }

  /**
   * List summary.
   *
   * @param appId the app id
   * @return the rest response
   */
  @GET
  @Path("summary")
  @Timed
  @ExceptionMetered
  public RestResponse<List<ArtifactStreamSummary>> listArtifactStreamSummary(@QueryParam("appId") String appId) {
    if (!appService.exist(appId)) {
      throw new NotFoundException("application with id " + appId + " not found.");
    }

    return new RestResponse<>(artifactStreamService.listArtifactStreamSummary(appId));
  }

  /**
   * List summary.
   *
   * @param id the artifactstream id
   * @return the rest response
   */
  @GET
  @Path("{id}/parameters")
  @Timed
  @ExceptionMetered
  public RestResponse<List<String>> listArtifactStreamParameters(@PathParam("id") String id) {
    return new RestResponse<>(artifactStreamService.getArtifactStreamParameters(id));
  }

  @PATCH
  @Timed
  @Path("resetCollection/{id}")
  @ExceptionMetered
  public RestResponse<ArtifactStream> resetArtifactCollection(
      @QueryParam("appId") String appId, @PathParam("id") String id) {
    return new RestResponse<>(artifactStreamService.resetStoppedArtifactCollection(appId, id));
  }
}
