# Terragrunt steps

You can add Terragrunt steps anywhere in your CD stage's **Execution**. The most common order is Terragrunt Plan -> Terragrunt Apply -> Terragrunt Destroy. 

You add the Terragrunt Rollback step in the stage **Rollback** section.

The following sections describe how to set up each of the Terragrunt steps in your CD stage.

```mdx-code-block
import Tabs from '@theme/Tabs';
import TabItem from '@theme/TabItem';
```
```mdx-code-block
<Tabs>
  <TabItem value="Terragrunt Plan" label="Terragrunt Plan" default>
```

To add a Terragrunt Plan step, do the following:

1. In your CD stage Execution, click **Add Step**, and then click **Terragrunt Plan**.
2. Enter the following Terragrunt Plan settings.
