### Referencing variables

You reference variables using a Harness expression with the syntax `<+infra.variables.[variable Id]>`. You can reference any setting in the variable entity, such as a connector's URL.

Here are some examples.

First, here's the variables:

![](static/refvars.png)

Here's the expressions referencing these variables:

```
<+infra.variables.git_connector.spec.url>

<+infra.variables.git_connector.spec.authentication.type>

<+infra.variables.git_connector.spec.authentication.spec.type>

<+infra.variables.git_connector.spec.authentication.spec.spec.username>

<+infra.variables.git_connector.spec.authentication.spec.spec.tokenRef>

<+secrets.getValue(<+infra.variables.git_connector.spec.authentication.spec.spec.tokenRef.scope> + "." +<+infra.variables.git_connector.spec.authentication.spec.spec.tokenRef.identifier>)>

<+stage.spec.infrastructure.output.variables.gitSecret>

<+infra.variables.gitSecret>

<+infra.variables.test1>

<+infra.variables.test12>
```
:::note

If the secret in the connector is project level, then use `<+secrets.getValue(<+infra.variables.git_connector.spec.authentication.spec.spec.tokenRef.identifier>)>` instead of `<+secrets.getValue(<+infra.variables.git_connector.spec.authentication.spec.spec.tokenRef.scope> + "." +<+infra.variables.git_connector.spec.authentication.spec.spec.tokenRef.identifier>)>` in the above expressions example. The latter expression is for a secret used in the connector at an account or organization level.

:::
