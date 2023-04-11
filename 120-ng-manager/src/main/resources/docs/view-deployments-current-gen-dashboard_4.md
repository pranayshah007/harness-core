# Example: Deployments and Services Dashboard

Here's an example that explains how you can create your own Dashboard to view your Deployment and Services data.

1. In Harness, click **Dashboards**.
2. In **Dashboards**, click **+ Dashboard**.

   ![](./static/view-deployments-current-gen-dashboard-43.png)

3. In **About the Dashboard**, in **Folder**, select **Organization Shared Folder**.
4. In **Name**, enter a name for your dashboard. For example, Deployments\_test.
5. (Optional) In **Tags**, type a name for your tag and press enter to create a tag, and click **Continue**.
6. Click **Edit Dashboard**.
   
   ![](./static/view-deployments-current-gen-dashboard-45.png)

7. Click **Add Tile**.
   
   ![](./static/view-deployments-current-gen-dashboard-46.png)

8. Select an Explore to get started. An Explore is a starting point for a query, designed to explore a particular subject area. The data shown in an Explore is determined by the dimensionsand measures you select from the field picker.
	* **Dimension**: A dimension can be thought of as a group or bucket of data.
	* **Measure**: A measure is information aboutthat bucket of data.
  
  ![](./static/view-deployments-current-gen-dashboard-48.png)

1.  Click the Explore that corresponds to the fields you want to include in your dashboard. For example, Deployments and Services.
2.  Enter a name for your tile. This will be the name of the tile on the dashboard.
   
   ![](./static/view-deployments-current-gen-dashboard-49.png)

3.  Select the dimensions and measures for your query. In this example, the following filters are used to aggregate data for the last 7 days:  

	* **Deployments**: Pipeline Name, Failed Deployments, and Total Deployments
	* **Services**: Failed Service Deployments, Successful Service Deployments, and Total Service Deployments
4.  Configure your visualization options. For more information, see [Create Visualizations and Graphs](../../platform/18_Dashboards/create-visualizations-and-graphs.md).
5.  Once you have set up your query, click **Run**.
   
   ![](./static/view-deployments-current-gen-dashboard-50.png)

6.  Click **Save** to save the query as a tile on your dashboard.
7.  You can add multiple tiles to your Dashboard. For example, add a tile for total and failed Service Deployments in the last 7 days.
   
   ![](./static/view-deployments-current-gen-dashboard-51.png)

8.  Once you have set up your query, click **Run** and then click **Save**. All the tiles are added to your Dashboard.
   
   ![](./static/view-deployments-current-gen-dashboard-52.png)
