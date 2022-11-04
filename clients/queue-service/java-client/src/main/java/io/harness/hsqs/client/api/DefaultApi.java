/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.hsqs.client.api;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.hsqs.client.ApiCallback;
import io.harness.hsqs.client.ApiClient;
import io.harness.hsqs.client.ApiException;
import io.harness.hsqs.client.ApiResponse;
import io.harness.hsqs.client.Configuration;
import io.harness.hsqs.client.Pair;
import io.harness.hsqs.client.ProgressRequestBody;
import io.harness.hsqs.client.ProgressResponseBody;
import io.harness.hsqs.client.model.AckRequest;
import io.harness.hsqs.client.model.AckResponse;
import io.harness.hsqs.client.model.DequeueRequest;
import io.harness.hsqs.client.model.DequeueResponse;
import io.harness.hsqs.client.model.EnqueueRequest;
import io.harness.hsqs.client.model.EnqueueResponse;
import io.harness.hsqs.client.model.UnAckRequest;
import io.harness.hsqs.client.model.UnAckResponse;

import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@OwnedBy(PIPELINE)
public class DefaultApi {
  private ApiClient apiClient;

  public DefaultApi() {
    this(Configuration.getDefaultApiClient());
  }

  public DefaultApi(ApiClient apiClient) {
    this.apiClient = apiClient;
  }

  public ApiClient getApiClient() {
    return apiClient;
  }

  public void setApiClient(ApiClient apiClient) {
    this.apiClient = apiClient;
  }

  /**
   * Build call for v1AckPost
   *
   * @param body                    query params (required)
   * @param authorization           Authorization (required)
   * @param progressListener        Progress listener
   * @param progressRequestListener Progress request listener
   * @return Call to execute
   * @throws ApiException If fail to serialize the request body object
   */
  public com.squareup.okhttp.Call v1AckPostCall(AckRequest body, String authorization,
      final ProgressResponseBody.ProgressListener progressListener,
      final ProgressRequestBody.ProgressRequestListener progressRequestListener) throws ApiException {
    Object localVarPostBody = body;

    // create path and map variables
    String localVarPath = "/v1/ack";

    List<Pair> localVarQueryParams = new ArrayList<>();
    List<Pair> localVarCollectionQueryParams = new ArrayList<>();

    Map<String, String> localVarHeaderParams = new HashMap<>();
    if (authorization != null) {
      localVarHeaderParams.put("Authorization", apiClient.parameterToString(authorization));
    }

    Map<String, Object> localVarFormParams = new HashMap<>();

    final String[] localVarAccepts = {"application/json"};
    final String localVarAccept = apiClient.selectHeaderAccept(localVarAccepts);
    if (localVarAccept != null) {
      localVarHeaderParams.put("Accept", localVarAccept);
    }
    final String[] localVarContentTypes = {"application/json"};
    final String localVarContentType = apiClient.selectHeaderContentType(localVarContentTypes);
    localVarHeaderParams.put("Content-Type", localVarContentType);

    if (progressListener != null) {
      apiClient.getHttpClient().networkInterceptors().add(new com.squareup.okhttp.Interceptor() {
        @Override
        public com.squareup.okhttp.Response intercept(com.squareup.okhttp.Interceptor.Chain chain) throws IOException {
          com.squareup.okhttp.Response originalResponse = chain.proceed(chain.request());
          return originalResponse.newBuilder()
              .body(new ProgressResponseBody(originalResponse.body(), progressListener))
              .build();
        }
      });
    }

    String[] localVarAuthNames = new String[] {};
    return apiClient.buildCall(localVarPath, "POST", localVarQueryParams, localVarCollectionQueryParams,
        localVarPostBody, localVarHeaderParams, localVarFormParams, localVarAuthNames, progressRequestListener);
  }

  @SuppressWarnings("rawtypes")
  private com.squareup.okhttp.Call v1AckPostValidateBeforeCall(AckRequest body, String authorization,
      final ProgressResponseBody.ProgressListener progressListener,
      final ProgressRequestBody.ProgressRequestListener progressRequestListener) throws ApiException {
    // verify the required parameter 'body' is set
    if (body == null) {
      throw new ApiException("Missing the required parameter 'body' when calling v1AckPost(Async)");
    }
    // verify the required parameter 'authorization' is set
    if (authorization == null) {
      throw new ApiException("Missing the required parameter 'authorization' when calling v1AckPost(Async)");
    }

    return v1AckPostCall(body, authorization, progressListener, progressRequestListener);
  }

  /**
   * Ack a Redis message
   * Ack a Redis message consumed successfully
   *
   * @param body          query params (required)
   * @param authorization Authorization (required)
   * @return StoreAckResponse
   * @throws ApiException If fail to call the API, e.g. server error or cannot deserialize the response body
   */
  public AckResponse v1AckPost(AckRequest body, String authorization) throws ApiException {
    ApiResponse<AckResponse> resp = v1AckPostWithHttpInfo(body, authorization);
    return resp.getData();
  }

  /**
   * Ack a Redis message
   * Ack a Redis message consumed successfully
   *
   * @param body          query params (required)
   * @param authorization Authorization (required)
   * @return ApiResponse&lt;StoreAckResponse&gt;
   * @throws ApiException If fail to call the API, e.g. server error or cannot deserialize the response body
   */
  public ApiResponse<AckResponse> v1AckPostWithHttpInfo(AckRequest body, String authorization) throws ApiException {
    com.squareup.okhttp.Call call = v1AckPostValidateBeforeCall(body, authorization, null, null);
    Type localVarReturnType = new TypeToken<AckResponse>() {}.getType();
    return apiClient.execute(call, localVarReturnType);
  }

  /**
   * Ack a Redis message (asynchronously)
   * Ack a Redis message consumed successfully
   *
   * @param body          query params (required)
   * @param authorization Authorization (required)
   * @param callback      The callback to be executed when the API call finishes
   * @return The request call
   * @throws ApiException If fail to process the API call, e.g. serializing the request body object
   */
  public com.squareup.okhttp.Call v1AckPostAsync(
      AckRequest body, String authorization, final ApiCallback<AckResponse> callback) throws ApiException {
    ProgressResponseBody.ProgressListener progressListener = null;
    ProgressRequestBody.ProgressRequestListener progressRequestListener = null;

    if (callback != null) {
      progressListener = new ProgressResponseBody.ProgressListener() {
        @Override
        public void update(long bytesRead, long contentLength, boolean done) {
          callback.onDownloadProgress(bytesRead, contentLength, done);
        }
      };

      progressRequestListener = new ProgressRequestBody.ProgressRequestListener() {
        @Override
        public void onRequestProgress(long bytesWritten, long contentLength, boolean done) {
          callback.onUploadProgress(bytesWritten, contentLength, done);
        }
      };
    }

    com.squareup.okhttp.Call call =
        v1AckPostValidateBeforeCall(body, authorization, progressListener, progressRequestListener);
    Type localVarReturnType = new TypeToken<AckResponse>() {}.getType();
    apiClient.executeAsync(call, localVarReturnType, callback);
    return call;
  }

  /**
   * Build call for v1DequeuePost
   *
   * @param body                    query params (required)
   * @param authorization           Authorization (required)
   * @param progressListener        Progress listener
   * @param progressRequestListener Progress request listener
   * @return Call to execute
   * @throws ApiException If fail to serialize the request body object
   */
  public com.squareup.okhttp.Call v1DequeuePostCall(DequeueRequest body, String authorization,
      final ProgressResponseBody.ProgressListener progressListener,
      final ProgressRequestBody.ProgressRequestListener progressRequestListener) throws ApiException {
    Object localVarPostBody = body;

    // create path and map variables
    String localVarPath = "/v1/dequeue";

    List<Pair> localVarQueryParams = new ArrayList<>();
    List<Pair> localVarCollectionQueryParams = new ArrayList<>();

    Map<String, String> localVarHeaderParams = new HashMap<>();
    if (authorization != null) {
      localVarHeaderParams.put("Authorization", apiClient.parameterToString(authorization));
    }
    Map<String, Object> localVarFormParams = new HashMap<>();

    final String[] localVarAccepts = {"application/json"};
    final String localVarAccept = apiClient.selectHeaderAccept(localVarAccepts);
    if (localVarAccept != null) {
      localVarHeaderParams.put("Accept", localVarAccept);
    }
    final String[] localVarContentTypes = {"application/json"};
    final String localVarContentType = apiClient.selectHeaderContentType(localVarContentTypes);
    localVarHeaderParams.put("Content-Type", localVarContentType);

    if (progressListener != null) {
      apiClient.getHttpClient().networkInterceptors().add(new com.squareup.okhttp.Interceptor() {
        @Override
        public com.squareup.okhttp.Response intercept(com.squareup.okhttp.Interceptor.Chain chain) throws IOException {
          com.squareup.okhttp.Response originalResponse = chain.proceed(chain.request());
          return originalResponse.newBuilder()
              .body(new ProgressResponseBody(originalResponse.body(), progressListener))
              .build();
        }
      });
    }

    String[] localVarAuthNames = new String[] {};
    return apiClient.buildCall(localVarPath, "POST", localVarQueryParams, localVarCollectionQueryParams,
        localVarPostBody, localVarHeaderParams, localVarFormParams, localVarAuthNames, progressRequestListener);
  }

  @SuppressWarnings("rawtypes")
  private com.squareup.okhttp.Call v1DequeuePostValidateBeforeCall(DequeueRequest body, String authorization,
      final ProgressResponseBody.ProgressListener progressListener,
      final ProgressRequestBody.ProgressRequestListener progressRequestListener) throws ApiException {
    // verify the required parameter 'body' is set
    if (body == null) {
      throw new ApiException("Missing the required parameter 'body' when calling v1DequeuePost(Async)");
    }
    // verify the required parameter 'authorization' is set
    if (authorization == null) {
      throw new ApiException("Missing the required parameter 'authorization' when calling v1DequeuePost(Async)");
    }

    return v1DequeuePostCall(body, authorization, progressListener, progressRequestListener);
  }

  /**
   * Dequeue in Redis
   * Dequeue a request
   *
   * @param body          query params (required)
   * @param authorization Authorization (required)
   * @return StoreDequeueResponse
   * @throws ApiException If fail to call the API, e.g. server error or cannot deserialize the response body
   */
  public DequeueResponse v1DequeuePost(DequeueRequest body, String authorization) throws ApiException {
    ApiResponse<DequeueResponse> resp = v1DequeuePostWithHttpInfo(body, authorization);
    return resp.getData();
  }

  /**
   * Dequeue in Redis
   * Dequeue a request
   *
   * @param body          query params (required)
   * @param authorization Authorization (required)
   * @return ApiResponse&lt;DequeueResponse&gt;
   * @throws ApiException If fail to call the API, e.g. server error or cannot deserialize the response body
   */
  public ApiResponse<DequeueResponse> v1DequeuePostWithHttpInfo(DequeueRequest body, String authorization)
      throws ApiException {
    com.squareup.okhttp.Call call = v1DequeuePostValidateBeforeCall(body, authorization, null, null);
    Type localVarReturnType = new TypeToken<DequeueResponse>() {}.getType();
    return apiClient.execute(call, localVarReturnType);
  }

  /**
   * Dequeue in Redis (asynchronously)
   * Dequeue a request
   *
   * @param body          query params (required)
   * @param authorization Authorization (required)
   * @param callback      The callback to be executed when the API call finishes
   * @return The request call
   * @throws ApiException If fail to process the API call, e.g. serializing the request body object
   */
  public com.squareup.okhttp.Call v1DequeuePostAsync(
      DequeueRequest body, String authorization, final ApiCallback<DequeueResponse> callback) throws ApiException {
    ProgressResponseBody.ProgressListener progressListener = null;
    ProgressRequestBody.ProgressRequestListener progressRequestListener = null;

    if (callback != null) {
      progressListener = new ProgressResponseBody.ProgressListener() {
        @Override
        public void update(long bytesRead, long contentLength, boolean done) {
          callback.onDownloadProgress(bytesRead, contentLength, done);
        }
      };

      progressRequestListener = new ProgressRequestBody.ProgressRequestListener() {
        @Override
        public void onRequestProgress(long bytesWritten, long contentLength, boolean done) {
          callback.onUploadProgress(bytesWritten, contentLength, done);
        }
      };
    }

    com.squareup.okhttp.Call call =
        v1DequeuePostValidateBeforeCall(body, authorization, progressListener, progressRequestListener);
    Type localVarReturnType = new TypeToken<DequeueResponse>() {}.getType();
    apiClient.executeAsync(call, localVarReturnType, callback);
    return call;
  }

  /**
   * Build call for v1HealthzGet
   *
   * @param progressListener        Progress listener
   * @param progressRequestListener Progress request listener
   * @return Call to execute
   * @throws ApiException If fail to serialize the request body object
   */
  public com.squareup.okhttp.Call v1HealthzGetCall(final ProgressResponseBody.ProgressListener progressListener,
      final ProgressRequestBody.ProgressRequestListener progressRequestListener) throws ApiException {
    Object localVarPostBody = null;

    // create path and map variables
    String localVarPath = "/v1/healthz";

    List<Pair> localVarQueryParams = new ArrayList<>();
    List<Pair> localVarCollectionQueryParams = new ArrayList<>();

    Map<String, String> localVarHeaderParams = new HashMap<>();

    Map<String, Object> localVarFormParams = new HashMap<>();

    final String[] localVarAccepts = {"application/json"};
    final String localVarAccept = apiClient.selectHeaderAccept(localVarAccepts);
    if (localVarAccept != null) {
      localVarHeaderParams.put("Accept", localVarAccept);
    }
    final String[] localVarContentTypes = {};
    final String localVarContentType = apiClient.selectHeaderContentType(localVarContentTypes);
    localVarHeaderParams.put("Content-Type", localVarContentType);

    if (progressListener != null) {
      apiClient.getHttpClient().networkInterceptors().add(new com.squareup.okhttp.Interceptor() {
        @Override
        public com.squareup.okhttp.Response intercept(com.squareup.okhttp.Interceptor.Chain chain) throws IOException {
          com.squareup.okhttp.Response originalResponse = chain.proceed(chain.request());
          return originalResponse.newBuilder()
              .body(new ProgressResponseBody(originalResponse.body(), progressListener))
              .build();
        }
      });
    }

    String[] localVarAuthNames = new String[] {};
    return apiClient.buildCall(localVarPath, "GET", localVarQueryParams, localVarCollectionQueryParams,
        localVarPostBody, localVarHeaderParams, localVarFormParams, localVarAuthNames, progressRequestListener);
  }

  @SuppressWarnings("rawtypes")
  private com.squareup.okhttp.Call v1HealthzGetValidateBeforeCall(
      final ProgressResponseBody.ProgressListener progressListener,
      final ProgressRequestBody.ProgressRequestListener progressRequestListener) throws ApiException {
    return v1HealthzGetCall(progressListener, progressRequestListener);
  }

  /**
   * Health API for Queue Service
   * Health API for Queue Service
   *
   * @return String
   * @throws ApiException If fail to call the API, e.g. server error or cannot deserialize the response body
   */
  public String v1HealthzGet() throws ApiException {
    ApiResponse<String> resp = v1HealthzGetWithHttpInfo();
    return resp.getData();
  }

  /**
   * Health API for Queue Service
   * Health API for Queue Service
   *
   * @return ApiResponse&lt;String&gt;
   * @throws ApiException If fail to call the API, e.g. server error or cannot deserialize the response body
   */
  public ApiResponse<String> v1HealthzGetWithHttpInfo() throws ApiException {
    com.squareup.okhttp.Call call = v1HealthzGetValidateBeforeCall(null, null);
    Type localVarReturnType = new TypeToken<String>() {}.getType();
    return apiClient.execute(call, localVarReturnType);
  }

  /**
   * Health API for Queue Service (asynchronously)
   * Health API for Queue Service
   *
   * @param callback The callback to be executed when the API call finishes
   * @return The request call
   * @throws ApiException If fail to process the API call, e.g. serializing the request body object
   */
  public com.squareup.okhttp.Call v1HealthzGetAsync(final ApiCallback<String> callback) throws ApiException {
    ProgressResponseBody.ProgressListener progressListener = null;
    ProgressRequestBody.ProgressRequestListener progressRequestListener = null;

    if (callback != null) {
      progressListener = new ProgressResponseBody.ProgressListener() {
        @Override
        public void update(long bytesRead, long contentLength, boolean done) {
          callback.onDownloadProgress(bytesRead, contentLength, done);
        }
      };

      progressRequestListener = new ProgressRequestBody.ProgressRequestListener() {
        @Override
        public void onRequestProgress(long bytesWritten, long contentLength, boolean done) {
          callback.onUploadProgress(bytesWritten, contentLength, done);
        }
      };
    }

    com.squareup.okhttp.Call call = v1HealthzGetValidateBeforeCall(progressListener, progressRequestListener);
    Type localVarReturnType = new TypeToken<String>() {}.getType();
    apiClient.executeAsync(call, localVarReturnType, callback);
    return call;
  }

  /**
   * Build call for v1QueuePost
   *
   * @param body                    query params (required)
   * @param authorization           Authorization (required)
   * @param progressListener        Progress listener
   * @param progressRequestListener Progress request listener
   * @return Call to execute
   * @throws ApiException If fail to serialize the request body object
   */
  public com.squareup.okhttp.Call v1QueuePostCall(EnqueueRequest body, String authorization,
      final ProgressResponseBody.ProgressListener progressListener,
      final ProgressRequestBody.ProgressRequestListener progressRequestListener) throws ApiException {
    Object localVarPostBody = body;

    // create path and map variables
    String localVarPath = "/v1/queue";

    List<Pair> localVarQueryParams = new ArrayList<>();
    List<Pair> localVarCollectionQueryParams = new ArrayList<>();

    Map<String, String> localVarHeaderParams = new HashMap<>();
    if (authorization != null) {
      localVarHeaderParams.put("Authorization", apiClient.parameterToString(authorization));
    }
    Map<String, Object> localVarFormParams = new HashMap<>();

    final String[] localVarAccepts = {"application/json"};
    final String localVarAccept = apiClient.selectHeaderAccept(localVarAccepts);
    if (localVarAccept != null) {
      localVarHeaderParams.put("Accept", localVarAccept);
    }
    final String[] localVarContentTypes = {"application/json"};
    final String localVarContentType = apiClient.selectHeaderContentType(localVarContentTypes);
    localVarHeaderParams.put("Content-Type", localVarContentType);

    if (progressListener != null) {
      apiClient.getHttpClient().networkInterceptors().add(new com.squareup.okhttp.Interceptor() {
        @Override
        public com.squareup.okhttp.Response intercept(com.squareup.okhttp.Interceptor.Chain chain) throws IOException {
          com.squareup.okhttp.Response originalResponse = chain.proceed(chain.request());
          return originalResponse.newBuilder()
              .body(new ProgressResponseBody(originalResponse.body(), progressListener))
              .build();
        }
      });
    }

    String[] localVarAuthNames = new String[] {};
    return apiClient.buildCall(localVarPath, "POST", localVarQueryParams, localVarCollectionQueryParams,
        localVarPostBody, localVarHeaderParams, localVarFormParams, localVarAuthNames, progressRequestListener);
  }

  @SuppressWarnings("rawtypes")
  private com.squareup.okhttp.Call v1QueuePostValidateBeforeCall(EnqueueRequest body, String authorization,
      final ProgressResponseBody.ProgressListener progressListener,
      final ProgressRequestBody.ProgressRequestListener progressRequestListener) throws ApiException {
    // verify the required parameter 'body' is set
    if (body == null) {
      throw new ApiException("Missing the required parameter 'body' when calling v1QueuePost(Async)");
    }
    // verify the required parameter 'authorization' is set
    if (authorization == null) {
      throw new ApiException("Missing the required parameter 'authorization' when calling v1QueuePost(Async)");
    }

    return v1QueuePostCall(body, authorization, progressListener, progressRequestListener);
  }

  /**
   * Enqueue
   * Enqueue the request
   *
   * @param body          query params (required)
   * @param authorization Authorization (required)
   * @return StoreEnqueueResponse
   * @throws ApiException If fail to call the API, e.g. server error or cannot deserialize the response body
   */
  public EnqueueResponse v1QueuePost(EnqueueRequest body, String authorization) throws ApiException {
    ApiResponse<EnqueueResponse> resp = v1QueuePostWithHttpInfo(body, authorization);
    return resp.getData();
  }

  /**
   * Enqueue
   * Enqueue the request
   *
   * @param body          query params (required)
   * @param authorization Authorization (required)
   * @return ApiResponse&lt;StoreEnqueueResponse&gt;
   * @throws ApiException If fail to call the API, e.g. server error or cannot deserialize the response body
   */
  public ApiResponse<EnqueueResponse> v1QueuePostWithHttpInfo(EnqueueRequest body, String authorization)
      throws ApiException {
    com.squareup.okhttp.Call call = v1QueuePostValidateBeforeCall(body, authorization, null, null);
    Type localVarReturnType = new TypeToken<EnqueueResponse>() {}.getType();
    return apiClient.execute(call, localVarReturnType);
  }

  /**
   * Enqueue (asynchronously)
   * Enqueue the request
   *
   * @param body          query params (required)
   * @param authorization Authorization (required)
   * @param callback      The callback to be executed when the API call finishes
   * @return The request call
   * @throws ApiException If fail to process the API call, e.g. serializing the request body object
   */
  public com.squareup.okhttp.Call v1QueuePostAsync(
      EnqueueRequest body, String authorization, final ApiCallback<EnqueueResponse> callback) throws ApiException {
    ProgressResponseBody.ProgressListener progressListener = null;
    ProgressRequestBody.ProgressRequestListener progressRequestListener = null;

    if (callback != null) {
      progressListener = new ProgressResponseBody.ProgressListener() {
        @Override
        public void update(long bytesRead, long contentLength, boolean done) {
          callback.onDownloadProgress(bytesRead, contentLength, done);
        }
      };

      progressRequestListener = new ProgressRequestBody.ProgressRequestListener() {
        @Override
        public void onRequestProgress(long bytesWritten, long contentLength, boolean done) {
          callback.onUploadProgress(bytesWritten, contentLength, done);
        }
      };
    }

    com.squareup.okhttp.Call call =
        v1QueuePostValidateBeforeCall(body, authorization, progressListener, progressRequestListener);
    Type localVarReturnType = new TypeToken<EnqueueResponse>() {}.getType();
    apiClient.executeAsync(call, localVarReturnType, callback);
    return call;
  }

  /**
   * Build call for v1UnackPost
   *
   * @param body                    query params (required)
   * @param authorization           Authorization (required)
   * @param progressListener        Progress listener
   * @param progressRequestListener Progress request listener
   * @return Call to execute
   * @throws ApiException If fail to serialize the request body object
   */
  public com.squareup.okhttp.Call v1UnackPostCall(UnAckRequest body, String authorization,
      final ProgressResponseBody.ProgressListener progressListener,
      final ProgressRequestBody.ProgressRequestListener progressRequestListener) throws ApiException {
    Object localVarPostBody = body;

    // create path and map variables
    String localVarPath = "/v1/unack";

    List<Pair> localVarQueryParams = new ArrayList<>();
    List<Pair> localVarCollectionQueryParams = new ArrayList<>();

    Map<String, String> localVarHeaderParams = new HashMap<>();
    if (authorization != null) {
      localVarHeaderParams.put("Authorization", apiClient.parameterToString(authorization));
    }

    Map<String, Object> localVarFormParams = new HashMap<>();

    final String[] localVarAccepts = {"application/json"};
    final String localVarAccept = apiClient.selectHeaderAccept(localVarAccepts);
    if (localVarAccept != null) {
      localVarHeaderParams.put("Accept", localVarAccept);
    }

    final String[] localVarContentTypes = {"application/json"};
    final String localVarContentType = apiClient.selectHeaderContentType(localVarContentTypes);
    localVarHeaderParams.put("Content-Type", localVarContentType);

    if (progressListener != null) {
      apiClient.getHttpClient().networkInterceptors().add(new com.squareup.okhttp.Interceptor() {
        @Override
        public com.squareup.okhttp.Response intercept(com.squareup.okhttp.Interceptor.Chain chain) throws IOException {
          com.squareup.okhttp.Response originalResponse = chain.proceed(chain.request());
          return originalResponse.newBuilder()
              .body(new ProgressResponseBody(originalResponse.body(), progressListener))
              .build();
        }
      });
    }

    String[] localVarAuthNames = new String[] {};
    return apiClient.buildCall(localVarPath, "POST", localVarQueryParams, localVarCollectionQueryParams,
        localVarPostBody, localVarHeaderParams, localVarFormParams, localVarAuthNames, progressRequestListener);
  }

  @SuppressWarnings("rawtypes")
  private com.squareup.okhttp.Call v1UnackPostValidateBeforeCall(UnAckRequest body, String authorization,
      final ProgressResponseBody.ProgressListener progressListener,
      final ProgressRequestBody.ProgressRequestListener progressRequestListener) throws ApiException {
    // verify the required parameter 'body' is set
    if (body == null) {
      throw new ApiException("Missing the required parameter 'body' when calling v1UnackPost(Async)");
    }
    // verify the required parameter 'authorization' is set
    if (authorization == null) {
      throw new ApiException("Missing the required parameter 'authorization' when calling v1UnackPost(Async)");
    }

    return v1UnackPostCall(body, authorization, progressListener, progressRequestListener);
  }

  /**
   * UnAck a Redis message or SubTopic
   * UnAck a Redis message or SubTopic to stop processing
   *
   * @param body          query params (required)
   * @param authorization Authorization (required)
   * @return StoreUnAckResponse
   * @throws ApiException If fail to call the API, e.g. server error or cannot deserialize the response body
   */
  public UnAckResponse v1UnackPost(UnAckRequest body, String authorization) throws ApiException {
    ApiResponse<UnAckResponse> resp = v1UnackPostWithHttpInfo(body, authorization);
    return resp.getData();
  }

  /**
   * UnAck a Redis message or SubTopic
   * UnAck a Redis message or SubTopic to stop processing
   *
   * @param body          query params (required)
   * @param authorization Authorization (required)
   * @return ApiResponse&lt;StoreUnAckResponse&gt;
   * @throws ApiException If fail to call the API, e.g. server error or cannot deserialize the response body
   */
  public ApiResponse<UnAckResponse> v1UnackPostWithHttpInfo(UnAckRequest body, String authorization)
      throws ApiException {
    com.squareup.okhttp.Call call = v1UnackPostValidateBeforeCall(body, authorization, null, null);
    Type localVarReturnType = new TypeToken<UnAckResponse>() {}.getType();
    return apiClient.execute(call, localVarReturnType);
  }

  /**
   * UnAck a Redis message or SubTopic (asynchronously)
   * UnAck a Redis message or SubTopic to stop processing
   *
   * @param body          query params (required)
   * @param authorization Authorization (required)
   * @param callback      The callback to be executed when the API call finishes
   * @return The request call
   * @throws ApiException If fail to process the API call, e.g. serializing the request body object
   */
  public com.squareup.okhttp.Call v1UnackPostAsync(
      UnAckRequest body, String authorization, final ApiCallback<UnAckResponse> callback) throws ApiException {
    ProgressResponseBody.ProgressListener progressListener = null;
    ProgressRequestBody.ProgressRequestListener progressRequestListener = null;

    if (callback != null) {
      progressListener = new ProgressResponseBody.ProgressListener() {
        @Override
        public void update(long bytesRead, long contentLength, boolean done) {
          callback.onDownloadProgress(bytesRead, contentLength, done);
        }
      };

      progressRequestListener = new ProgressRequestBody.ProgressRequestListener() {
        @Override
        public void onRequestProgress(long bytesWritten, long contentLength, boolean done) {
          callback.onUploadProgress(bytesWritten, contentLength, done);
        }
      };
    }

    com.squareup.okhttp.Call call =
        v1UnackPostValidateBeforeCall(body, authorization, progressListener, progressRequestListener);
    Type localVarReturnType = new TypeToken<UnAckResponse>() {}.getType();
    apiClient.executeAsync(call, localVarReturnType, callback);
    return call;
  }
}
