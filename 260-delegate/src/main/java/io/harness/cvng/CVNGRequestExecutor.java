/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import retrofit2.Call;
import retrofit2.Response;

@Singleton
@TargetModule(HarnessModule._420_DELEGATE_AGENT)
public class CVNGRequestExecutor {
  private static final int MAX_RETRIES = 2;
  @Inject @Named("verificationDataCollectorCVNGCallExecutor") private ExecutorService executorService;
  public <U> U executeWithTimeout(Call<U> request, Duration timeout) {
    Callable<U> task = () -> executeRequest(request);
    Future<U> future = executorService.submit(task);
    try {
      return future.get(timeout.getSeconds(), TimeUnit.SECONDS);
    } catch (TimeoutException ex) {
      request.cancel();
      throw new IllegalStateException(ex);
    } catch (ExecutionException e) {
      throw new IllegalStateException(e.getCause());
    } catch (InterruptedException e) {
      throw new IllegalStateException(e);
    }
  }

  public <U> U execute(Call<U> request) {
    return executeWithTimeout(request, Duration.ofSeconds(10));
  }

  /**
   * Only use this when request failure does not have any side effects
   */
  public <U> U executeWithRetry(Call<U> request) {
    int retryCount = 0;
    while (true) {
      try {
        return executeWithTimeout(request.clone(), Duration.ofSeconds(10));
      } catch (Exception e) {
        if (retryCount == MAX_RETRIES) {
          throw e;
        }
      }
      retryCount++;
    }
  }
  private <U> U executeRequest(Call<U> request) {
    try {
      Response<U> response = request.clone().execute();
      if (response.isSuccessful()) {
        return response.body();
      } else {
        String errorBody = response.errorBody() != null ? response.errorBody().string() : "";
        throw new IllegalStateException("Response Code: " + response.code()
            + ", Response Message: " + response.message() + ", Error Body: " + errorBody);
      }
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }
}
