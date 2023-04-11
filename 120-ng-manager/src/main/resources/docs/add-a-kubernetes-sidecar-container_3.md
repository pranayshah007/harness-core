# Step 1: Prepare the Sidecar Manifest

If you are using Harness **Artifacts**, in the deployment manifest or values.yaml file for this deployment, you reference this artifact using the expression `<+artifacts.sidecars.[sidecar_identifier].imagePath>`.

Using the earlier example of the Id **sidecar**, the reference is `<+artifacts.sidecars.sidecar.imagePath>`. Here's the values.yaml:


```yaml
name: harness-example  
replicas: 1  
  
image: <+artifacts.sidecars.sidecar.imagePath>  
dockercfg: <+artifacts.sidecars.sidecar.imagePullSecret>  
  
createNamespace: true  
namespace: <+infra.namespace>  
...
```

Other sidecar expressions are:

* `<+artifacts.sidecars.sidecar.imagePullSecret>`
* `<+artifacts.sidecars.[sidecar_identifier].imagePath>`
* `<+artifacts.sidecars.[sidecar_identifier].type>`
* `<+artifacts.sidecars.[sidecar_identifier].tag>`
* `<+artifacts.sidecars.[sidecar_identifier].connectorRef>`

Now that you have your sidecar manifests set up, you can add them to Harness.
