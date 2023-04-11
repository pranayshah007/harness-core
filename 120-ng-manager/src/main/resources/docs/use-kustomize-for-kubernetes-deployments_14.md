## Using Harness Variables in Patches

Kustomize does not natively support variable substitution but Harness supports variable substitution using [Harness variable expressions](../../../platform/12_Variables-and-Expressions/harness-variables.md) in Kustomize patches.

This allows you to configure any patch YAML labels as Harness variables expressions and replace those values at Pipeline runtime.

Let's look at an example.

Here is the deployment.yaml used by our kustomization:


```yaml
apiVersion: apps/v1  
kind: Deployment  
metadata:  
  name: example-deploy  
  namespace: default  
  labels:  
    app: example-app  
  annotations:  
spec:  
  selector:  
    matchLabels:  
      app: example-app  
  replicas: 1  
  strategy:  
    type: RollingUpdate  
    rollingUpdate:  
      maxSurge: 1  
      maxUnavailable: 0  
  template:  
    metadata:  
      labels:  
        app: example-app  
    spec:  
      containers:  
      - name: example-app  
        image: harness/todolist-sample:latest  
        imagePullPolicy: Always  
        ports:  
        - containerPort: 5000
```
You cannot use Harness variables in the base manifest or kustomization.yaml. You can only use Harness variables in kustomize patches you add in **Kustomize Patches Manifest Details**.You add the patch files that will patch deployment.yaml to **Kustomize Patches** **Manifest Details**. Only these patch files can use Harness variables.

We're going to use variables for `replicas` and `image`.

Let's look at the Harness variables in our Pipeline stage. Here are two Service-level variables:

![](./static/use-kustomize-for-kubernetes-deployments-05.png)

One variable is for the `image` and another for the `replicas` count.

A patch using these variables will look like this:


```yaml
apiVersion: apps/v1  
kind: Deployment  
metadata:  
 name: example-deploy  
 namespace: default  
spec:  
 template :  
   spec:  
     containers:  
       - name: example-app  
         image: <+serviceConfig.serviceDefinition.spec.variables.image>  
   
---  
apiVersion: apps/v1  
kind: Deployment  
metadata:  
 name: example-deploy  
 namespace: default  
spec:  
 replicas: <+serviceConfig.serviceDefinition.spec.variables.replica>
```
To get those variable references, you simply copy them:

![](./static/use-kustomize-for-kubernetes-deployments-06.png)

Add this patch in the Kustomize Patches **Manifest Details**:

![](./static/use-kustomize-for-kubernetes-deployments-07.png)

Now, when the Pipeline is run, the values for the two variables are rendered in the patch YAML and then the patch is applied to the deployment.yaml.

If you look at the Initialize phase of the deployment step (in Rolling, Canary, etc), you can see the variable values rendered in the Deployment manifest.

![](./static/use-kustomize-for-kubernetes-deployments-08.png)
