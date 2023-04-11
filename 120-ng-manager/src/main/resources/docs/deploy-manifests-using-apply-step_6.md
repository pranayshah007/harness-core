 harness.io/skip-file-for-deploy  
  
{{- if .Values.env.config}}  
apiVersion: v1  
kind: ConfigMap  
metadata:  
  name: {{.Values.name}}  
data:  
{{.Values.env.config | toYaml | indent 2}}  
---  
{{- end}}
```

Now, when this pipeline is executed, this resource will not be applied.

Later, you can apply a skipped manifest using the **Apply** step. Here's an example using a Kubernetes Job:

<!-- ![](./static/5ec2523eac5fa7169bf2101a9b4920cfe7aa2efe688075fea0ee57f775c0b05b.png) -->

<docimage path={require('./static/5ec2523eac5fa7169bf2101a9b4920cfe7aa2efe688075fea0ee57f775c0b05b.png')} />
