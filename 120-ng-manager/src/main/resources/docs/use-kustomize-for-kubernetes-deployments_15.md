## Using Harness Secrets in Patches

You can also use Harness secrets in patches.

For example, let's say we have two secrets, one for `image` and one for `app`:

![](./static/use-kustomize-for-kubernetes-deployments-09.png)

The following patch uses these secrets for `image` and `app`, referencing them using the expression `<+secrets.getValue("[secret name]")>`.


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
          image: <+secrets.getValue("image")>  
  
---  
apiVersion: v1  
kind: Service  
metadata:  
  name: example-service  
  namespace: default  
spec:  
  selector:  
    app: <+secrets.getValue("appName")>
```
The secret output in the manifest will be asterisks (\*). The secret value is not displayed.

See [Add Text Secrets](../../../platform/6_Security/2-add-use-text-secrets.md).
