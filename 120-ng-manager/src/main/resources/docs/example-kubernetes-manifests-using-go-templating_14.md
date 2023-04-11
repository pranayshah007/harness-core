## templates/namespace.yaml

The namespace manifest references the namespace value from values.yaml.


```yaml
{{- if .Values.createNamespace}}  
apiVersion: v1  
kind: Namespace  
metadata:  
  name: {{.Values.namespace}}  
{{- end}}
```
