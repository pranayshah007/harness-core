# Step 5: Add Health Sources

This option is available only if you have configured the service and environment as fixed values.

A Health Source is basically a mapping of a Harness Service to the service in a deployment environment monitored by an APM or logging tool.

A Health Source is a combination of the following:

* Health Source type. This is the APM or logging tool monitoring the deployment environment. For example, AppDynamics or Splunk.
* Connection to the APM or logging tool monitoring the deployment environment. This is set up as a Harness Connector.
* The APM or logging tool feature used in monitoring. For example, AppDynamics Application Monitoring.
* The APM or logging tool settings that let you select the deployment environment. For example, an AppDynamics application and tier.
* Metric Pack: the metrics you want to use in verification. For example, Errors and Performance.

In **Health Sources**, click **Add**. The **Add New Health Source** settings appear.

![](./static/verify-deployments-with-the-verify-step-24.png)

In **Select health source type**, select your APM or logging tool. Each tool requires its own Health Source.

In **Health Source Name**, enter a name for the Health Source.

Click in **Select Connector**.

In the **Connector** settings, select a Connector to your APM or logging tool or click **New Connector**.

Here's an example of an AppDynamics Connector's settings:

![](./static/verify-deployments-with-the-verify-step-25.png)

When your Connector is set up, click **Apply Selected**. The Connector is added to the Health Source.

![](./static/verify-deployments-with-the-verify-step-26.png)

In **Select Feature**, select the APM or logging tool component to use. For example, AppDynamics **Application Monitoring**.

Click **Next**.

The settings in **Customize Health Source** will depend on the Health Source Type you selected. For example, here are the Customize Health Source settings for an AppDynamics Health Source:

![](./static/verify-deployments-with-the-verify-step-27.png)

The AppDynamics application and tier are selected to map the Harness Service to the monitored environment.

In **Metric Packs**, select the metrics you want Harness to use.

Click **Validation Passed** or **No Data** to see the data received from the tool. Some metrics might not have data at this time.

![](./static/verify-deployments-with-the-verify-step-28.png)

Click **View calls to [Provider Name]** to see the requests and responses of each call.

Click **Submit**. The Health Source is displayed in the Verify step.

![](./static/verify-deployments-with-the-verify-step-29.png)

You can add more Health Sources for each APM or logging provider.
