package io.harness.util;

import static io.harness.annotations.dev.HarnessTeam.DEL;

import io.harness.annotations.dev.OwnedBy;

import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import retrofit2.Call;
import retrofit2.Response;

@OwnedBy(DEL)
@Slf4j
public class DelegateRestUtils {
  public static <T> T executeRestCall(Call<T> call) throws IOException {
    Response<T> response = null;
    try {
      response = call.execute();
      if (response.isSuccessful()) {
        return response.body();
      }
    } catch (Exception e) {
      log.error("error executing rest call", e);
      throw e;
    } finally {
      if (response != null && !response.isSuccessful()) {
        String errorResponse = response.errorBody().string();
        final int errorCode = response.code();
        log.warn("Call received {} Error Response: {}", errorCode, errorResponse);
        response.errorBody().close();
      }
    }
    return null;
  }
}
