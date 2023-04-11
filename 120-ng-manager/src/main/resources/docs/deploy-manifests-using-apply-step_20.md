# YAML example of Apply step

Here's a YAML example of an Apply step.

```yaml
...
          - step:
              type: K8sApply
              name: Apply DB Migration Job
              identifier: Apply_DB_Migration_Job
              spec:
                filePaths:
                  - database-migration.yaml
                skipDryRun: false
                skipSteadyStateCheck: true
                skipRendering: false
                commandFlags: 
                  - commandType: Apply
                    flag: "--dry-run=true --server-side"
                overrides:
                  - manifest:
                      identifier: DBValues
                      type: Values
                      spec:
                        store:
                          type: Github
                          spec:
                            connectorRef: account.ThisRohanGupta
                            gitFetchType: Branch
                            paths:
                              - migration-values.yaml
                            repoName: Product-Management
                            branch: "main"
              timeout: 10m
              failureStrategies:
                - onFailure:
                    errors:
                      - AllErrors
                    action:
                      type: Retry
                      spec:
                        retryCount: 3
                        retryIntervals:
                          - 2s
                        onRetryFailure:
                          action:
                            type: StageRollback
...
```

