/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.client;

import io.harness.datacollection.utils.DataCollectionUtils;
import io.harness.eraro.ErrorCode;
import io.harness.eraro.ResponseMessage;
import io.harness.rest.RestResponse;
import io.harness.serializer.JsonUtils;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.List;
import javax.ws.rs.BadRequestException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import retrofit2.Call;
import retrofit2.Response;

@Singleton
@Slf4j
public class RequestExecutor {
  // TODO: enhance it to handle exceptions, stacktraces and retries based on response code and exception from manager.
  public <U> U execute(Call<U> request) {
    try {
      Response<U> response = request.clone().execute();
      if (response.isSuccessful()) {
        return response.body();
      } else {
        int code = response.code();
        String errorBody = DataCollectionUtils.getErrorBodyString(response);
        tryParsingErrorFromRestResponse(code, errorBody);
        throw new ServiceCallException(response.code(), response.message(), errorBody);
      }
    } catch (IOException e) {
      throw new ServiceCallException(e);
    }
  }

  private void tryParsingErrorFromRestResponse(int code, String errorBody) {
    // Try to parse manager response format - io.harness.cvng.exception.GenericExceptionMapper
    // If resource has annotation @ExposeInternalException(withStackTrace = true) we will also get
    // stacktrace from manager.
    // Add @ExposeInternalException(withStackTrace = true) if you want to get stacktrace of failure.
    RestResponse<?> restResponse;
    try {
      restResponse = (RestResponse<?>) JsonUtils.asObject(errorBody, RestResponse.class);
    } catch (RuntimeException e) {
      // ignore if json can not be parsed to RestResponse
      return;
    }
    List<ResponseMessage> responseMessages = restResponse.getResponseMessages();
    if (CollectionUtils.isNotEmpty(responseMessages)) {
      ResponseMessage responseMessage = responseMessages.get(0);
      if (ErrorCode.DATA_COLLECTION_ERROR.equals(responseMessage.getCode())) {
        throw new ServiceCallException(
            responseMessage.getCode(), code, responseMessages.get(0).getMessage(), errorBody, responseMessages);
      } else {
        throw new ServiceCallException(
            ErrorCode.UNKNOWN_ERROR, code, responseMessages.get(0).getMessage(), errorBody, responseMessages);
      }
    } else {
      String errorMessage;
      try {
        JsonParser jsonParser = new JsonParser();
        JsonObject jsonObject = (JsonObject) jsonParser.parse(errorBody);
        errorMessage = jsonObject.get("message").getAsString();
      } catch (Exception e) {
        return;
      }
      if (code == 400) {
        throw new BadRequestException(errorMessage);
      }
    }
  }
}
