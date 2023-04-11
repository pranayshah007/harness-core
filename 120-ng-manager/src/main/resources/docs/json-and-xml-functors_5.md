## format()

* **Syntax:** `format(object)`
* **Description:** Format the array passed as the string value in JSON format.
* **Parameters:** object. Typically, this is the response from the HTTP response body (`httpResponseBody`). The `httpResponseBody` argument is used to indicate that we want to select the path within the HTTP response body. `httpResponseBody` is propagated from the HTTP request.

**Example:**

We add an HTTP step to obtain the `httpResponseBody` and then reference that in a subsequent Shell Script step.

We are using the example at <https://raw.githubusercontent.com/wings-software/harness-docs/main/functors/select.json>.

If we simply rendered the `httpResponseBody`, we would get:


```json
{data:{attributes:name:new-construction-api}} {data:{attributes:version_pins:{mvn-service://new-construction-api:0.0.253-feature_NC-6595.8d268cd~nc1.6312a66}}}
```
If we render it using `<+json.format(<+pipeline.stages.Functors.spec.execution.steps.jsonformat1.output.httpResponseBody>)>` we get a JSON formatted string:


```json
{\n  "data": {\n    "attributes": {\n      "name": "new-construction-api",\n      "version_pins": {\n        "mvn-service://new-construction-api": "0.0.253-feature_NC-6595.8d268cd~nc1.6312a66"\n      }\n    }\n  }\n}\n
```

:::important

1. JSON accepts the control sequence `\n` as strings. To format JSON, use `jq` to prettify the JSON.

2. Conditional expressions within double quotes are considered strings.
   
   So, `"<+json.select("fields.status.name", httpResponseBody)>"=="In Progress"` is treated as string comparison and will not work.
   
   Use `<+json.select("fields.status.name", httpResponseBody)>=="In Progress"` instead.
   
   The keyword null, too, shouldn't be enclosed in quotes during comparison. 
   
   Here's an example of a null comparison:
   `<+json.object(httpResponseBody).fields.parent>!=null`

:::
