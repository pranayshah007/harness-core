## values.yaml

In addition to `<+artifact.image>` and `dockercfg` , this file also uses `name: <+stage.variables.name>` to reference a Stage variable `name` and `namespace: <+infra.namespace>` to reference the namespace entered in the Stage's **Infrastructure Definition**. Service type and ports are hardcoded.


```yaml
name: <+stage.variables.name>  
replicas: 2  
  
image: <+artifact.image>  
dockercfg: <+artifact.imagePullSecret>  
  
createNamespace: true  
namespace: <+infra.namespace>  
  