# Headers

Headers are `key:value` pairs. For example:

* `Content-Type: application/json`
* `Content-Type: text/html`

You can use a Fixed Value, Runtime Input, or Expression.

You can use [Harness variables](../../../platform/12_Variables-and-Expressions/harness-variables.md), too.

You can reference [Harness secrets](../../../platform/6_Security/2-add-use-text-secrets.md) in the **Value** setting, too.

For example, in **Key**, enter `Token` .

In **Value**, enter `<+secrets.getValue("aws-playground_AWS_secret_key")>`.
