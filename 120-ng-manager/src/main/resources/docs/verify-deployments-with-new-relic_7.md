# Step 5: Add Health Sources

This option is available only if you have configured the service and environment as fixed values.

A Health Source is basically a mapping of a Harness Service to the service in a deployment environment monitored by an APM or logging tool.

1. In **Health Sources**, click **Add**. The **Add New Health Source** settings appear.
   
   ![](./static/verify-deployments-with-new-relic-05.png)
    
2. In **Select health source type**, select New Relic.
3. In **Health Source Name**, enter a name for the Health Source.
4. Under **Connect Health Source**, click **Select Connector**.
5. In **Connector** settings, you can either choose an existing connector or click **New Connector.**

    ![](./static/verify-deployments-with-new-relic-06.png)

6. Click **Apply Selected**. The Connector is added to the Health Source.
7. In **Select Feature**, select the NewRelic feature to be used.
    
    ![](./static/verify-deployments-with-new-relic-07.png)
    
8. Click **Next**. The **Customize Health Source** settings appear.
    The subsequent settings in **Customize Health Source** depend on the Health Source Type you selected. You can customize the metrics to map the Harness Service to the monitored environment. In Applications & Tiers, enter the following details:
9.  In **Find a New Relic application** type the name of an application.
10. In **Find an AppDynamics tier** type a tier name from which you want usage metrics, code exceptions, error conditions, or exit calls.
11. In **Metric Packs** select the metrics you want Harness to use.
12. Click **Submit**. The Health Source is displayed in the Verify step.

You can add one or more Health Sources for each APM or logging provider.
