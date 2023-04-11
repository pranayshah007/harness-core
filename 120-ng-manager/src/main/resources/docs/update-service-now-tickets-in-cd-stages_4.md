# Step 1: Add a ServiceNow Update Step

In a Harness CD or Approval stage, in **Execution**, click **Add Step**.

Click **ServiceNow Update**. The ServiceNow Update settings appear.

In **Name**, enter a name that describes the step.

In **Timeout**, enter how long you want Harness to try to update the issue before failing (and initiating the stage or step [Failure Strategy](../../../platform/8_Pipelines/define-a-failure-strategy-on-stages-and-steps.md)).

In **ServiceNow Connector**, create or select the [ServiceNow Connector](../../../platform/7_Connectors/connect-to-service-now.md) to use.

In **Ticket Type**, select a ServiceNow ticket type from the list.

![](./static/update-service-now-tickets-in-cd-stages-22.png)

In **Ticket Number** enter the ServiceNow ticket number to update. You can enter fixed values, provide runtime input, or provide an expression for this field.

![](./static/update-service-now-tickets-in-cd-stages-23.png)
