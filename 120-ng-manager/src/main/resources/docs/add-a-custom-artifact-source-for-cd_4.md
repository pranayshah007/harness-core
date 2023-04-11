## Add Custom Artifact Source

In **Artifacts**, click Add **Primary Artifact**.

Custom Artifact is also supported in **Sidecar**.In **Specify Artifact Repository Type**, select **Custom** and click **Continue**.

In the **Custom Artifact** source, you will enter a script to fetch a JSON payload and add it to the Harness variable `$HARNESS_ARTIFACT_RESULT_PATH`. Here's an example:


```
curl -X GET "https://nexus3.dev.harness.io/service/rest/v1/components?repository=cdp-qa-automation-1" -H "accept: application/json" > $HARNESS_ARTIFACT_RESULT_PATH
```

Here's an example of the JSON payload returned from the cURL command:


```json
{  
  "items" : [ {  
    "id" : "Y2RwLXFhLWF1dG9tYXRpb24tMTo5M2I5YjllYjlhN2VjYjA2NWJlYjdkNWUxNDgyMDFjOQ",  
    "repository" : "cdp-qa-automation-1",  
    "format" : "docker",  
    "group" : null,  
    "name" : "nginx",  
    "version" : "latest",  
    "assets" : [ {  
      "downloadUrl" : "https://nexus3.dev.harness.io/repository/cdp-qa-automation-1/v2/nginx/manifests/latest",  
      "path" : "v2/nginx/manifests/latest",  
      "id" : "Y2RwLXFhLWF1dG9tYXRpb24tMTo5MTJkMGZlN2I4MTkyMzkyODc0NTUyYTgyZWVmYzhkZQ",  
      "repository" : "cdp-qa-automation-1",  
      "format" : "docker",  
      "checksum" : {  
        "sha1" : "33acb567b9635e73ae566691eb22a89c84138c0b",  
        "sha256" : "bb129a712c2431ecce4af8dde831e980373b26368233ef0f3b2bae9e9ec515ee"  
      }  
    } ]  
...
```

Next, in **Artifacts Array Path**, you will define where to find each artifact in the array (`$.items`).

Next, in **Versions Path**, define where to find the artifact versions in your payload (`version`).

When you done Artifact Details will look something like this:

![](./static/add-a-custom-artifact-source-for-cd-07.png)

Enter the following settings.
