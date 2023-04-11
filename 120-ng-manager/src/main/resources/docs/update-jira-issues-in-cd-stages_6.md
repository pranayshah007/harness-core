## Option: Status and Transition

In **Status**, enter the status type (Issue Action) to update the issue with (In Progress, Done, etc). Harness will automatically update the issue with this status.

In **Transition Name**, enter the name of the transition to move the issues into (for example, `Transition to`, `PR Testing`, `Ready for Test`).

![](./static/update-jira-issues-in-cd-stages-17.png)

If the issue is not part of a Jira workflow and does not have transition options, then the step will fail.See [Statuses and transitions](https://support.atlassian.com/jira-cloud-administration/docs/work-with-issue-workflows/#Workingwithworkflows-steps) from Atlasssian.
