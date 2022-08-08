/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm;

import static io.harness.annotations.dev.HarnessTeam.CE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.UnexpectedException;
import io.harness.ng.core.dto.ResponseDTO;

import com.google.inject.Singleton;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import retrofit2.Call;
import retrofit2.Response;

@Singleton
@Slf4j
@OwnedBy(CE)
public class RestCallToCENGClientUtils {
  public static <T> T execute(Call<ResponseDTO<T>> request) {
    try {
      Response<ResponseDTO<T>> responseDTO = request.execute();
      log.info("ResponseDTO raw is {}", responseDTO.raw());
      if (responseDTO.body() != null) {
        return responseDTO.body().getData();
      }
      return (T) responseDTO;
    } catch (IOException ex) {
      log.error("IO error while connecting to ceng", ex);
      throw new UnexpectedException("Unable to connect, please try again.");
    }
  }
}
