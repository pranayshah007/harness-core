/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.resources;

import static io.harness.beans.SearchFilter.Operator.EQ;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.PageRequest;
import io.harness.beans.PageResponse;
import io.harness.rest.RestResponse;

import software.wings.beans.Service;
import software.wings.beans.alert.AlertType;
import software.wings.beans.alert.ArtifactCollectionFailedAlert;
import software.wings.beans.artifact.Artifact;
import software.wings.beans.artifact.ArtifactStream;
import software.wings.beans.artifact.ArtifactView;
import software.wings.security.PermissionAttribute.Action;
import software.wings.security.PermissionAttribute.PermissionType;
import software.wings.security.annotations.AuthRule;
import software.wings.service.intfc.AlertService;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.ArtifactService;
import software.wings.service.intfc.ArtifactStreamService;
import software.wings.service.intfc.PermitService;
import software.wings.service.intfc.ServiceResourceService;

import com.codahale.metrics.annotation.ExceptionMetered;
import com.codahale.metrics.annotation.Timed;
import com.google.inject.Inject;
import io.swagger.annotations.Api;
import java.io.File;
import javax.ws.rs.BeanParam;
import javax.ws.rs.DELETE;
import javax.ws.rs.Encoded;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;
import org.apache.commons.lang3.StringUtils;

/**
 * ArtifactResource.
 *
 * @author Rishi
 */
@Api("artifacts")
@Path("/artifacts")
@Produces("application/json")
@OwnedBy(HarnessTeam.CDC)
public class ArtifactResource {
  private ArtifactService artifactService;
  private ArtifactStreamService artifactStreamService;
  private PermitService permitService;
  private AppService appService;
  private AlertService alertService;
  private ServiceResourceService serviceResourceService;

  /**
   * Instantiates a new artifact resource.
   *
   * @param artifactService the artifact service
   * @param artifactStreamService
   */
  @Inject
  public ArtifactResource(ArtifactService artifactService, ArtifactStreamService artifactStreamService,
      PermitService permitService, AppService appService, AlertService alertService) {
    this.artifactService = artifactService;
    this.artifactStreamService = artifactStreamService;
    this.permitService = permitService;
    this.appService = appService;
    this.alertService = alertService;
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
  public RestResponse<PageResponse<Artifact>> list(@QueryParam("appId") String appId,
      @QueryParam("accountId") String accountId, @QueryParam("routingId") String routingId,
      @QueryParam("serviceId") String serviceId, @BeanParam PageRequest<Artifact> pageRequest) {
    pageRequest.addFilter("appId", EQ, appId);
    if (StringUtils.isNoneBlank(accountId, routingId)) {
      pageRequest.addFilter("accountId", EQ, StringUtils.isNotBlank(accountId) ? accountId : routingId);
    }
    return new RestResponse<>(artifactService.listArtifactsForService(appId, serviceId, pageRequest));
  }

  /**
   * List artifacts for artifact streams having collection enabled.
   *
   * @param appId       the app id
   * @param pageRequest the page request
   * @return the rest response
   */
  @GET
  @Path("/collection-enabled-artifacts")
  @Timed
  @ExceptionMetered
  public RestResponse<PageResponse<Artifact>> listArtifactsWithCollectionEnabled(@QueryParam("appId") String appId,
      @QueryParam("accountId") String accountId, @QueryParam("routingId") String routingId,
      @QueryParam("serviceId") String serviceId, @BeanParam PageRequest<Artifact> pageRequest) {
    pageRequest.addFilter("appId", EQ, appId);
    if (StringUtils.isNoneBlank(accountId, routingId)) {
      pageRequest.addFilter("accountId", EQ, StringUtils.isNotBlank(accountId) ? accountId : routingId);
    }
    return new RestResponse<>(
        artifactService.listArtifactsForServiceWithCollectionEnabled(appId, serviceId, pageRequest));
  }

  @GET
  @Path("/v2")
  @Timed
  @ExceptionMetered
  public RestResponse<PageResponse<Artifact>> listArtifactsByServiceId(@QueryParam("serviceId") String serviceId,
      @QueryParam("accountId") String accountId, @BeanParam PageRequest<Artifact> pageRequest) {
    if (StringUtils.isNotBlank(accountId)) {
      pageRequest.addFilter("accountId", EQ, accountId);
    } else if (serviceId != null) {
      Service service = serviceResourceService.get(serviceId);
      if (service != null) {
        String accountIdentifier = service.getAccountId();
        pageRequest.addFilter("accountId", EQ, accountIdentifier);
      }
    }
    return new RestResponse<>(artifactService.listArtifactsForService(serviceId, pageRequest));
  }

  /**
   * Gets the.
   *
   * @param appId      the app id
   * @param artifactId the artifact id
   * @return the rest response
   */
  @GET
  @Path("{artifactId}")
  @Timed
  @ExceptionMetered
  public RestResponse<ArtifactView> get(@QueryParam("appId") String appId, @PathParam("artifactId") String artifactId) {
    return new RestResponse<>(artifactService.getWithServices(artifactId, appId));
  }

  /**
   * Save.
   *
   * @param appId    the app id
   * @param artifact the artifact
   * @return the rest response
   */
  @POST
  @Timed
  @ExceptionMetered
  public RestResponse<Artifact> save(@QueryParam("appId") String appId, Artifact artifact) {
    artifact.setAppId(appId);
    ArtifactStream artifactStream = artifactStreamService.get(artifact.getArtifactStreamId());
    artifact.setDisplayName(artifactStream.fetchArtifactDisplayName(artifact.getBuildNo()));
    Artifact savedArtifact = artifactService.create(artifact);
    if (artifactStream.getFailedCronAttempts() != 0) {
      artifactStreamService.updateFailedCronAttemptsAndLastIteration(
          artifactStream.getAccountId(), artifact.getArtifactStreamId(), 0, false);
      permitService.releasePermitByKey(artifactStream.getUuid());
      alertService.closeAlert(artifactStream.getAccountId(), null, AlertType.ARTIFACT_COLLECTION_FAILED,
          ArtifactCollectionFailedAlert.builder().artifactStreamId(artifactStream.getUuid()).build());
    }
    return new RestResponse<>(savedArtifact);
  }

  /**
   * Update.
   *
   * @param appId      the app id
   * @param artifactId the artifact id
   * @param artifact   the artifact
   * @return the rest response
   */
  @PUT
  @Path("{artifactId}")
  @Timed
  @ExceptionMetered
  @AuthRule(permissionType = PermissionType.SERVICE, action = Action.READ, skipAuth = true)
  public RestResponse<Artifact> update(
      @QueryParam("appId") String appId, @PathParam("artifactId") String artifactId, Artifact artifact) {
    artifact.setUuid(artifactId);
    artifact.setAppId(appId);
    return new RestResponse<>(artifactService.update(artifact));
  }

  /**
   * Update.
   *
   * @param appId      the app id
   * @param artifactId the artifact id
   * @return the rest response
   */
  @DELETE
  @Path("{artifactId}")
  @Timed
  @ExceptionMetered
  public RestResponse delete(@QueryParam("appId") String appId, @PathParam("artifactId") String artifactId) {
    String accountId = appService.getAccountIdByAppId(appId);
    artifactService.delete(accountId, artifactId);
    return new RestResponse();
  }

  /**
   * Download.
   *
   * @param appId      the app id
   * @param artifactId the artifact id
   * @return the response
   */
  @GET
  @Path("{artifactId}/artifactFile")
  @Encoded
  @Timed
  @ExceptionMetered
  @AuthRule(skipAuth = true)
  public Response download(@QueryParam("appId") String appId, @PathParam("artifactId") String artifactId) {
    String accountId = appService.getAccountIdByAppId(appId);
    File artifactFile = artifactService.download(accountId, artifactId);
    ResponseBuilder response = Response.ok(artifactFile, MediaType.APPLICATION_OCTET_STREAM);
    response.header("Content-Disposition", "attachment; filename=" + artifactFile.getName());
    return response.build();
  }

  /**
   * Save.
   * @param appId    the app id
   * @param artifact the artifact
   * @return the rest response
   */
  @PUT
  @Timed
  @ExceptionMetered
  public RestResponse<Artifact> collectArtifactContent(@QueryParam("appId") String appId, Artifact artifact) {
    artifact.setAppId(appId);
    return new RestResponse<>(
        artifactService.startArtifactCollection(appService.getAccountIdByAppId(appId), artifact.getUuid()));
  }
}
