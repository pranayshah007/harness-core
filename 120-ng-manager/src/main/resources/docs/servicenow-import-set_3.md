# Add a ServiceNow Import Set step

You can add a ServiceNow Import Set step anywhere in CD, approval, or custom stages where you need to import data using a staging table.

1. In a Harness CD, approval, or custom stage, in **Execution**, click **Add Step**, and then select **ServiceNow Import Set**.
2. Enter a name for the step.
3. Enter a timeout period for the step. Once the timeout expires, Harness will initiate the step or stage [failure strategy](../../../platform/Pipelines/8_Pipelines/../../8_Pipelines/define-a-failure-strategy-on-stages-and-steps.md).
4. In **ServiceNow Connector**, select or create the [Harness ServiceNow Connector](../../../platform/7_Connectors/connect-to-service-now.md) to use.
