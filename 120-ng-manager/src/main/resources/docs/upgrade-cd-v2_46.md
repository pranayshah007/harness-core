### Sample stage template

```yaml
template:
  name: Deploy
  identifier: Deploy
  type: Stage
  projectIdentifier: Rohan
  orgIdentifier: default
  tags: {}
  spec:
    type: Deployment
    spec:
 
 ## SERVICE + ENVIRONMENT v2 UPDATE
 ## deploymentType. This scopes the stage config to a Harness deployment type.
 ## Steps, services, environments, and infrastructure definitions are all scoped to the stage's deployment type. This prevents incompatible config usage.
 
      deploymentType: Kubernetes
      
 ## SERVICE + ENVIRONMENT v2 UPDATE
 ## serviceref. This is now a reference to the service object that is configured and managed outside the pipeline.
 ## serviceInputs. These are the runtime inputs that users provide for the artifact when they deploy the particular service.
 
      service:
        serviceRef: <+input>
        serviceInputs: <+input>
        
 ## SERVICE + ENVIRONMENT v2 UPDATE
 ## environmentref. This is now a reference to the environment object that is configured and managed outside the pipeline.
 ## infrastructureDefinitions. This object is defined outside of the pipeline and is referenced using the identifier. The YAML is inserted into the stage definition once defined.
 
      environment:
        environmentRef: <+input>
        deployToAll: false
        environmentInputs: <+input>
        infrastructureDefinitions: <+input>
      execution:
        steps:
          - step:
              name: Rollout Deployment
              identifier: rolloutDeployment
              type: K8sRollingDeploy
              timeout: 10m
              spec:
                skipDryRun: false
                pruningEnabled: false
          - step:
              type: ShellScript
              name: Shell Script
              identifier: ShellScript
              spec:
                shell: Bash
                onDelegate: true
                source:
                  type: Inline
                  spec:
                    script: kubectl get pods -n <+infra.namespace>
                environmentVariables: []
                outputVariables: []
              timeout: 10m
          - step:
              type: Http
              name: HTTP
              identifier: HTTP
              spec:
                url: https://google.com
                method: GET
                headers: []
                outputVariables: []
              timeout: 10s
        rollbackSteps:
          - step:
              name: Rollback Rollout Deployment
              identifier: rollbackRolloutDeployment
              type: K8sRollingRollback
              timeout: 10m
              spec:
                pruningEnabled: false
    failureStrategies:
      - onFailure:
          errors:
            - AllErrors
          action:
            type: StageRollback
  versionLabel: "2.0"

```
