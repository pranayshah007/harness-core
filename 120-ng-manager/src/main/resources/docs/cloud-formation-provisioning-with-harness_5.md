# CloudFormation Rollback

When rollback happens, Harness rolls back the provisioned infrastructure/resources to the previous successful version of the CloudFormation stack.

Harness won't increment the serial in the state, but perform a hard rollback to the exact version of the state provided.

Harness determines what to rollback using the **Provision Identifier** entered in the **CloudFormation Rollback** step.

If you've made these settings using Harness expressions, Harness uses the values it obtains at runtime when it evaluates the expression.

See [Rollback Provisioned Infra with the CloudFormation Rollback Step](rollback-provisioned-infra-with-the-cloud-formation-rollback-step.md).

