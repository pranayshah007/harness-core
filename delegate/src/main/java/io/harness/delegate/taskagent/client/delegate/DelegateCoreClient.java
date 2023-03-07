/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.taskagent.client.delegate;

import io.harness.delegate.beans.DelegateTaskResponse;
import io.harness.serializer.kryo.KryoRequest;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.Headers;
import retrofit2.http.POST;
import retrofit2.http.Path;

public interface DelegateCoreClient {
  @Headers({"Content-Type: application/x-kryo-v2"})
  @POST("task/{taskId}/execution-response")
  @KryoRequest
  Call<Void> taskResponse(@Path("taskId") String taskId, @Body DelegateTaskResponse delegateTaskResponse);
}
