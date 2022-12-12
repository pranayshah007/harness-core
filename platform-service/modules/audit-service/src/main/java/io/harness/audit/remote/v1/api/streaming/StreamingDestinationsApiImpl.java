/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.audit.remote.v1.api.streaming;

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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

@OwnedBy(HarnessTeam.PL)
@AllArgsConstructor(onConstructor = @__({ @Inject }))
public class StreamingDestinationsApiImpl implements StreamingDestinationsApi {
  private final StreamingService streamingService;
  private final StreamingDestinationsApiUtils streamingDestinationsApiUtils;

  @Override
  public Response createStreamingDestinations(@Valid StreamingDestinationDTO body, String harnessAccount) {
    StreamingDestination streamingDestination = streamingService.create(harnessAccount, body);
    return Response.status(Response.Status.CREATED)
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
}
