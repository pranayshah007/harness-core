### Plugin support

Harness supports Serverless plugins in your serverless.yaml file.

You simply add the plugin using the standard Serverless `plugins` format and Harness adds the plugin at runtime.

```yaml
service: <+service.name>  
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
