/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.remote;

import io.harness.beans.sweepingoutputs.StageInfraDetails;
import io.harness.ci.beans.entities.CIExecutionImages;
import io.harness.ng.core.dto.ResponseDTO;

import com.google.inject.Singleton;
import okhttp3.Request;
import org.jetbrains.annotations.NotNull;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

@Singleton
public class NoOpCiResourceClient implements CiServiceResourceClient {
  @Override
  public Call<ResponseDTO<CIExecutionImages>> getCustomersExecutionConfig(
      StageInfraDetails.Type infra, boolean overridesOnly, String accountIdentifier) {
    return getNoOpCall();
  }

  private static Call<ResponseDTO<CIExecutionImages>> getNoOpCall() {
    return new Call<>() {
      @NotNull
      @Override
      public Response<ResponseDTO<CIExecutionImages>> execute() {
        return Response.success(ResponseDTO.newResponse(CIExecutionImages.builder().build()));
      }

      public void enqueue(Callback<ResponseDTO<CIExecutionImages>> callback) {}

      @Override
      public boolean isExecuted() {
        return true;
      }

      @Override
      public void cancel() {}

      @Override
      public boolean isCanceled() {
        return false;
      }

      @Override
      public Call<ResponseDTO<CIExecutionImages>> clone() {
        return null;
      }

      @Override
      public Request request() {
        return null;
      }
    };
  }
}
