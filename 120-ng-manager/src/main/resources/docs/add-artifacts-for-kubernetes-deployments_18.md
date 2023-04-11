# Step 4: Reference the Artifact in Your Values File

In this example, the public artifact isn't hardcoded in the manifest and we reference the image in the Service Definition **Artifacts** section using the variable `<+artifact.image>`.

For example, here's a reference in a Values file:


```yaml
...  
name: <+stage.variables.name>  
replicas: 2  
  
image: <+artifact.image>  
dockercfg: <+artifact.imagePullSecret>  
...
```

That `<+artifact.image>` will reference the **Primary** artifact.

In your manifests, you simply use the Go template reference to the `image` value (`{{.Values.image}}`):


```yaml
apiVersion: apps/v1  
kind: Deployment  
...  
    spec:  
      {{- if .Values.dockercfg}}  
      imagePullSecrets:  
      - name: {{.Values.name}}-dockercfg  
      {{- end}}  
      containers:  
      - name: {{.Values.name}}  
        image: {{.Values.image}}  
...
```
See [Example Manifests](#example-manifests) for more details.
