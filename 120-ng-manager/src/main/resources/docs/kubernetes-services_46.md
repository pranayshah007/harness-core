 dockercfg: <+artifacts.primary.imagePullSecret>  
  
createNamespace: true  
namespace: <+infra.namespace>  
  
serviceType: LoadBalancer  
  
servicePort: 80  
serviceTargetPort: 80  
  
env:  
  config:  
    key1: value10  
  secrets:  
    key2: value2
```

Here's the manifest containing multiple objects referring to the values in the values YAML file:

```go
{{- if .Values.env.config}}  
apiVersion: v1  
kind: ConfigMap  
metadata:  
 name: {{.Values.name}}  
data:  
{{.Values.env.config | toYaml | indent 2}}  
---  
{{- end}}  
  
{{- if .Values.env.secrets}}  
apiVersion: v1  
kind: Secret  
metadata:  
 name: {{.Values.name}}  
stringData:  
{{.Values.env.secrets | toYaml | indent 2}}  
---  
{{- end}}  
  
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

</details>

