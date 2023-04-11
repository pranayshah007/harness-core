# Create queries to pull data into your DORA dashboard

After you [create a DORA metrics dashboard](../../platform/18_Dashboards/create-dashboards.md), you can [add tiles](https://developer.harness.io/docs/platform/dashboards/create-dashboards/#step-2-add-tiles-to-a-dashboard) to the dashboard. 

You can edit, delete, resize, move postions, or download data of the tiles. 

Using the DORA metrics dashboard, you can view the following metrics.

* **Deployments Frequency** tells you how many deployments happened in a particular duration. This metric helps measure the consistency of software delivery and delivery performance. 
* **Mean Time to Restore (MTTR)** tells you the time taken to restore an issue found in the production environment.  
* **Change Failure Rate** is the percentage of failure rate across all services in a given time period. 

![](.static/../static/dora-dashboard.png)

To gain deeper insights, you can create queries in the dashboard to capture data. Harness captures metrics for each service-environment combination within a pipeline. If you have a multi-service pipeline, metrics for each service-environment combination will be captured and reported separately.
