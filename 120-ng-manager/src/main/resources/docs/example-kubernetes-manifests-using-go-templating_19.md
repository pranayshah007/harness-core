# Indexing Structures in Templates

If the data passed to the template is a map, slice, or array it can be indexed from the template.

You can use `{{index x number}}` where `index` is the keyword, `x` is the data, and `number` is an integer for the `index` value.

If we had `{{index names 2}}` it is equivalent to `names[2]`. We can add more integers to index deeper into data. `{{index names 2 3 4}}` is equivalent to `names[2][3][4]`.

Let's look at an example:


```yaml
{{- if .Values.env.config}}  
apiVersion: v1  
kind: ConfigMap  
metadata:  
 name: {{.Values.name}}-{{.Values.track}}  
 labels:  
 app: {{.Values.name}}  
 track: {{.Values.track}}  
 annotations:  
 harness.io/skip-versioning: "true"  
data:  
{{- if hasKey .Values.env .Values.track}}  
{{index .Values.env .Values.track "config" | mergeOverwrite .Values.env.config | toYaml | indent 2}}  
{{- else }}  
{{.Values.env.config | toYaml | indent 2}}  
{{- end }}  
---  
{{- end}}  
  
{{- if .Values.env.secrets}}  
apiVersion: v1  
kind: Secret  
metadata:  
 name: {{.Values.name}}-{{.Values.track}}  
 labels:  
 app: {{.Values.name}}  
 track: {{.Values.track}}  
stringData:  
{{- if hasKey .Values.env .Values.track}}  
{{index .Values.env .Values.track "secrets" | mergeOverwrite .Values.env.secrets | toYaml | indent 2}}  
{{- else }}  
{{.Values.env.secrets | toYaml | indent 2}}  
{{- end }}  
---  
{{- end}}
```
