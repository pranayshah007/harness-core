# Review: Harness Variables in Values YAML

You cannot use Harness variables in Kubernetes manifests. You can only use Harness variables in Values YAML files. Let's look at an example. Here's a values.yaml file that uses Harness variables for name, image, dockercfg, and namespace:


```yaml
name: <+stage.variables.name>  
replicas: 2  
  
image: <+artifact.image>  
dockercfg: <+artifact.imagePullSecret>  
  
createNamespace: true  
namespace: <+infra.namespace>  
...
```

Here's a Deployment object that references the values.yaml using Go templating:


```yaml
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
        {{- if or .Values.env.config .Values.env.secrets}}  
        envFrom:  
        {{- if .Values.env.config}}  
        - configMapRef:  
            name: {{.Values.name}}  
        {{- end}}  
        {{- if .Values.env.secrets}}  
        - secretRef:  
            name: {{.Values.name}}  
        {{- end}}  
        {{- end}}
```
In Harness, these variables come from multiple places:

![](./static/define-kubernetes-manifests-29.png)

At runtime, the Harness variables in the values.yaml file are replaced with the values you entered in the Stage as fixed values or as [Runtime Inputs](../../../platform/20_References/runtime-inputs.md).

See [Built-in Harness Variables Reference](../../../platform/12_Variables-and-Expressions/harness-variables.md) and [Example Kubernetes Manifests using Go Templating](../../cd-technical-reference/cd-k8s-ref/example-kubernetes-manifests-using-go-templating.md).
