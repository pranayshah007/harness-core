/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.audit.client.remote.streaming;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.spec.server.audit.v1.model.StreamingDestinationDTO;
import io.harness.spec.server.audit.v1.model.StreamingDestinationResponse;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.Header;
import retrofit2.http.PUT;
import retrofit2.http.Path;

@OwnedBy(HarnessTeam.PL)
public interface StreamingDestinationClient {
  String STREAMING_DESTINATION_API = "v1/streaming-destinations";

  @PUT(STREAMING_DESTINATION_API + "/{streaming-destination}")
  Call<StreamingDestinationResponse> updateStreamingDestination(
      @Path("streaming-destination") String streamingDestinationIdentifier,
      @Body StreamingDestinationDTO streamingDestinationDTO, @Header("Harness-Account") String accountIdentifier);
}
