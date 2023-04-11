# Step 5: Add Health Sources

This option is available only if you have configured the service and environment as fixed values.

A Health Source is basically a mapping of a Harness Service to the service in a deployment environment monitored by an APM or logging tool.

In **Health Sources**, click **Add**. The **Add New Health Source** settings appear.

![](./static/verify-deployments-with-datadog-51.png)

In **Select health source type**, select Datadog.

In **Health Source Name**, enter a name for the Health Source.

Under **Connect Health Source**, click **Select Connector**.

In **Connector** settings, you can either choose an existing connector or click **New Connector.**

![](./static/verify-deployments-with-datadog-52.png)

Click **Apply Selected**. The Connector is added to the Health Source.

In **Select Feature**, select the Datadog feature to be used. You can choose **Cloud Metrics** or **Cloud Logs**.

![](./static/verify-deployments-with-datadog-53.png)

Click **Next**.

The subsequent settings in **Customize Health Source** depend on the Health Source Type you selected. ### Option: Cloud Metrics

If you select **Cloud Metrics**, the **Select Dashboards** settings appear. It displays a list of Datadog dashboards.

![](./static/verify-deployments-with-datadog-54.png)

Click the dashboards of your choice. If you don't have any preexisting dashboards or don't want to select the ones in the displayed result, click **Manually input query.**

Click **Next** and the **Customize Health Source** settings appear.

Select a query from the options displayed on the left side of setting panel. The specification of the selected query auto-populates in the fields under **Query Specifications**.

![](./static/verify-deployments-with-datadog-55.png)

Click **Submit**. The Health Source is displayed in the Verify step.
