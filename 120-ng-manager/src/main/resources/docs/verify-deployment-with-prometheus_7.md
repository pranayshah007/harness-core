# Step 5: Add Health Sources

This option is available only if you have configured the service and environment as fixed values.

A Health Source is basically a mapping of a Harness Service to the service in a deployment environment monitored by an APM or logging tool.

In **Health Sources**, click **Add**. The **Add New Health Source** settings appear.

![](./static/verify-deployment-with-prometheus-77.png)

1. In **Select health source type**, select Prometheus.
2. In **Health Source Name**, enter a name for the Health Source.
3. Under **Connect Health Source**, click **Select Connector**.
4. In **Connector** settings, you can either choose an existing connector or click **New Connector.**
   ![](./static/verify-deployment-with-prometheus-78.png)
1. After selecting the connector, click **Apply Selected**. The Connector is added to the Health Source.
   ![](./static/verify-deployment-with-prometheus-79.png)
2. In **Select Feature**, select the Prometheus feature to be used.
3. Click **Next**. The **Customize Health Source** settings appear.

   The subsequent settings in **Customize Health Source** depend on the Health Source Type you selected. You can customize the metrics to map the Harness Service to the monitored environment.

   ![](./static/verify-deployment-with-prometheus-80.png)
   
1. In **Query Specifications and Mapping**, first click **Map Metric(s) to Harness Services**.
2. Enter the desired metric name in **Metric** **Name**.
3. Enter a name for the Prometheus group in **Group Name**.
4. Click **Build your Query** drop down.
5. In **Prometheus Metric**, select the Prometheus metric.
6. In **Filter on Environment**, select a filter.
7. In **Filter on Service**, select a filter. To add more filters, click **Additional Filter** which is optional.
8. To add an aggregator for the metric, click **Aggregator** which is also optional.
9. In **Assign**, you can select the services for which you want to apply the metric.![](./static/verify-deployment-with-prometheus-81.png)
10. Click **Submit**. The Health Source is displayed in the Verify step.

You can add one or more Health Sources for each APM or logging provider.
