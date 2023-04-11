# this is also associated with the service configuration

      artifacts:
        primary:
          primaryArtifactRef: <+input>
          sources:
            - spec:
                connectorRef: public_dockerhub
                imagePath: library/nginx
                tag: <+input>
              identifier: nginx
              type: DockerRegistry
            
 ## SERVICE v2 UPDATE
 ## NEW CAPABILITY: We now have service variables that are mapped and managed with the service no longer defined in the pipeline
 ## This can be overwritten when deploying the service to different environments
 
 
      variables:
        - name: canaryName
          type: String
          description: ""
          value: colors-canary
        - name: host
          type: String
          description: ""
          value: nginx-canary.harness.io
        - name: name
          type: String
          description: ""
          value: colors
        - name: stableName
          type: String
          description: ""
          value: colors-stable
  gitOpsEnabled: false

```
