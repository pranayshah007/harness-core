# Step 5: Add Health Sources

This option is available only if you have configured the service and environment as fixed values.

A Health Source is basically a mapping of a Harness Service to the service in a deployment environment monitored by an APM or logging tool.

In **Health Sources**, click **Add**. The **Add New Health Source** settings appear.

1. In **Select health source type**, select Google Cloud Operations.
   
   ![](./static/verify-deployments-with-google-cloud-operations-124.png)

2. In **Health Source Name**, enter a name for the Health Source.
3. Under **Connect Health Source**, click **Select Connector**.
4. In **Connector** settings, you can either choose an existing connector Gcp connector or click **New Connector.**
   ![](./static/verify-deployments-with-google-cloud-operations-125.png)

5. Click **Apply Selected**. The Connector is added to the Health Source.
6. In **Select Feature**, select the feature to be used.

![](./static/verify-deployments-with-google-cloud-operations-126.png)

The subsequent settings in **Customize Health Source** depend on the Health Source Type you selected. 
