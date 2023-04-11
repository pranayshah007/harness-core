# Kubernetes YAML

You can enter the path to a manifests folder.

For example, if your expanded package has this folder structure:


```yaml
manifest:  
 - values.yaml  
 - templates  
     - deployment.yaml  
     - service.yaml
```
In this example, you can enter manifest and Harness automatically detects the values.yaml and the other file (for example, deployment.yaml and service.yaml). If no values.yaml file is present, Harness will simply use the other files.

That's all the setup required. You can now deploy the Service and the script is executed at runtime.
