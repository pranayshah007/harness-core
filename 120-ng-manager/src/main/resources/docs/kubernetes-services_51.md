 dockercfg: <+artifacts.primary.imagePullSecret>  
  
createNamespace: true  
namespace: <+infra.namespace>  
  
serviceType: LoadBalancer  
  
servicePort: 80  
serviceTargetPort: 80  
  
env:  
  config:  
    key1: value10  
  secrets:  
    key2: value2
```
</details>


You can set variable values as runtime inputs and then provide their values when the pipeline is run.

For more information on runtime inputs and expressions, go to [Fixed values runtime inputs and expressions](https://developer.harness.io/docs/platform/references/runtime-inputs/).
