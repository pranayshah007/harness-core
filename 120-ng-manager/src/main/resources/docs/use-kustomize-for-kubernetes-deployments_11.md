# Option: Kustomize Patches

You cannot use Harness variables in the base manifest or kustomization.yaml. You can only use Harness variables in kustomize patches you add in **Kustomize Patches Manifest Details**.Kustomize patches override values in the base manifest. Harness supports the `patchesStrategicMerge` patches type.

For example, let's say you have a simple kustomization.yaml for your **application** folder like this:


```yaml
resources:  
  - namespace.yaml  
  - deployment.yaml  
  - service.yaml  
  - configmap.yaml
```
And you have an overlay for a production environment that points to the **application** folder like this:


```yaml
resources:  
  - ../../application  
namePrefix: nonpro-  
configMapGenerator:  
- name: example-config  
  namespace: default  
  #behavior: replace  
  files:  
    - configs/config.json  
patchesStrategicMerge:  
  - env.yaml
```
The `patchesStrategicMerge` label identifies the location of the patch **env.yaml**, which looks like this:


```yaml
apiVersion: apps/v1  
kind: Deployment  
metadata:  
  name: example-deploy  
spec:  
  template:  
    spec:  
      containers:  
      - name: example-app  
        env:  
        - name: ENVIRONMENT  
          value: Production
```
As you can see, it patches a new environment variable `name: ENVIRONMENT`.

Here's what the patching looks like side-by-side:

![](./static/use-kustomize-for-kubernetes-deployments-03.png)

When the kustomization.yaml is deployed, the patch is rendered and the environment variable is added to the deployment.yaml that is deployed.
