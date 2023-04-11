# These variables are globally defined and can be accessed when the environment is referenced in the pipeline

  variables:
    - name: db_host
      type: String
      value: postgres-staging
      description: ""
    - name: DB_PASS_STAGING
      type: Secret
      value: Rohan_QA
      description: ""
 
 ## ENVIRONMENT v2 UPDATE    
 ## Environment-specific property files like values.yaml can now be mapped to the environment as well as in the manifest block

  overrides:
    manifests:
      - manifest:
          identifier: staging
          type: Values
          spec:
            store:
              type: Github
              spec:
                connectorRef: ProductManagementRohan
                gitFetchType: Branch
                paths:
                  - cdng/staging-values.yaml
                repoName: Product-Management
                branch: main

```
