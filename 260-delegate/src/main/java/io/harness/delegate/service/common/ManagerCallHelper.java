/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.service.common;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import okhttp3.internal.http2.StreamResetException;
import retrofit2.Call;
import retrofit2.Response;

import java.io.IOException;

import java.util.function.Consumer;

@Slf4j
@UtilityClass
public class ManagerCallHelper {
    private static final int MAX_ATTEMPTS = 3;
    public <T> T executeAcquireCallWithRetry(Call<T> call, String failureMessage, Consumer<Response<T>> handler) throws IOException {
        Response<T> response = null;
        try {
            response = executeCallWithRetryableException(call, failureMessage);
            return response.body();
        } catch (Exception e) {
            log.error("error executing rest call", e);
            throw e;
        } finally {
            handler.accept(response);
        }
    }

    private <T> Response<T> executeCallWithRetryableException(Call<T> call, String failureMessage) throws IOException {
        T responseBody = null;
        Response<T> response = null;
        int attempt = 1;
        while (attempt <= MAX_ATTEMPTS && responseBody == null) {
            try {
                response = call.clone().execute();
                responseBody = response.body();
                if (responseBody == null) {
                    attempt ++;
                }
            } catch (Exception exception) {
                if (exception instanceof StreamResetException && attempt < MAX_ATTEMPTS) {
                    attempt++;
                    log.warn(String.format("%s : Attempt: %d", failureMessage, attempt));
                } else {
                    throw exception;
                }
            }
        }
        return response;
    }

    public <T> T executeRestCall(Call<T> call, Consumer<Response<T>> handler) throws IOException {
        Response<T> response = null;
        try {
            response = call.execute();
            return response.body();
        } catch (Exception e) {
            log.error("error executing rest call", e);
            throw e;
        } finally {
            handler.accept(response);
        }
    }
}
