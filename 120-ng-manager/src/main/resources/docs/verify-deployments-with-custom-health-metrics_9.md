# Option: Custom Health Logs

1. If you select Custom Health Logs, the **Customize Health Source** settings appear as:
   
   ![](./static/verify-deployments-with-custom-health-metrics-92.png)

2. Click **Query specifications and mapping**.
3. In **Query Name**, enter a name for the query. For example Custom Log Query.
4. In **Request Method**, you can select **GET** or **POST**. If you select POST, you need to define the body format.
5. In **Path**, enter the complete path of the metric. For example,`v2/logs/events/search`.
6. In **Start and End Time Placeholders**, enter the following:
	1. In **Start time placeholder**, enter the start time placeholder in the metric path. For example, start\_time.
	2. In **Unit**, select the preferred unit of measurement. For example, Milliseconds.
	3. In **End time placeholder**, enter the end time placeholder in the metric path. For example, end\_time.
	4. In **Unit**, select the preferred unit of measurement. For example, Milliseconds.
7. In **Body**, enter the request body. For example,`{"filter":{"query":","from":start_time,"to":end_time}}`.
8. Click **Fetch Records** to retrieve records from the provided URL.
   
   ![](./static/verify-deployments-with-custom-health-metrics-93.png)

9.  Once the response is retrieved, click **JSON path selection**.
	1. In **Log Message JSON path**, click the plus icon to select the path to the log message from the data source. For example,`$.data.[*].attributes.message`.
	2. In **Timestamp Field/Locator JSON** Path, click the plus icon to select the path to the log message from the data source. For example,`$.data.[*].attributes.timestamp`.
	3. In **Provide Service Instance to map to Harness Service Instance**, click the plus icon to select the Service instance from the data source. For example,`$.data.[*].attributes.tags.[4]`.

	![](./static/verify-deployments-with-custom-health-metrics-94.png)

1.  Click **Submit**.
