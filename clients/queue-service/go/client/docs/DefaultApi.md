# {{classname}}

All URIs are relative to *http://localhost:9091/*

Method | HTTP request | Description
------------- | ------------- | -------------
[**V1AckPost**](DefaultApi.md#V1AckPost) | **Post** /v1/ack | Ack a Redis message
[**V1DequeuePost**](DefaultApi.md#V1DequeuePost) | **Post** /v1/dequeue | Dequeue in Redis
[**V1HealthzGet**](DefaultApi.md#V1HealthzGet) | **Get** /v1/healthz | Health API for Queue Service
[**V1QueuePost**](DefaultApi.md#V1QueuePost) | **Post** /v1/queue | Enqueue
[**V1UnackPost**](DefaultApi.md#V1UnackPost) | **Post** /v1/unack | UnAck a Redis message or SubTopic

# **V1AckPost**
> StoreAckResponse V1AckPost(ctx, body, authorization)
Ack a Redis message

Ack a Redis message consumed successfully

### Required Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **ctx** | **context.Context** | context for authentication, logging, cancellation, deadlines, tracing, etc.
  **body** | [**StoreAckRequest**](StoreAckRequest.md)| query params | 
  **authorization** | **string**| Authorization | 

### Return type

[**StoreAckResponse**](store.AckResponse.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: application/json
 - **Accept**: application/json

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

# **V1DequeuePost**
> StoreDequeueResponse V1DequeuePost(ctx, body, authorization)
Dequeue in Redis

Dequeue a request

### Required Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **ctx** | **context.Context** | context for authentication, logging, cancellation, deadlines, tracing, etc.
  **body** | [**StoreDequeueRequest**](StoreDequeueRequest.md)| query params | 
  **authorization** | **string**| Authorization | 

### Return type

[**StoreDequeueResponse**](store.DequeueResponse.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: application/json
 - **Accept**: application/json

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

# **V1HealthzGet**
> string V1HealthzGet(ctx, )
Health API for Queue Service

Health API for Queue Service

### Required Parameters
This endpoint does not need any parameter.

### Return type

**string**

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: application/json

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

# **V1QueuePost**
> StoreEnqueueResponse V1QueuePost(ctx, body, authorization)
Enqueue

Enqueue the request

### Required Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **ctx** | **context.Context** | context for authentication, logging, cancellation, deadlines, tracing, etc.
  **body** | [**StoreEnqueueRequest**](StoreEnqueueRequest.md)| query params | 
  **authorization** | **string**| Authorization | 

### Return type

[**StoreEnqueueResponse**](store.EnqueueResponse.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: application/json
 - **Accept**: application/json

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

# **V1UnackPost**
> StoreUnAckResponse V1UnackPost(ctx, body, authorization)
UnAck a Redis message or SubTopic

UnAck a Redis message or SubTopic to stop processing

### Required Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **ctx** | **context.Context** | context for authentication, logging, cancellation, deadlines, tracing, etc.
  **body** | [**StoreUnAckRequest**](StoreUnAckRequest.md)| query params | 
  **authorization** | **string**| Authorization | 

### Return type

[**StoreUnAckResponse**](store.UnAckResponse.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: application/json
 - **Accept**: application/json

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

