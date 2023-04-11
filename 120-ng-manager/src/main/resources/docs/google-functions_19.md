## Blue Green

Harness provides a [step group](https://developer.harness.io/docs/continuous-delivery/cd-technical-reference/cd-gen-ref-category/step-groups/) to perform the blue green deployment. 

The step group consists of: 

- Deploy Cloud Function With No Traffic step: deploys the new function version but does not route any traffic over to the new function version.
- Cloud Function Traffic Shift step: routes 100% of traffic over to the new function version.

You can also route traffic incrementally using multiple Cloud Function Traffic Shift steps with gradually increasing routing percentages.


<details>
<summary>YAML example of blue green deployment step group</summary>

```yaml
          execution:
            steps:
              - stepGroup:
                  name: Blue Green Deployment
                  identifier: blueGreenDepoyment
                  steps:
                    - step:
                        name: Deploy Cloud Function With No Traffic
                        identifier: deployCloudFunctionWithNoTraffic
                        type: DeployCloudFunctionWithNoTraffic
                        timeout: 10m
                        spec: {}
                    - step:
                        name: Cloud Function Traffic Shift
                        identifier: cloudFunctionTrafficShift
                        type: CloudFunctionTrafficShift
                        timeout: 10m
                        spec:
                          trafficPercent: 100
```

</details>

