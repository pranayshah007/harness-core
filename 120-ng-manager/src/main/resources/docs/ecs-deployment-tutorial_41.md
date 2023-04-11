### targetGroupArn is mandatory

You can see `targetGroupArn: <+targetGroupArn>` in the example above. For Harness Blue Green deployments, this is mandatory.

Harness will resolve the `<+targetGroupArn>` expression to the ARN of the first target group associated with the **Stage Listener Rule ARN** in the **ECS Blue Green Create Service** step.
