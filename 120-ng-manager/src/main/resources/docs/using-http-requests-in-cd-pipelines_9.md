# Output variables

You can create output variables and reference them in other steps in the stage. The **Value** setting can contain any HTTP step input, output, or response information.

To reference the value of the output variable in another step using its fully-qualified name (FQN).

For example, here's a variable `example` with the value `1234`. The step name is `GET`.

![](./static/using-http-requests-in-cd-pipelines-33.png)

Save the step and then click **Variables**.

1. In the **Variables** drawer, copy the **example** variable.
2. In another step, like a **Shell Script** step, paste the FQN.

The FQN will resolve to the variable value at execution runtime.

You can also use ​JSON and XML functors in the values for the output variable. For example, `<+json.select("data.attributes.version_pins.mvn-service://new-construction-api", httpResponseBody)>`.

See [JSON and XML Functors](../../cd-technical-reference/cd-gen-ref-category/json-and-xml-functors.md).
