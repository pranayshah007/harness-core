## Rollbacks

If deployment failure occurs, the stage or step [failure strategy](../../../platform/Pipelines/8_Pipelines/../../8_Pipelines/define-a-failure-strategy-on-stages-and-steps.md) is initiated. Typically, this runs the **Rollback Cloud Function** step in the **Rollback** section of **Execution**. Harness adds the Rollback Cloud Function step automatically. 

The Harness rollback capabilities are based on the Google Cloud Function [revisions](https://cloud.google.com/run/docs/managing/revisions) available in Google Cloud.

If the function already exists (for example, revision 10) and a new revision is deployed (for example, revision 11) but a step after the deployment there is a failure and rollback is triggered, then Harness will deploy a new function revision (revision **12**) but it will contain the artifact and metadata for revision 10.
