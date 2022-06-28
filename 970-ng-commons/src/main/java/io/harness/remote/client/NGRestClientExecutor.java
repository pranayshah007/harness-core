/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.remote.client;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.remote.client.RestClientUtils.DEFAULT_CONNECTION_ERROR_MESSAGE;
import static io.harness.remote.client.RestClientUtils.DEFAULT_ERROR_MESSAGE;

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
import okhttp3.MediaType;
import org.apache.commons.lang3.StringUtils;
import retrofit2.Call;
import retrofit2.Response;

@OwnedBy(PL)
@Singleton
@Slf4j
public class NGRestClientExecutor {
  private static final MediaType APPLICATION_KRYO_MEDIA_TYPE = MediaType.parse("application/x-kryo");
  @Inject private KryoSerializer kryoSerializer;

  public <T> T getResponse(Call<ResponseDTO<T>> request) {
    return getResponse(request, DEFAULT_ERROR_MESSAGE, DEFAULT_CONNECTION_ERROR_MESSAGE);
  }

  public <T> T getResponse(Call<ResponseDTO<T>> request, String defaultErrorMessage) {
    return getResponse(request, defaultErrorMessage, DEFAULT_CONNECTION_ERROR_MESSAGE);
  }

  public <T> T getResponse(Call<ResponseDTO<T>> request, String defaultErrorMessage, String connectionErrorMessage) {
    try {
      Response<ResponseDTO<T>> response = request.execute();
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
        throw new InvalidRequestException(StringUtils.isEmpty(errorMessage) ? defaultErrorMessage : errorMessage);
      }
    } catch (IOException ex) {
      String url = Optional.ofNullable(request.request()).map(x -> x.url().encodedPath()).orElse(null);
      log.error("IO error while connecting to the service: {}", url, ex);
      throw new UnexpectedException(connectionErrorMessage);
    }
  }
}
