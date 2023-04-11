### Option: Custom Health Metrics

1. If you select Custom Health Metrics, the **Customize Health Source** settings appear as:
   
   ![](./static/verify-deployments-with-custom-health-metrics-91.png)

2. Click **Map Metric(s) to Harness Services**.
3. In **Metric Name**, enter the name of the metric.
4. In **Group Name**, click **Add New** and enter a name for the metric group.
5. Click **Query specifications and mapping.**
6. In **Query Type** you can choose either **Service Based (used for Health Score and SLI)** or **Host Based (used for CV)**.
	If you select the query type as **Host Based** (Continuous Verification), the verification won't happen for SLI and Health Score (Service Based), and vice versa.
1. In **Request Method**, you can select **GET** or **POST**. If you select POST, you need to define the body format.
2. In **Path**, enter the complete path of the metric.
3. In **Start and End Time Placeholders**, enter the following:
	1. In **Start time placeholder**, enter the start time placeholder in the metric path.
	2. In **Unit**, select the preferred unit of measurement.
	3. In **End time placeholder**, enter the end time placeholder in the metric path.
	4. In **Unit**, select the preferred unit of measurement.
4. Click **Fetch Records** to retrieve records from the provided URL.
5. Click **Metric values and charts**.
6. In **Timestamp Format**, enter a static value in dd/mm/yy format.
7. Click **Assign**. Select the services for which you want to apply the metric. You can select **Health Score** or **SLI** or both options.
   The subsequent steps depend on the service you select in this step.1. In **Risk Category**, select a risk type. Available options for risk types are:
	* Errors
	* Infrastructure
	* Performance/Throughput
	* Performance/Other
	* Performance/Response Time
8. In **Deviation compared to Baseline**, select one of the options based on the selected risk type. Available options are:
	* **Higher value is higher risk** - Select this option if a high value of the selected risk type is a risk.
	* **Lower value is higher risk** - Select this option if lower value of the selected risk type is a risk.
9.  Click **Submit**.
