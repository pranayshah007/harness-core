## Canary

Harness provides a [step group](https://developer.harness.io/docs/continuous-delivery/cd-technical-reference/cd-gen-ref-category/step-groups/) to perform the canary deployment. 

The step group consists of:

- Deploy Cloud Function With No Traffic step: deploys the new function version but does not route any traffic over to the new function version.
- First Cloud Function Traffic Shift step: routes 10% of traffic over to the new function version.
- Second Cloud Function Traffic Shift step: routes 100% of traffic over to the new function version.


<details>
<summary>YAML example of canary deployment step group</summary>

```yaml
          execution:
            steps:
              - stepGroup:
                  name: Canary Deployment
                  identifier: canaryDepoyment
                  steps:
                    - step:
                        name: Deploy Cloud Function With No Traffic
                        identifier: deployCloudFunctionWithNoTraffic
                        type: DeployCloudFunctionWithNoTraffic
                        timeout: 10m
                        spec: {}
                    - step:
                        name: Cloud Function Traffic Shift
                        identifier: cloudFunctionTrafficShiftFirst
                        type: CloudFunctionTrafficShift
                        timeout: 10m
                        spec:
                          trafficPercent: 10
                    - step:
                        name: Cloud Function Traffic Shift
                        identifier: cloudFunctionTrafficShiftSecond
                        type: CloudFunctionTrafficShift
                        timeout: 10m
                        spec:
                          trafficPercent: 100
```

</details>
