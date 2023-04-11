# Option: Cloud Logs

Select Cloud Logs and click **Next.** The **Customize Health Source** settings appear.

You can customize the metrics to map the Harness Service to the monitored environment in **Query Specifications and Mapping** settings.

1. Click **Map Queries to Harness Services** drop down.
2. Enter a name for the query in **Name your Query**.
3. In **Query**, enter the query and click **Fetch Records.** The retrived results appear in **Records**.
4. In **Identify Service Instance**, click the plus icon to select the path of the service instance.
5. In **Identify Message**, click the plus icon to select the message identifier.

![](./static/verify-deployments-with-google-cloud-operations-129.png)

Click **Submit**. The Health Source is displayed in the Verify step.

You can add one or more Health Sources for each APM or logging provider.
