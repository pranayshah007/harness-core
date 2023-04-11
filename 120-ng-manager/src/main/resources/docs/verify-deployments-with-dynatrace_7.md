# Step 5: Add Health Sources

This option is available only if you have configured the service and environment as fixed values.

A Health Source is basically a mapping of a Harness Monitored Service to the Service in a deployment environment monitored by an APM or logging tool.

In **Health Sources**, click **Add**. The **Add New Health Source** settings appear.

![](./static/verify-deployments-with-dynatrace-14.png)

1. In **Select health source type**, select **Dynatrace**.
2. In **Health Source Name**, enter a name for the Health Source. For example Quickstart.
3. Under **Connect Health Source**, click **Select Connector**.
4. In **Connector** settings, you can either choose an existing connector or click **New Connector** to create a new **Connector.**
   
   ![](./static/verify-deployments-with-dynatrace-15.png)

5. After selecting the connector, click **Apply Selected**. The Connector is added to the Health Source.
6. In **Select Feature**, a Dynatrace feature is selected by default.
7. Click **Next**. The **Customize Health Source** settings appear.
   
   The subsequent steps in **Customize Health Source** depend on the Health Source type you selected.
   
   ![](./static/verify-deployments-with-dynatrace-16.png)
   	
8. In **Find a Dynatrace service**, enter the name of the desired Dynatrace service.
9.  In **Select Metric Packs to be monitored,** you can select **Infrastructure** or **Performance** or both.
10. Click **Add Metric** if you want to add any specific metric to be monitored (optional) or simply click **Submit.**
11. If you click Add Metric, click **Map Metric(s) to Harness Services**.
12. In **Metric Name**, enter the name of the metric.
13. In **Group Name**, enter the group name of the metric.
14. Click **Query Specifications and mapping**.
15. In **Metric**, choose the desired metric from the list.
16. Click **Fetch Records** to retrieve data for the provided query.
17. In **Assign**, choose the services for which you want to apply the metric. Available options are:
	* Continuous Verification
	* Health Score
	* SLI
18. In **Risk Category**, select a risk type.
19. In **Deviation Compared to Baseline**, select one of the options based on the selected risk type.
    ![](./static/verify-deployments-with-dynatrace-17.png)
20. Click **Submit**. The Health Source is displayed in the Verify step.

You can add one or more Health Sources for each APM or logging provider.
