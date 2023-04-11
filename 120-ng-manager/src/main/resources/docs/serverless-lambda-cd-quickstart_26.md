### Serverless YAML for files

```yaml
service: example-service  
frameworkVersion: '2 || 3'  
  
provider:  
  name: aws  
  runtime: nodejs12.x  
functions:  
  hello:  
    handler: handler.hello  
    events:  
      - httpApi:  
          path: /tello  
          method: get    
package:  
  artifact: <+artifact.path>  
plugins:  
  - serverless-deployment-bucket@latest
```
