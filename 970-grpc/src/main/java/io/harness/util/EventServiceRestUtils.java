/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.util;

import static io.harness.annotations.dev.HarnessTeam.CE;

import io.harness.annotations.dev.OwnedBy;

import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import retrofit2.Call;
import retrofit2.Response;

@OwnedBy(CE)
@Slf4j
public class EventServiceRestUtils {
  public static <T> T executeRestCall(Call<T> call) throws IOException {
    try {
      Response<T> response = call.execute();
      log.info("Rest call to CE Event Service isSuccessful: {}", response.isSuccessful());
      if (response.isSuccessful()) {
        return response.body();
      } else {
        String errorResponse = (response.errorBody() != null) ? response.errorBody().string() : "";
        final int errorCode = response.code();
        throw new IOException(
            String.format("CE Event Rest call received %d Error Response: %s", errorCode, errorResponse));
      }
    } catch (Exception e) {
      log.error("Error executing rest call", e);
      throw new IOException(e);
    }
  }
}
