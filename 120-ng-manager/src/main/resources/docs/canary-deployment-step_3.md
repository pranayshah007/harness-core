# YAML

```YAML
              - step:
                  type: K8sCanaryDeploy
                  name: Canary Deploy
                  identifier: Canary_Deploy
                  spec:
                    skipDryRun: false
                    instanceSelection:
                      type: Count
                      spec:
                        count: 1
                  timeout: 10m
```



