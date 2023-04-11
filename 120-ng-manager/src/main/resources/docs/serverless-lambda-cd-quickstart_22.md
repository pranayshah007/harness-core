### Docker sidecars

Here's an example of a serverless.yaml referencing primary and sidecar artifacts:

```yaml
...  
functions:  
  hello:  
    image: <+artifact.image>  
  hello1:  
    image: <+artifacts.sidecars.mysidecar>  
...
```
