## Single Kubernetes service

Only one Kubernetes service is mandatory and it doesn’t need any annotations to establish if it is the primary (production) service.

Here is a very generic service example that uses a values.yaml file for its values:

```yaml
apiVersion: v1  
kind: Service  
metadata:  
  name: {{.Values.name}}-svc  
spec:  
  type: {{.Values.serviceType}}  
  ports:  
  - port: {{.Values.servicePort}}  
    targetPort: {{.Values.serviceTargetPort}}  
    protocol: TCP  
  selector:  
    app: {{.Values.name}}
```

This file and sample deployment and values.yaml files are publicly available on the [Harness Docs repo](https://github.com/wings-software/harness-docs/tree/main/k8s-bluegreen).

Note that there are no annotations to indicate that it is the primary service.

If you are using only one service in your manifest, Harness will create a duplicate of that service and name it with the `-stage` suffix.
