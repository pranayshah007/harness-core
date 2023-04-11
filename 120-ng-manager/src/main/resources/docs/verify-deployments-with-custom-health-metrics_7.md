# Step 5: Add Health Sources

This option is available only if you have configured the service and environment as fixed values.

A Health Source is basically a mapping of a Harness Service to the service in a deployment environment monitored by an APM or logging tool.

In **Health Sources**, click **Add**. The **Add New Health Source** settings appear.

![](./static/verify-deployments-with-custom-health-metrics-90.png)

1. In **Select health source type**, select **Custom Health**.
2. In **Health Source Name**, enter a name for the Health Source. For example Quickstart.
3. In **Connect Health Source**, click **Select Connector**.
4. In **Connector** settings, you can either choose an existing connector or click **New Connector.**
5. Click **Apply Selected**. The Connector is added to the Health Source.
6. In **Select Feature**, select the feature to be monitored. You can either select **Custom Health Metrics** or **Custom Health Logs**.
7. Click **Next**.
