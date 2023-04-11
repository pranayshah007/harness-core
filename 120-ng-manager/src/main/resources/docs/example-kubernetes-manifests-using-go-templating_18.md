# Verbatim

Use `indent` and `toYaml` to put something from the values file into the manifest verbatim.


```go
{{.Values.env.config | toYaml | indent 2}}
```