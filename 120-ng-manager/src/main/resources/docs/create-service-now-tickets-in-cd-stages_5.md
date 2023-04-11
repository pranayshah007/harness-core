# Step 1: Add a ServiceNow Create Step

In a Harness CD or Approval stage, in **Execution**, click **Add Step**.

![](./static/create-service-now-tickets-in-cd-stages-05.png)

Click **ServiceNow Create**. The ServiceNow Create settings appear.

![](./static/create-service-now-tickets-in-cd-stages-06.png)

In **Name**, enter a name that describes the step.

In **Timeout**, enter how long you want Harness to try to create the issue before failing (and initiating the stage or step [Failure Strategy](../../../platform/8_Pipelines/define-a-failure-strategy-on-stages-and-steps.md)).

In **ServiceNow Connector**, create or select the [ServiceNow Connector](../../../platform/7_Connectors/connect-to-service-now.md) to use.

In **Ticket Type**, select a ServiceNow ticket type from the list.

![](./static/create-service-now-tickets-in-cd-stages-07.png)
