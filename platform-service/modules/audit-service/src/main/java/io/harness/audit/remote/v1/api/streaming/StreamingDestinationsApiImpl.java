/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.audit.remote.v1.api.streaming;

import static io.harness.audit.remote.v1.api.streaming.StreamingDestinationPermissions.DELETE_STREAMING_DESTINATION_PERMISSION;
import static io.harness.audit.remote.v1.api.streaming.StreamingDestinationPermissions.EDIT_STREAMING_DESTINATION_PERMISSION;
import static io.harness.audit.remote.v1.api.streaming.StreamingDestinationPermissions.VIEW_STREAMING_DESTINATION_PERMISSION;
import static io.harness.audit.remote.v1.api.streaming.StreamingDestinationResourceTypes.STREAMING_DESTINATION;

import io.harness.accesscontrol.acl.api.Resource;
import io.harness.accesscontrol.acl.api.ResourceScope;
import io.harness.accesscontrol.clients.AccessControlClient;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.audit.api.streaming.StreamingService;
import io.harness.audit.entities.streaming.StreamingDestination;
import io.harness.audit.entities.streaming.StreamingDestinationFilterProperties;
import io.harness.spec.server.audit.v1.StreamingDestinationsApi;
import io.harness.spec.server.audit.v1.model.StreamingDestinationDTO;
import io.harness.spec.server.audit.v1.model.StreamingDestinationResponse;
import io.harness.utils.ApiUtils;

import com.google.inject.Inject;
import java.util.List;
import javax.validation.Valid;
import javax.validation.constraints.Max;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

@OwnedBy(HarnessTeam.PL)
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
public class StreamingDestinationsApiImpl implements StreamingDestinationsApi {
  private final StreamingService streamingService;
  private final StreamingDestinationsApiUtils streamingDestinationsApiUtils;
  private final AccessControlClient accessControlClient;

  @Override
  public Response createStreamingDestinations(@Valid StreamingDestinationDTO body, String harnessAccount) {
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(harnessAccount, null, null),
        Resource.of(STREAMING_DESTINATION, null), EDIT_STREAMING_DESTINATION_PERMISSION);

    StreamingDestination streamingDestination = streamingService.create(harnessAccount, body);

    log.info(String.format(
        "Streaming destination with identifier [%s] is successfully created", streamingDestination.getIdentifier()));
    return Response.status(Response.Status.CREATED)
        .entity(streamingDestinationsApiUtils.getStreamingDestinationResponse(streamingDestination))
        .build();
  }

  @Override
  public Response deleteDisabledStreamingDestination(String streamingDestinationIdentifier, String harnessAccount) {
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(harnessAccount, null, null),
        Resource.of(STREAMING_DESTINATION, streamingDestinationIdentifier), DELETE_STREAMING_DESTINATION_PERMISSION);

    streamingService.delete(harnessAccount, streamingDestinationIdentifier);

    log.info(String.format(
        "Streaming destination with identifier [%s] is successfully deleted", streamingDestinationIdentifier));
    return Response.status(Response.Status.NO_CONTENT).build();
  }

  @Override
  public Response getStreamingDestination(String streamingDestinationIdentifier, String harnessAccount) {
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(harnessAccount, null, null),
        Resource.of(STREAMING_DESTINATION, streamingDestinationIdentifier), VIEW_STREAMING_DESTINATION_PERMISSION);

    StreamingDestination streamingDestination =
        streamingService.getStreamingDestination(harnessAccount, streamingDestinationIdentifier);

    return Response.status(Response.Status.OK)
        .entity(streamingDestinationsApiUtils.getStreamingDestinationResponse(streamingDestination))
        .build();
  }

  @Override
  public Response getStreamingDestinations(String harnessAccount, Integer page, @Max(100L) Integer limit, String sort,
      String order, String searchTerm, String status) {
    StreamingDestinationFilterProperties streamingDestinationFilterProperties =
        streamingDestinationsApiUtils.getFilterProperties(searchTerm, status);
    Pageable pageable = streamingDestinationsApiUtils.getPageRequest(page, limit, sort, order);
    Page<StreamingDestination> streamingDestinationPage =
        streamingService.list(harnessAccount, pageable, streamingDestinationFilterProperties);
    Page<StreamingDestinationResponse> streamingDestinationResponsePage =
        streamingDestinationPage.map(streamingDestinationsApiUtils::getStreamingDestinationResponse);
    List<StreamingDestinationResponse> streamingDestinations = streamingDestinationResponsePage.getContent();
    ResponseBuilder responseBuilder = Response.ok();
    ResponseBuilder responseBuilderWithLinks = ApiUtils.addLinksHeader(
        responseBuilder, "v1/streaming-destinations", streamingDestinations.size(), page, limit);

    return responseBuilderWithLinks.entity(streamingDestinations).build();
  }

  @Override
  public Response updateStreamingDestination(String streamingDestinationIdentifier,
      @Valid StreamingDestinationDTO streamingDestinationDTO, String harnessAccount) {
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(harnessAccount, null, null),
        Resource.of(STREAMING_DESTINATION, streamingDestinationIdentifier), EDIT_STREAMING_DESTINATION_PERMISSION);

    StreamingDestination streamingDestination =
        streamingService.update(streamingDestinationIdentifier, streamingDestinationDTO, harnessAccount);

    log.info(String.format(
        "Streaming destination with identifier [%s] is successfully updated", streamingDestinationIdentifier));
    return Response.status(Response.Status.OK)
        .entity(streamingDestinationsApiUtils.getStreamingDestinationResponse(streamingDestination))
        .build();
  }
}
