# Option: Use an Expression in Issue Key

In **Issue Key**, you can use an expression to reference the Key ID from another Jira Create or Jira Update step.

The Jira Create or Jira Update step you want to reference must be before the Jira Update step that references it in the stage.

First, identify the step where you want to get the ID from. In this example, we'll use a Jira Create step.

You'll have to close the Jira Update step to get the the ID from the previous step. An ID is required, so you can just enter any number for now and click **Save**.In the Pipeline, click **Execution History**.

Select a successful execution, and click the Jira Create step in the execution.

Click the **Output** tab, locate the **Key** setting, and click the copy button.

![](./static/update-jira-issues-in-cd-stages-15.png)

The expression will look something like this:

`<+pipeline.stages.Jira_Stage.spec.execution.steps.jiraCreate.issue.key>`

Now you have the expression that references the key ID from this step.

Go back to your Jira Update step. You can just select **Edit Pipeline**.

In **Issue Key**, select **Expression**.

![](./static/update-jira-issues-in-cd-stages-16.png)

**Issue Key**, paste in the expression you copied from the previous Jira Create step.

Now this Jira Update step will update the issue created by the Jira Create step.

Some users can forget that when you use a Jira Create step it creates a new, independent Jira issue every time it is run. If you are using the same issue ID in Jira Update, you are updating a new issue every run.
