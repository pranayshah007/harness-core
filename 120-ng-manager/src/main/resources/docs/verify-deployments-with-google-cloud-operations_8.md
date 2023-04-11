# Option: Cloud Metrics

1. Select Cloud Metrics and click **Next.** The **Select Dashboard** settings appear.
2. Click one or more dashboards from the displayed results. If you don't have any preexisting dashboards or don't want to select the ones in the displayed result, click **Manually input query.**
   ![](./static/verify-deployments-with-google-cloud-operations-127.png)
3. Click **Next** and the **Customize Health Source** settings appear.
4. Select a query from the options displayed on the left side of setting panel. The specification of the selected query auto-populates in the fields under **Query Specifications**.
   ![](./static/verify-deployments-with-google-cloud-operations-128.png)
5. In **Configure Risk Profile**, select one of the following risk types in **Risk Category**:
   
   | Risk Type | Type of Values | Deviation Type | Default Delta | Default Ratio |
   | --- | --- | --- | --- | --- |
   | Error | Web / Business transactions | Higher is bad | 0 | 0 |
   | Infrastructure | Cpu, memory .... | Higher and Lower is bad | 20 | 0.2 |
   | Performance/Other | Cpu, memory .... | Higher and Lower is bad | 20 | 0.2 |
   | Performance/Throughput | Web / Business transactions | Lower is bad | 20 | 0.2 |
   | Performance/Response time | Web / Business transactions | Higher is bad | 20 | 0.2 |

6. In **Deviation Compared to Baseline**, select one of the options based on the selected risk type.
7. Click **Submit**. The Health Source is displayed in the Verify step.
