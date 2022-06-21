# Status and Error Responses

HTTP has a range of useful status code already and standard status with responses should be used.

- [2xx Success](#2xx-success)
  * [200 OK](#200-ok)
  * [201 Created](#201-created)
  * [202 Accepted](#202-accepted)
  * [204 No Content](#204-no-content)
- [3xx Redirection](#3xx-redirection)
  * [304 Not Modified](#304-not-modified)
- [4xx Client Error](#4xx-client-error)
  * [400 Bad Request](#400-bad-request)
  * [401 Unauthorized](#401-unauthorized)
  * [403 Forbidden](#403-forbidden)
  * [404 Not Found](#404-not-found)
  * [412 Precondition Failed](#412-precondition-failed)
  * [415 Unsupported Media Type](#415-unsupported-media-type)
  * [429 Too Many Requests](#429-too-many-requests)
* [5xx Server Error](#5xx-server-error)
  * [500 Server Error](#500-server-error)


## 2xx Success
The following are default status codes indicating the request was completed successfully. 

### 200 OK 

Should be the typical response to indicate a successful Show, List and Edit actions.

Where possible individual resources should be returned as the highest level and nesting should be avoided e.g.

Prefer:

```json
{
  "name": "Max"
}
```

Not:

```json
{
  "data": {
    "name": "Max"
  }
}
```

Similarly, collection responses should be favoured:

Prefer:
```json
[
  { "name": "Duke" },
  { "name": "Gidget" },
  { "name": "Max" }
]
```

Over:
```json
{
  "items": [
    { "name": "Duke" },
    { "name": "Gidget" },
    { "name": "Max" }
  ]
}
```

### 201 Created 

Should be used on the successful Create action of a new resource and be accompanied by a view of the resource which was created. 

### 202 Accepted

May be used for asynchronous requests where the final outcome of the request is unknown but there is a guarantee the request has been accepted by the platform. e.g. where processing may take longer than acceptable for typical HTTP request/response latency. 

If used, this response should contain information to the user on where to track the request progress; Location header may be used but there is no guarantee a client will use the Location header with a 202 status so the payload should also provide information on where to track progress.

### 204 No Content

Should be used on the successful Destroy action of a resource with an empty response body.

## 3xx Redirection

Typically most 3xx status codes are not required for service-level API responses but are typically handled higher in the stack. 

### 304 Not Modified

When using http caching through the use of Etag, If-None-Match and Last-Modified headers use a 304 response. Note when returning a 304 the response must have the same headers as would be applied to a 200 response e.g. Cache-Control, Content-Location, Date, ETag, Expires, and Vary. 

_Note: if using If-None-Match header for PUT actions, e.g. to get optimistic locking, the server should return a 412 Precondition Failed response rather than the 304 Not Modified response._

## 4xx Client Error

4xx range errors are to indicate the client made an error in the request and should resolve the problem before trying again. Apart from the 

### 400 Bad Request

Generally reserved for input validation errors, e.g. on creation where field validation fails or a JSON payload is malformed. 

As a 400 error is generic in nature it requires slightly different message format. It should be a `msg` describing the error along with a list of errors describing how to resolve the issue. Each error must have a `msg` attribute and optionally the `field` attribute. 

```json
{
  "error": "Validation error",
  "detail" [
    {
      "error": "Required error message for the consumer to understand how to adjust their request and try again.",
      "field": "Optional field the error corsponds to, e.g. in the case of a validation error."
    }
  ]
}
```

### 401 Unauthorized

The user has no active session. User should retry with a valid token.

```json
{
  "error": "Unauthorized"
}
```

### 403 Forbidden

The user has a valid token but does not have access to the requested resource.

```json
{
  "error": "Forbidden"
}
```

### 404 Not Found

The requested path does not exist; either the resource or the resource collection.

```json
{
  "error": "Not Found"
}
```

### 412 Precondition Failed

Status code when request contains an If-None-Matched header but the etag does not match the existing resource etag.

```json
{
  "error": "Precondition Failed"
}
```

### 415 Unsupported Media Type

The media-type in Content-type of the request is not supported by the server.

```json
{
  "error": "Unsupported Media Type"
}
```

### 429 Too Many Requests

This response should be used when a rate limit is exceeded.

```json
{
  "error": "Unsupported Media Type"
}
```

## 5xx Server Error

As the end user can do little about this type of error there is little need to leak internal implementations and in most cases a “500 Internal Server Error” should be enough.

### 500 Server Error

Typically this is a catch all error response for uncaught code errors, backing store, db, network or queue failures and when the api consumer can do nothing to help. 

```json
{
  "error": "Server Error"
}
```

_Note: all code level exceptions, traces and underlying messages must be terminated with a final log message and not returned in the response to the client. The response should not indicate what has failed._

