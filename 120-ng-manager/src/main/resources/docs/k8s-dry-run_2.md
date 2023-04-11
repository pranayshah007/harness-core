# Add the Dry Run step

You add the Dry Run step before the deployment step(s) in your stage (such as the Apply, Rolling, Canary, Blue Green deployment steps). 

You can add an [Approval](https://developer.harness.io/docs/category/approvals/) step after the Dry Run step to have a Harness user(s) validate the manifest output before deployment.

For example, here is a stage with a Dry Run step followed by an Approval step and subsequent Rolling Deployment step.

![dry run](static/9feaeaab45b1c59f1ca71b4d1eb9936a03690198586f9f1d752b98710d6ccd6a.png)

To add the Dry Run step, do the following:

```mdx-code-block
import Tabs from '@theme/Tabs';
import TabItem from '@theme/TabItem';
```
```mdx-code-block
<Tabs>
  <TabItem value="Visual" label="Visual" default>
```

1. In the CD stage **Execution**, select **Add Step**.
2. Select the **Dry Run** step.
3. Enter a name for the step.
4. In **Timeout**, enter how long this step should run before failing and initiating the step or stage [failure strategy](https://developer.harness.io/docs/platform/pipelines/w_pipeline-steps-reference/step-failure-strategy-settings/).

    You can use:

   - `w` for weeks.
   - `d` for days.
   - `h` for hours.
   - `m` for minutes.
   - `s` for seconds.
   - `ms` for milliseconds.

   The maximum is `53w`.

   Timeouts can be set at the pipeline-level also, in the pipeline **Advanced Options**.
5. Select **Apply Changes**.

The Dry Run step is ready.

```mdx-code-block
  </TabItem>
  <TabItem value="YAML" label="YAML">
```

1. In **Pipeline Studio**, select **YAML**.
2. Paste the following YAML example and select **Save**:

```
              - step:
                  type: K8sDryRun
                  name: Output Service Manifests
                  identifier: OutputService
                  spec: {}
                  timeout: 10m
```

```mdx-code-block
  </TabItem>
</Tabs>
```
