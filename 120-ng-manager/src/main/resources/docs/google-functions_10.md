 If you use a remove manifest, please ensure it includes all of these parameters.

function:
  name: <functionName>
  buildConfig:
    runtime: nodejs18
    entryPoint: helloGET
  environment: GEN_2
function_id: <functionName>
```

</details>

<details>
<summary>Example 1: A basic Cloud Function that triggers on an HTTP request</summary>

```yaml
function:
  name: my-http-function
  buildConfig:
    runtime: nodejs14
    entryPoint: myFunction
  environment: GEN_2
function_id: my-http-function

```

</details>

<details>
<summary>Example 2: A Cloud Function that uses environment variables</summary>

```yaml
function:
  name: my-env-function
  buildConfig:
    runtime: python38
    entryPoint: my_function
  environment: GEN_2
function_id: my-env-function
  environmentVariables:
    MY_VAR: my-value

```

</details>

<details>
<summary>Example 3: A Cloud Function that uses Cloud Storage as a trigger</summary>

```yaml
function:
  name: my-storage-function
  buildConfig:
    runtime: go111
    entryPoint: MyFunction
  environment: GEN_2
function_id: my-storage-function
  trigger:
    eventType: google.storage.object.finalize
    resource: projects/_/buckets/my-bucket
  availableMemoryMb: 512
  timeout: 180s

```
</details>


