# Step 1: Add a Jira Create Step

In a Harness CD or Approval stage, in **Execution**, click **Add Step**.

Click **Jira Create**. The Jira Create step appears.

![](./static/create-jira-issues-in-cd-stages-00.png)

In **Name**, enter a name that describes the step.

In **Timeout**, enter how long you want Harness to try to create the issue before failing (and initiating the stage or step [Failure Strategy](../../../platform/8_Pipelines/define-a-failure-strategy-on-stages-and-steps.md)).

In **Jira Connector**, create or select the [Jira Connector](../../../platform/7_Connectors/connect-to-jira.md) to use.

In **Project**, select a Jira project from the list. A Jira project is used to create the issue key and ID when the issue is created. The unique issue number is created automatically by Jira.

In **Issue Type**, select a Jira issue type from the list of types in the Jira project you selected.
