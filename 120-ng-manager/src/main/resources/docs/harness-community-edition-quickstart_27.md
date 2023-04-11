 harness-service-account.yml  
apiVersion: v1  
kind: ServiceAccount  
metadata:  
  name: harness-service-account  
  namespace: harness
```

Next, you apply the SA.

```
kubectl apply -f harness-service-account.yml
```

Next, grant the SA the `cluster-admin` permission.

```yaml