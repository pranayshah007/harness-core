### Sample v2 pipeline YAML

```yaml
pipeline:
  name: Nginx Colors Canary
  identifier: Nginx_Colors_Canary
  projectIdentifier: Rohan
  orgIdentifier: default
  tags: {}
  stages:
    - stage:
        name: Deploy
        identifier: Deploy
        description: ""
        type: Deployment
        spec:
          deploymentType: Kubernetes
          
 ## SERVICE + ENVIRONMENT v2 UPDATE
 ## serviceref. This is now a reference to the service object that is configured and managed outside the pipeline.
 ## serviceInputs. These are the runtime inputs that users provide for the artifact when they deploy the particular service.
 
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
 ## environmentref. This is now a reference to the environment object that is configured and managed outside the pipeline.
 ## infrastructureDefinitions. This object is defined outside of the pipeline and is referenced via the identifier. The YAML is inserted into the stage definition once defined.
 
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
