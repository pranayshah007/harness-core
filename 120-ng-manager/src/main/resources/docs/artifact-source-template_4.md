# Create an artifact source template

To create an artifact source template, do the following:

```mdx-code-block
import Tabs from '@theme/Tabs';
import TabItem from '@theme/TabItem';
```
```mdx-code-block
<Tabs>
  <TabItem value="Visual" label="Visual" default>
```
1. In a Harness project, org, or account settings, select **Templates**.
   1. Project:
      1. Select a module, such as **Deployments**.
      2. In **Project Setup**, click **Templates**.
   2. Org:
      1. Select an org.
      2. In **Organization Resources**, select **Templates**.
   3. Account:
      1. Select **Account Resources**.
      2. Select **Templates**.
2. Select **New Template**, and then select **Artifact Source**.

    The artifact source template appears.

    ![picture 1](static/05724ade849eca8316a4538ab02e64fc2342d7c34c7b5294f99c7153f4437b60.png)
3. Enter a name for the artifact source template. Use a name that describes the artifact source so that team members understand what artifact is represents.
4. In **Version Label**, enter a version for the template. You can update the version each time you change and save a template.
5. In **Logo**, upload an icon image for the template.
6. If you are creating this template at the Harness project level, in **Save To**, select where you want to save the template. For more information, go to [Saving templates to project, org, or account level](#saving-templates-to-project-org-or-account-level).
7. Select **Start**.
