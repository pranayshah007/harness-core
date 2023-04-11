## Stages

The stage definition changes when the service and environment v2 update is enabled.

Stages now have a deployment type, a service reference, an environment reference, and an infrastructure definition. These settings must be defined along with the **Execution** steps of the stage.

```yaml
    - stage:
        name: Deploy
        identifier: Deploy
        description: ""
        type: Deployment
        spec:
        
 ## SERVICE + ENVIRONMENT v2 UPDATE
 ## deploymentType. This scopes the stage config to one of the deployment types that Harness offers 
 ## Steps, services, environments, infrastructure definitions are all scoped to the stage's deployment type. This prevents incompatible config usage
 
          deploymentType: Kubernetes
          
 ## SERVICE + ENVIRONMENT v2 UPDATE
 ## serviceref. This is now a reference to the service object that is configured and managed outside the pipeline
 ## serviceInputs. These are the runtime inputs that users provide for the artifact when they deploy the particular service
 
          service:
            serviceRef: nginxcanary
            serviceInputs:
              serviceDefinition:
                type: Kubernetes
                spec:
                  artifacts:
                    primary:
                      primaryArtifactRef: <+input>
                      sources: <+input>
                      
 ## SERVICE + ENVIRONMENT v2 UPDATE
 ## environmentref. This is now a reference to the environment object that is configured and managed outside the pipeline
 ## infrastructureDefinitions. This object is defined outside of the pipeline and is referenced via the identifier. The YAML is inserted into the stage definition once defined
 
          environment:
            environmentRef: staging
            deployToAll: false
  
            infrastructureDefinitions:
              - identifier: productstaging
                inputs:
                  identifier: productstaging
                  type: KubernetesDirect
                  spec:
                    namespace: <+input>.allowedValues(dev,qa,prod)
          execution:
            steps:
              - stepGroup:
                  name: Canary Deployment
                  identifier: canaryDepoyment
                  steps:
                    - step:
                        name: Canary Deployment
                        identifier: canaryDeployment
                        type: K8sCanaryDeploy
                        timeout: 10m
                        spec:
                          instanceSelection:
                            type: Count
                            spec:
                              count: 1
                          skipDryRun: false
                    - step:
                        name: Canary Delete
                        identifier: canaryDelete
                        type: K8sCanaryDelete
                        timeout: 10m
                        spec: {}
              - stepGroup:
                  name: Primary Deployment
                  identifier: primaryDepoyment
                  steps:
                    - step:
                        name: Rolling Deployment
                        identifier: rollingDeployment
                        type: K8sRollingDeploy
                        timeout: 10m
                        spec:
                          skipDryRun: false
            rollbackSteps:
              - step:
                  name: Canary Delete
                  identifier: rollbackCanaryDelete
                  type: K8sCanaryDelete
                  timeout: 10m
                  spec: {}
              - step:
                  name: Rolling Rollback
                  identifier: rollingRollback
                  type: K8sRollingRollback
                  timeout: 10m
                  spec: {}
        tags: {}
        failureStrategies:
          - onFailure:
              errors:
                - AllErrors
              action:
                type: StageRollback
```
