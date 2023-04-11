## Primary Step in Deployment

Let's look at an example where the **Primary Deployment** section deploys the Service Definition **Manifests** objects. Here is the step in the Harness **Deployments** page:

![](./static/create-a-kubernetes-canary-deployment-09.png)

Before we look at the logs, let's look at the Service Definition **Manifests** files it's deploying.

Here is the Deployment object YAML from our Service **Manifests** section:


```yaml
apiVersion: apps/v1  
kind: Deployment  
metadata:  
  name: my-nginx  
  labels:  
    app: nginx  
spec:  
  replicas: 3  
...
```

Let's look at the **Initialize**, **Prepare**, and **Apply** stages of the **Rollout Deployment**.
