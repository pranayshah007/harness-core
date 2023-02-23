/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.remote.client;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.UnexpectedException;
import io.harness.ng.core.dto.ErrorDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.serializer.JsonUtils;
import io.harness.serializer.KryoSerializer;

import com.fasterxml.jackson.core.type.TypeReference;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.FailsafeException;
import net.jodah.failsafe.RetryPolicy;
import okhttp3.MediaType;
import org.apache.commons.lang3.StringUtils;
import retrofit2.Call;
import retrofit2.Response;

@OwnedBy(PL)
@Singleton
@Slf4j
public class NGRestClientExecutor {
  public static final String DEFAULT_ERROR_MESSAGE = "Error occurred while performing this operation.";
  private static final MediaType APPLICATION_KRYO_MEDIA_TYPE = MediaType.parse("application/x-kryo");
  @Inject private KryoSerializer kryoSerializer;

  public <T> T getResponse(Call<ResponseDTO<T>> request) {
    RetryPolicy<Response<ResponseDTO<T>>> retryPolicy = RestClientUtils.getRetryPolicy("Request failed");
    try {
      Response<ResponseDTO<T>> response = Failsafe.with(retryPolicy).get(() -> executeRequest(request));
      return handleResponse(response);
    } catch (FailsafeException ex) {
      throw new UnexpectedException(DEFAULT_ERROR_MESSAGE, ex.getCause());
    }
  }

  private static <T> Response<ResponseDTO<T>> executeRequest(Call<ResponseDTO<T>> request) throws IOException {
    try {
      Call<ResponseDTO<T>> cloneRequest = request.clone();
      return cloneRequest == null ? request.execute() : cloneRequest.execute();
    } catch (IOException ioException) {
      String url = Optional.ofNullable(request.request()).map(x -> x.url().encodedPath()).orElse(null);
      log.error("IO error while connecting to the service: {}", url, ioException);
      throw ioException;
    }
  }

  private <T> T handleResponse(Response<ResponseDTO<T>> response) {
    if (response.isSuccessful()) {
      return response.body().getData();
    } else {
      String errorMessage = "";
      try {
        ErrorDTO errorDTO;
        if (response.errorBody().contentType().toString().startsWith(APPLICATION_KRYO_MEDIA_TYPE.toString())) {
          byte[] bytes = response.errorBody().bytes();
          errorDTO = (ErrorDTO) kryoSerializer.asObject(bytes);
        } else {
          errorDTO = JsonUtils.asObject(response.errorBody().string(), new TypeReference<ErrorDTO>() {});
        }
        if (errorDTO != null) {
          errorMessage = errorDTO.getMessage();
        }
      } catch (Exception e) {
        log.debug("Error while converting error received from upstream systems", e);
      }
      throw new InvalidRequestException(StringUtils.isEmpty(errorMessage) ? DEFAULT_ERROR_MESSAGE : errorMessage);
    }
  }
}
