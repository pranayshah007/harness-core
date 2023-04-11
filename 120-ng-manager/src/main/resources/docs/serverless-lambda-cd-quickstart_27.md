### Serverless YAML for Docker images

```yaml
service: example-service  
frameworkVersion: '2 || 3'  
  
provider:  
  name: aws  
  
functions:  
  hello:  
    image: <+artifact.image>
```
