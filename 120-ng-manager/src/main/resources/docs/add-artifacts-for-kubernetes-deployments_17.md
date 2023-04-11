## Option: Custom

:::note

Currently, this feature is behind the Feature Flag `CUSTOM_ARTIFACT_NG`. Contact [Harness Support](mailto:support@harness.io) to enable the feature.

:::

You can select a Custom Artifact Source to add your custom repository.

Select **Custom** and click **Continue**.

![](./static/add-artifacts-for-kubernetes-deployments-15.png)

The **Artifact Details** settings appear.

![](./static/add-artifacts-for-kubernetes-deployments-16.png)

In **Version**, enter your artifact version.

Click **Submit**.

You can only set an Artifact version number when using a Custom Artifact Source. The version you select determines the Artifact context for the deploy stage. You may refer to it anywhere in the stage by using the expression `<+artifact.version>`.
