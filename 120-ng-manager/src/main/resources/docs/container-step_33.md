## Environment variables

Environment variables may be injected into the container and used in the script in **Command**. 

When using these environment variables, make sure to enter a **Name** and **Value** for each variable.

You may also reference environment variables in the script by their name. For example, in Bash, this would be `$var_name` or `${var_name}`. In Windows PowerShell, the reference would be `$Env:varName`.

For **Value**, you may enter [fixed values, runtime inputs, and expressions](https://developer.harness.io/docs/platform/references/runtime-inputs/).

For example, if you created the environment variables `DB_HOST`, `DB_URL`, and `DB_PASSWORD`, your script could reference them like this:

```bash
DB_URL=$DB_URL
user=$DB_HOST
password=$DB_PASSWORD
```

For example, you can set **Value** as an expression and reference the value of some other setting in the stage or pipeline.

```mdx-code-block
  </TabItem>
  <TabItem value="YAML" label="YAML">
```

1. In **Pipeline Studio**, select **YAML**
2. Paste the following YAML example and select **Save**:

```yaml
              - step:
                  type: Container
                  name: Test
                  identifier: Test
                  spec:
                    connectorRef: Docker_Hub_with_Pwd
                    image: maven:3.6.3-jdk-8
                    command: echo "Run some smoke tests"
                    shell: Sh
                    infrastructure:
                      type: KubernetesDirect
                      spec:
                        connectorRef: K8s_Cluster_1675446740237
                        namespace: default
                        resources:
                          limits:
                            cpu: "0.5"
                            memory: 500Mi
                    outputVariables: []
                    envVariables: {}
                  timeout: 1d
```

```mdx-code-block
  </TabItem>
</Tabs>
```
