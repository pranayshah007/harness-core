## select()

* **Syntax:** `select(string, string)`
* **Description:** Select attribute values using a path.
* **Parameters:** literal string, string (typically, the second string is `httpResponseBody`). This is a path to identify the desired JSON attribute value from a JSON response.

**Example:**

Here is the JSON array that we want to select a value from:


```json
{  
  "data": {  
    "attributes": {  
      "name": "new-construction-api",  
      "version_pins": {  
        "mvn-service://new-construction-api": "0.0.253-feature_NC-6595.8d268cd~nc1.6312a66"  
      }  
    }  
  }  
}
```

You can find this example at <https://raw.githubusercontent.com/wings-software/harness-docs/main/functors/select.json>.

To select the value `0.0.253-feature_NC-6595.8d268cd~nc1.6312a66`, you would use `select()` to specify the path to the value, like this:


```json
<+json.select("data.attributes.version_pins.mvn-service://new-construction-api", httpResponseBody)>
```

The `httpResponseBody` argument is used to indicate that we want to select the path *within* the HTTP response body. `httpResponseBody` is propagated from the HTTP request.

A common use of `select()` is in an HTTP step.

For example, the following HTTP step uses a variable named **book** and the `select()` method in **Value** to obtain the value `0.0.253-feature_NC-6595.8d268cd~nc1.6312a66` from the HTTP response payload at the URL specified in **URL**.

![](./static/json-and-xml-functors-06.png)

When this HTTP step is executed, in its **Output** tab, you can see the HTTP response in **HTTP Response Body** and the selection in the **Output Variables**:

![](./static/json-and-xml-functors-07.png)

You can also use a Shell Script step to echo the book output like this:


```bash
echo <+pipeline.stages.Functors.spec.execution.steps.jsonselect.output.outputVariables.book>
```

When the Pipeline is executed, the value of **book** is output:

![](./static/json-and-xml-functors-08.png)
