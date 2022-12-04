/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.hsqs.client;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.hsqs.client.model.AckRequest;
import io.harness.hsqs.client.model.AckResponse;
import io.harness.hsqs.client.model.DequeueRequest;
import io.harness.hsqs.client.model.DequeueResponse;
import io.harness.hsqs.client.model.EnqueueRequest;
import io.harness.hsqs.client.model.EnqueueResponse;
import io.harness.hsqs.client.model.UnAckRequest;
import io.harness.hsqs.client.model.UnAckResponse;

import java.util.List;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.Header;
import retrofit2.http.POST;

@OwnedBy(PIPELINE)
public interface HsqsServiceClient {
  String V1_ENDPOINT = "v1/";

  @POST(V1_ENDPOINT + "queue/")
  Call<EnqueueResponse> enqueue(@Body EnqueueRequest enqueueRequest, @Header("Authorization") String auth);

  @POST(V1_ENDPOINT + "dequeue")
  Call<List<DequeueResponse>> dequeue(@Body DequeueRequest dequeueRequest, @Header("Authorization") String auth);

  @POST(V1_ENDPOINT + "ack") Call<AckResponse> ack(@Body AckRequest ackRequest, @Header("Authorization") String auth);

  @POST(V1_ENDPOINT + "nack")
  Call<UnAckResponse> unack(@Body UnAckRequest unAckRequest, @Header("Authorization") String auth);
}
