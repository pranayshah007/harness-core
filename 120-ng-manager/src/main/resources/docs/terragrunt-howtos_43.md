# YAML examples

The following YAML examples shows you how to quickly add Terragrunt steps into your CD stages.

```mdx-code-block
import Tabs2 from '@theme/Tabs';
import TabItem2 from '@theme/TabItem';
```
```mdx-code-block
<Tabs2>
  <TabItem2 value="Terragrunt Plan" label="Terragrunt Plan" default>
```

Here is an example of the YAML for a Terragrunt Plan step:

```yaml
              - step:
                  type: TerragruntPlan
                  name: Terragrunt Plan_1
                  identifier: TerragruntPlan_1
                  spec:
                    configuration:
                      command: Apply
                      configFiles:
                        store:
                          type: Github
                          spec:
                            gitFetchType: Branch
                            connectorRef: vlprerequisites
                            branch: main
                            folderPath: terragrunt/
                      moduleConfig:
                        terragruntRunType: RunModule
                        path: qa/local-file-resource
                      secretManagerRef: harnessSecretManager
                      backendConfig:
                        type: Inline
                        spec:
                          content: |-
                            resource_group_name  = "tfResourceGroup"
                            storage_account_name = "vlicaterraformremoteback"
                            container_name       = "azure-backend"
                      environmentVariables:
                        - name: ARM_CLIENT_ID
                          value: <+secrets.getValue("account.vl_tg_azure_client_id")>
                          type: String
                        - name: ARM_CLIENT_SECRET
                          value: <+secrets.getValue("account.vl_tg_azure_client_secret")>
                          type: String
                        - name: ARM_TENANT_ID
                          value: <+secrets.getValue("account.vl_tg_azure_tenant_id")>
                          type: String
                      varFiles:
                        - varFile:
                            identifier: vasd12312311
                            spec:
                              content: |-
                                count_of_null_resources = "7"
                                file_message = "testing inherit 111"
                            type: Inline
                    provisionerIdentifier: planinherit123aa1
                  timeout: 10m
```

```mdx-code-block
  </TabItem2>
  <TabItem2 value="Terragrunt Apply" label="Terragrunt Apply">
```

Here is an example of the YAML for a Terragrunt Apply step that inherits from the previous Terragrunt Plan step:

```yaml
              - step:
                  type: TerragruntApply
                  name: Terragrunt Apply_1
                  identifier: TerragruntApply_1
                  spec:
                    configuration:
                      type: InheritFromPlan
                    provisionerIdentifier: planinherit123aa1
                  timeout: 10m
```

```mdx-code-block
  </TabItem2>
  <TabItem2 value="Terragrunt Destroy" label="Terragrunt Destroy">
```

Here is an example of the YAML for a Terragrunt Destroy step that inherits from the previous Terragrunt Apply step:

```yaml
              - step:
                  type: TerragruntDestroy
                  name: Terragrunt Destroy_1
                  identifier: TerragruntDestroy_1
                  spec:
                    provisionerIdentifier: planinherit123aa1
                    configuration:
                      type: InheritFromApply
                  timeout: 10m
```

```mdx-code-block
  </TabItem2>
  <TabItem2 value="Terragrunt Rollback" label="Terragrunt Rollback">
```
Here is an example of the YAML for a Terragrunt Rollback step:

```yaml
            rollbackSteps:
              - step:
                  type: TerragruntRollback
                  name: Terragrunt Rollback_1
                  identifier: TerragruntRollback_1
                  spec:
                    provisionerIdentifier: abc123abc
                    delegateSelectors: []
                  timeout: 10m
```


```mdx-code-block
  </TabItem2>

</Tabs2>
```

