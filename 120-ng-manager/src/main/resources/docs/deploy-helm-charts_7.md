## Helm chart with hardcoded artifact

The image artifact is identified in the Helm chart values.yaml file. For example:


```yaml
...  
containers:  
  - name: nginx  
    image: docker.io/bitnami/nginx:1.21.1-debian-10-r0  
...
```

If the image is hardcoded then you do not use the **Artifacts** section of the service. Any artifacts added here are ignored.
