# URL for HTTP call

1. In **URL**, enter a URL for the call. It must include the `http://` scheme.

For more information on runtime inputs and expressions, go to [Fixed values runtime inputs and expressions](https://developer.harness.io/docs/platform/references/runtime-inputs/)..

You can use [Harness variables](../../../platform/12_Variables-and-Expressions/harness-variables.md), too. For example, if the Service name matches the domain name of the HTTP address, you can use `http://<+service.name>/...`.

Before handing the execution of the HTTP step to a Harness Delegate, Harness performs a capability check on the URL to ensure that a non-400 response code is returned.


<!-- ### Using secrets in the HTTP step URL

In some cases, you might want to use a [Harness text secret](../../../platform/6_Security/2-add-use-text-secrets.md) in the **URL** setting. For example, `https://www.google.com/<+secrets.getValue("xyz")>`.

It's important to know how Harness uses the secret when evaluating the URL.

Harness does not use the actual secret value, but rather it uses the secret Id. So, if you have a secret with the Id `xyz` and the value `gmail`, Harness will perform the capability check using `https://www.google.com/<<xyz>>`. This will fail the capability check.

If you use a Harness text secret for the entire URL, the capability check will fail. -->
