### **Add Amazon Managed Service for Prometheus as health source**

:::note

Currently, this feature is behind the feature flag `SRM_ENABLE_HEALTHSOURCE_AWS_PROMETHEUS`. Contact [Harness Support](mailto:support@harness.io) to enable the feature.

:::

Harness now supports Amazon Managed Service for Prometheus as health source. To select Amazon Managed Service for Prometheus as health source:

1. In Health Sources, click **Add**.   
The Add New Health Source settings appear.
2. In **Select health source type**, select **Prometheus**.
3. In **Health Source Name**, enter a name for the Health Source.
4. Under **Connect Health Source** > **Via Cloud Provider**, select Amazon web services.
   
   ![](./static/verify-deployment-with-prometheus-82.png)

5. Under **Connect Health Source**, click **Select Connector**.
6. In **Connector** settings, you can either choose an existing connector or click **New Connector.**
7. In the **Select Feature** field, select the Prometheus feature that you want to use.
8. In the **Select AWS Region** field, select the appropriate region.
9.  In the **Select Workplace Id** field, select the appropriate workplace id.
10. Click **Next**. The **Customize Health Source** settings appear.  
You can customize the metrics to map the Harness Service to the monitored environment in **Query Specifications and Mapping** settings.The subsequent settings in **Customize Health Source** depend on the Health Source Type you selected. Click **Map Queries to Harness Services** drop down.
1.  Click **Add Metric**.
2.  Enter a name for the query in **Name your Query**.
3.  Click **Select Query** to select a saved query. This is an optional step. You can also enter the query manually in the **Query** field.
4.  Click **Fetch Records** to retrieve the details. The results are displayed under **Records.**
