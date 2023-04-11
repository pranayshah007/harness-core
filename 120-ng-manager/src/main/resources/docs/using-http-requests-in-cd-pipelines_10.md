# Simulate load by running steps in parallel

You can use multiple HTTP steps in parallel to simulate load.

Simply create a Step Group and add multiple HTTP steps with the same URL pointing to your service.

![](./static/using-http-requests-in-cd-pipelines-34.png)

The steps are executed in parallel and simulate load.

You can add multiple steps to the group quickly using YAML. Just paste additional steps into the Step Group. Be sure to rename each step. Here's an example:


```yaml
...  
- stepGroup:  
  name: Simulate Load  
  identifier: Simulate_Load  
  steps:  
      - parallel:  
            - step:  
                  type: Http  
                  name: Load 1  
                  identifier: Load_1  
                  spec:  
                      url: http://example.com  
                      method: GET  
                      headers: []  
                      outputVariables: []  
                  timeout: 10s  
            - step:  
                  type: Http  
                  name: Load 2  
                  identifier: Load_2  
                  spec:  
                      url: http://example.com  
                      method: GET  
                      headers: []  
                      outputVariables: []  
                  timeout: 10s  
            - step:  
                  type: Http  
                  name: Load 3  
                  identifier: Load_3  
                  spec:  
                      url: http://example.com  
                      method: GET  
                      headers: []  
                      outputVariables: []  
                  timeout: 10s  
  failureStrategies: []  
  spec: {}  
...
```



