/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.azure;

import static java.lang.String.format;

import io.harness.azure.model.AzureAPIError;
import io.harness.azure.model.AzureConfig;
import io.harness.azure.utility.AzureUtils;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.AzureAuthenticationException;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.NestedExceptionUtils;
import io.harness.exception.WingsException;
import io.harness.exception.runtime.azure.AzureClientRuntimeException;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.azure.core.exception.ClientAuthenticationException;
import com.azure.core.http.rest.PagedResponse;
import com.azure.core.http.rest.PagedResponseBase;
import com.azure.core.management.implementation.serializer.AzureJacksonAdapter;
import com.azure.core.util.FluxUtil;
import com.azure.core.util.paging.ContinuablePage;
import com.azure.core.util.serializer.SerializerEncoding;
import com.google.inject.Singleton;
import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.interfaces.RSAPrivateKey;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import retrofit2.Response;

@Singleton
@Slf4j
public class AzureClientBase {
  protected AzureJacksonAdapter serializerAdapter;
  protected SerializerEncoding serializerEncoding;

  public AzureClientBase() {
    serializerAdapter = new AzureJacksonAdapter();
    serializerEncoding = SerializerEncoding.JSON;
  }

  protected <T extends ContinuablePage, B> Mono<PagedResponse<B>> executePagedRequest(
      Mono<Response<T>> request, Class<T> responseType) {
    return FluxUtil
        .withContext(context -> {
          Response<T> response = request.block();

          T result = processResponse(response, responseType);

          List<B> itemsList = (List) result.getElements().stream().collect(Collectors.toList());

          String continuationToken = null;
          if (result.getContinuationToken() != null) {
            continuationToken = String.valueOf(result.getContinuationToken());
          }

          return Mono.just(new PagedResponseBase(null, response.code(), null, itemsList, continuationToken, null));
        })
        .onErrorResume(error -> {
          if (error instanceof AzureClientRuntimeException) {
            return Mono.error(error);
          }
          return Mono.error(new AzureClientRuntimeException(error.getMessage(), error));
        })
        .map(res -> {
          return new PagedResponseBase(res.getRequest(), res.getStatusCode(), res.getHeaders(), res.getValue(),
              res.getContinuationToken(), null);
        });
  }

  protected <T> Mono<T> getMonoRequest(Mono<Response<T>> request, Class<T> responseType) {
    return request.onErrorResume(error -> Mono.error(error))
        .flatMap(response -> getMonoResponse(response, responseType));
  }

  private <T> Mono<T> getMonoResponse(Response<T> response, Class<T> responseType) {
    validateResponse(response);

    if (response.isSuccessful()) {
      return Mono.just(processSuccessfulResponse(response, responseType));
    }

    return Mono.just(processUnsuccessfulResponse(response));
  }

  private <T> T processResponse(Response<T> response, Class<T> responseType) {
    validateResponse(response);

    if (response.isSuccessful()) {
      return processSuccessfulResponse(response, responseType);
    }

    return processUnsuccessfulResponse(response);
  }

  private void validateResponse(Response response) {
    if (response == null) {
      throw new AzureClientRuntimeException("REST response received was NULL");
    }
  }

  private <T> T processSuccessfulResponse(Response<T> response, Class<T> responseType) {
    log.info(format("Request: [%s %s] was successful: %d", response.raw().request().method(),
        response.raw().request().url(), response.code()));
    return responseType.cast(response.body());
  }

  private <T> T processUnsuccessfulResponse(Response<T> response) {
    try {
      log.error(format("Request: [%s %s] was unsuccessful: %d", response.raw().request().method(),
          response.raw().request().url(), response.code()));

      if (response.errorBody() != null) {
        String exceptionMsg = response.errorBody().string();
        if (EmptyPredicate.isNotEmpty(exceptionMsg)) {
          log.error(format("Error response: %s", exceptionMsg));
          try {
            AzureAPIError azureAPIError =
                serializerAdapter.deserialize(exceptionMsg, AzureAPIError.class, serializerEncoding);
            throw new AzureClientRuntimeException(azureAPIError.getError().getMessage());
          } catch (IOException e) {
            throw new AzureClientRuntimeException(exceptionMsg);
          }
        }
      }

      throw new AzureClientRuntimeException(processResponseStatusCode(response));

    } catch (ClassCastException | IOException e) {
      log.error(format("Error occurred: %s", e.getMessage()), e);
      throw new AzureClientRuntimeException("There was an issue with parsing the response from Azure.");
    }
  }

  private String processResponseStatusCode(Response response) {
    StringBuffer stringer = new StringBuffer(200);
    stringer.append(format("Request was unsuccessful. Response code: [%d]. ", response.code()));

    if (response.code() == 401) {
      stringer.append("Check if the provided Azure credentials are correct.");
    }

    if (response.code() == 400) {
      stringer.append("Request made was invalid.");
    }

    if (response.code() == 403) {
      stringer.append("Check if the provided Azure credentials have appropriate permissions.");
    }

    if (response.code() == 404) {
      stringer.append("Could not find the resource.");
    }

    return stringer.toString();
  }

  protected void handleAzureAuthenticationException(Exception e) {
    String message = null;

    Throwable e1 = e;
    while (e1.getCause() != null) {
      e1 = e1.getCause();
      if (e1 instanceof ClientAuthenticationException || e1 instanceof InvalidKeyException) {
        message = "Invalid Azure credentials." + e1.getMessage();
      }
      if (e1 instanceof InterruptedException) {
        message = "Failed to connect to Azure cluster. " + ExceptionUtils.getMessage(e1);
      }
    }

    if (EmptyPredicate.isEmpty(message)) {
      message = e.getMessage();
    }

    throw NestedExceptionUtils.hintWithExplanationException("Check your Azure credentials",
        "Failed to connect to Azure", new AzureAuthenticationException(message, WingsException.USER, null));
  }

  protected String createClientAssertion(AzureConfig azureConfig) {
    String certThumbprintInBase64 = AzureUtils.getCertificateThumbprintBase64Encoded(azureConfig.getCert());
    RSAPrivateKey privateKey = AzureUtils.getPrivateKeyFromPEMFile(azureConfig.getCert());

    Algorithm algorithm = Algorithm.RSA256(privateKey);

    Map<String, Object> headers = new HashMap<>();
    headers.put("x5t", certThumbprintInBase64);

    long currentTimestamp = System.currentTimeMillis();
    return JWT.create()
        .withHeader(headers)
        .withAudience(format("%s%s/oauth2/v2.0/token",
            AzureUtils.getAzureEnvironment(azureConfig.getAzureEnvironmentType()).getActiveDirectoryEndpoint(),
            azureConfig.getTenantId()))
        .withIssuer(azureConfig.getClientId())
        .withIssuedAt(new Date(currentTimestamp))
        .withNotBefore(new Date(currentTimestamp))
        .withExpiresAt(new Date(currentTimestamp + 10 * 60 * 1000))
        .withJWTId(UUID.randomUUID().toString())
        .withSubject(azureConfig.getClientId())
        .sign(algorithm);
  }
}
