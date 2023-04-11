## Artifacts Array Path

Enter the path in the payload array to the artifacts listing. For example, in the following payload the artifacts are listed using `items`, so in Artifacts Array Path you reference the path with `$.items`.

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
