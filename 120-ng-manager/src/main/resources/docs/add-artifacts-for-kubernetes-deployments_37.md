## Private Artifact

When the image is in a private repo, you use the expression `<+artifact.imagePullSecret>` in the Secret and Deployment objects in your manifest.

This key will import the credentials from the Docker credentials file in the artifact.

It's much simpler to simple use the `<+artifact.imagePullSecret>` expression in the values.yaml file and then reference it in other manifests.

Using the values.yaml file above, we simply remove the comment in front of `dockercfg: <+artifact.imagePullSecret>`:


```yaml
name: <+stage.variables.name>  
replicas: 2  
  
image: <+artifact.image>  
dockercfg: <+artifact.imagePullSecret>  
  
createNamespace: true  
namespace: <+infra.namespace>  
...
```

You don't need to make changes to the deployment manifest from earlier. It uses Go templating to check for the `dockercfg` value in values.yaml and applies it to Secret and Deployment.

If using the condition `{{- if .Values.dockercfg}}` to check for `dockercfg` in values.yaml.


```go
...  
{{- if .Values.dockercfg}}  
apiVersion: v1  
kind: Secret  
metadata:  
  name: {{.Values.name}}-dockercfg  
  annotations:  
    harness.io/skip-versioning: true  
data:  
  .dockercfg: {{.Values.dockercfg}}  
type: kubernetes.io/dockercfg  
---  
{{- end}}  
  
apiVersion: apps/v1  
kind: Deployment  
metadata:  
  name: {{.Values.name}}-deployment  
spec:  
  replicas: {{int .Values.replicas}}  
  selector:  
    matchLabels:  
      app: {{.Values.name}}  
  template:  
    metadata:  
      labels:  
        app: {{.Values.name}}  
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