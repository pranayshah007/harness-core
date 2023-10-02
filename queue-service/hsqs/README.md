# hsqs
Harness Queueing and Scheduling library

## Setup

Requirements:

`go>=1.19`\
`redis`

This library uses [echo](https://echo.labstack.com/]echo) framework

Default run configuration (for Goland) is already present in the repo in  

Use below command to run using bazel

1. `bazel build //queue-service/hsqs/...` to create bazel build

2. `swag init -g cmd/server.go` from hsqs folder to generate swagger               


# TL;DR: How to run queue service locally
```
export HSQS_DISABLE_AUTH=true
bazelisk run //queue-service/hsqs:hsqs-service server

OR

cd queue-service/hsqs
./hsqs server

Replace bazelisk -> bazel if needed
Queue service will start up on port 9091
```


# Env variables for auth

If you're running queue service locally, chances are you don't want to worry about auth. In that case, set
```
$ export HSQS_DISABLE_AUTH=true
```

# Enqueue / Dequeue Service Using Swagger Client

Swagger is accessible across all environments and localhost on https://{hostname}/swagger/index.html

Select the API you want and interact with it.

`http://127.0.0.1:9091/swagger/index.html#`


# Interact with Queue Service via Golang

Example available in file end_to_end_handler_test.go

Using net/http library

```
enqueueRequest := store.EnqueueRequest{
			Topic:        topic,
			SubTopic:     "ACCOUNT1",
			Payload:      "PAYLOAD1",
			ProducerName: producer,
	}

	requestBody, _ := json.Marshal(enqueueRequest)
	req, _ := http.NewRequest("POST", url+"/v1/queue", bytes.NewBuffer(requestBody))
	resp, err := call(req)
	if err != nil {
		panic(err)
	}
		
```

you can use json unmarshaller to convert it into response objects


# Interact with Queue Service via Java

Initialize service client config for queue Service in config.yaml

```
queueServiceClientConfig:
    httpClientConfig:
      baseUrl: http://localhost:9544/
      connectTimeOutSeconds: 15
      readTimeOutSeconds: 15
    queueServiceSecret: abc
    envNamespace: ${EVENTS_FRAMEWORK_ENV_NAMESPACE:-localhost}
```

You can use the hsqs retrofit client present inside clients/queue-service/java-client

```
Gson gson = new GsonBuilder()
                .setDateFormat("yyyy-MM-dd'T'HH:mm:ssZ")
                .create();
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl("http://localhost:9091/")
                .addConverterFactory(GsonConverterFactory.create(gson))
                .build();

HsqsServiceClient hsqsServiceClient = retrofit.create(HsqsServiceClient.class);

        try {
            Response<List<DequeueResponse>> execute =
					hsqsServiceClient.dequeue(DequeueRequest.builder()
							.batchSize(2)
							.consumerName("PMS")
							.topic("PMS")
							.maxWaitDuration(100).build(), "secret_auth").execute();

            System.out.println(execute);
        } catch (IOException e) {
            e.printStackTrace();
        }
```

# Accessing Queue Service on pre-QA and QA

## QA
```
https://qa.harness.io/queue-service/swagger/index.html
```

## Pre - QA
```
https://stress.harness.io/harness-nextgen/queue-service/swagger/index.html
```

# Metrics 

TBD