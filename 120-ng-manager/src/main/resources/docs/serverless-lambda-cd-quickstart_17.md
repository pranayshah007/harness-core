## Serverless manifest supports Harness secrets and expressions

The serverless.yaml file you use with Harness can use Harness secret and built-in expressions.

Expression support lets you take advantage of Runtime Inputs and Input Sets in your serverless.yaml files. For example, you could use a Stage variable as a runtime input to change plugins with each stage deployment:

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
  - <+stage.variables.devplugins>
```

See:

* [Add and Reference Text Secrets](../../../platform/6_Security/2-add-use-text-secrets.md)
* [Built-in Harness Variables Reference](../../../platform/12_Variables-and-Expressions/harness-variables.md)
* [Run Pipelines using Input Sets and Overlays](../../../platform/8_Pipelines/run-pipelines-using-input-sets-and-overlays.md)
