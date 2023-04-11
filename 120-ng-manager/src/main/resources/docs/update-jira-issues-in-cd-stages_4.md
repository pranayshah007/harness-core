# Step 1: Add a Jira Update Step

In a Harness CD or Approval stage, in **Execution**, click **Add Step**.

Click **Jira Update**. The Jira Update step appears.

![](./static/update-jira-issues-in-cd-stages-14.png)

In **Name**, enter a name that describes the step.

In **Timeout**, enter how long you want Harness to try to update the issue before failing (and initiating the stage or step [Failure Strategy](../../../platform/8_Pipelines/define-a-failure-strategy-on-stages-and-steps.md)).

In **Jira Connector**, create or select the [Jira Connector](../../../platform/7_Connectors/connect-to-jira.md) to use.

In **Project**, select the Jira project that contains the issue you want to update.

In **Issue Key**, enter the Jira issue key of the issue you want to update.
