## Basic

The basic deployment execution strategy uses the **Deploy Cloud Function** step. The Deploy Cloud Function step deploys the new function version and routes 100% of traffic over to the new function version.

<details>
<summary>YAML example of Deploy Cloud Function step</summary>

```yaml
          execution:
            steps:
              - step:
                  name: Deploy Cloud Function
                  identifier: deployCloudFunction
                  type: DeployCloudFunction
                  timeout: 10m
                  spec: {}
```

</details>

