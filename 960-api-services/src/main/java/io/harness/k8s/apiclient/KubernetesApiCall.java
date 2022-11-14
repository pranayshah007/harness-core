/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.k8s.apiclient;

import static java.lang.String.format;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.ExplanationException;
import io.harness.exception.sanitizer.ExceptionMessageSanitizer;

import com.google.gson.reflect.TypeToken;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import java.lang.reflect.Type;
import lombok.experimental.UtilityClass;
import okhttp3.Call;
import okhttp3.Request;

@OwnedBy(HarnessTeam.CDP)
@UtilityClass
public class KubernetesApiCall {
  @FunctionalInterface
  public interface ApiCallSupplier {
    Call get() throws ApiException;
  }

  public <T> T call(ApiClient apiClient, ApiCallSupplier callSupplier) {
    Request request = null;
    try {
      Call call = callSupplier.get();
      request = call.request();
      Type varReturnType = (new TypeToken<T>() {}).getType();
      return apiClient.<T>execute(call, varReturnType).getData();
    } catch (ApiException e) {
      e = ExceptionMessageSanitizer.sanitizeException(e);
      if (request != null) {
        String explanation;
        if (e.getCode() == 0) {
          explanation = format("Connection failed on HTTP API call %s %s with message: %s", request.method(),
              request.url(), e.getMessage());
        } else {
          explanation = format("HTTP API call %s %s failed with error code %d and response body: %s", request.method(),
              request.url(), e.getCode(), e.getResponseBody());
        }
        throw new ExplanationException(explanation, e);
      }

      throw new ExplanationException(
          format("Failed to create a kubernetes request due to error: %s", e.getMessage()), e);
    } catch (Exception ex) {
      ex = ExceptionMessageSanitizer.sanitizeException(ex);
      throw new ExplanationException(format("K8s HTTP API call failed due to error: %s", ex.getMessage()), ex);
    }
  }
}
