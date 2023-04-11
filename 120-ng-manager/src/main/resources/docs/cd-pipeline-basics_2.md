## Stages

A CD Stage is a subset of a Pipeline that contains the logic to perform one major segment of the deployment process. Stages are based on the different milestones of your release process, such as dev, qa, and prod releases, and approvals.

This table explains how Stages perform your CD operations:



|                                   |                                           |
| --------------------------------- | ----------------------------------------- |
| **Your CD Process**                           | **CD Pipelines**                          |
| What you are deploying            | Service and Service Definition            |
| Where you are deploying it        | Environment and Infrastructure Definition |
| How you are deploying it          | Execution steps and Failure Strategy      |
| When you want it deployed         | Triggers                                  |
| Who can approve deployment stages | Approval steps and stages                 |


See the following:

* [Add a Stage](../../../platform/8_Pipelines/add-a-stage.md)
* [Add a Stage Template Quickstart](../../../platform/13_Templates/add-a-stage-template.md)
* [Stage and Step Conditional Execution Settings](../../../platform/8_Pipelines/w_pipeline-steps-reference/step-skip-condition-settings.md)
