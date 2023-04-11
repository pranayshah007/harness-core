## Deploy with or without gates

CD deployments are typically preformed using manual approvals before deploying changes to production. These approvals are often called approval gates or release gates. 

Gates are checkpoints in the deployment process that can provide several benefits, including increased control, improved quality, compliance and security, stakeholder involvement, and better risk management.

However, gates can also slow down the deployment process, requiring manual intervention and increasing the time it takes to get changes to production.

CD deployments without gates, also known as "no-gate CD," refers to a CD process that does not require manual approval before deploying changes to production.

This approach has several advantages, including faster time to market and increased collaboration.

Of course, no-gate CD is not appropriate for all organizations and situations, and it may require significant investment in automation and testing to ensure that changes are deployed safely and reliably. 

Harness supports gated and no-gate CD by default. You can use several approval stages or steps in your pipelines, or simply deploy without gates.

For information on approval stages and steps, go to:

- [Using manual harness approval stages](https://developer.harness.io/docs/platform/Approvals/adding-harness-approval-stages)
- [Adding Jira approval stages and steps](https://developer.harness.io/docs/platform/Approvals/adding-jira-approval-stages)
- [Adding ServiceNow approval steps and stages](https://developer.harness.io/docs/platform/Approvals/service-now-approvals)
