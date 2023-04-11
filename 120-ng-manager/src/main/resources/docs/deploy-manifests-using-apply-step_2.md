## Apply step failures

In the event of deployment failure, Harness will not roll back the Apply step action because there is no record of its state when the Apply step is performed. 

We recommend users define rollback steps in the **Rollback** section of the stage to undo the Apply step(s) actions. 

Harness will roll back any infrastructure or deployments that happened prior to the failed step.
