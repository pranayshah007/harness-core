# Step 1: Use dockercfg in values.yaml

Open the values.yaml file you are using for deployment.

Verify that `dockercfg` key exists, and uses the `<+artifact.imagePullSecret>` expression to obtain the credentials:


```yaml
name: <+stage.variables.name>  
replicas: 2  
  
image: <+artifact.image>  
dockercfg: <+artifact.imagePullSecret>  
  
createNamespace: true  
namespace: <+infra.namespace>  
...
```
