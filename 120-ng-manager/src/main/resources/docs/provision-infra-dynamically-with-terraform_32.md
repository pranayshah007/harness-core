# Terraform Rollback

The **Terraform Rollback** step is automatically added to the **Rollback** section.

![](./static/provision-infra-dynamically-with-terraform-07.png)

1. Open **Terraform Rollback**.
2. Enter a name for the step.
3. In **Provisioner Identifier**, enter the same Provisioner Identifier you used in the Terraform Plan and Apply steps.
   
   <!-- ![](./static/provision-infra-dynamically-with-terraform-08.png) -->
   
   <docimage path={require('./static/provision-infra-dynamically-with-terraform-08.png')} />
4. Click **Apply Changes**.

When rollback happens, Harness rolls back the provisioned infrastructure to the previous successful version of the Terraform state.

Harness won't increment the serial in the state, but perform a hard rollback to the exact version of the state provided.

Harness determines what to rollback using the **Provisioner Identifier**.

If you've made these settings expressions, Harness uses the values it obtains at runtime when it evaluates the expression.
