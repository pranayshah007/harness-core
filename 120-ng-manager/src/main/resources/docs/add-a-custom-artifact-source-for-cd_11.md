# Additional Attributes (Metadata)

In **Additional Attributes**, you can map any additional values from your JSON array.

For example, the following payload has `downloadUrl` and `path` in its `assets`:

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
        "sha1" : "33acb567b96xxx9c84138c0b",  
        "sha256" : "bb129a712cxxx33ef0f3b2bae9e9ec515ee"  
      }  
    } ]  
...
```

You can assign these items to variables. In **Name**, enter the variable name and in **Value** enter the path to the item.

For example, to reference `downloadUrl` and `path` in the first item in the array:

* `assets[0].downloadUrl`
* `assets[0].path`

Here's an example:

![](./static/add-a-custom-artifact-source-for-cd-09.png)

Later, in a Shell Script step, you can reference these attributes using the expression format:

`<+pipeline.stages.[stage_Id].spec.serviceConfig.output.artifactResults.primary.metadata.[attribute_name]>`

For example:

```
echo "URL: <+pipeline.stages.Kube.spec.serviceConfig.output.artifactResults.primary.metadata.URL>"  
echo "Path: <+pipeline.stages.Kube.spec.serviceConfig.output.artifactResults.primary.metadata.path>"
```

At runtime, these expressions will resolve to the data from the array.
