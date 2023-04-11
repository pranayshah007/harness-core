# Output

Create output variables to be used by other steps in the stage. The **Value** setting can contain any HTTP step input, output, or response information.

You can also use ​JSON and XML functors in the values for the output variable. For example, `<+json.select("data.attributes.version_pins.mvn-service://new-construction-api", httpResponseBody)>`.

You can use pipeline variables along with httpResponseBody and httpResponseCode. Some examples are listed below: 
`<+json.object(httpResponseBody).title>`

`<+json.select(<+pipeline.variables.title>, httpResponseBody)>`

To concatenate strings within the JSON functor:

`<+json.select(<+ <+pipeline.variables.user> + <+pipeline.variables.id>>,httpResponseBody)>` or

`<+json.select("user".concat(<+pipeline.variables.id>),httpResponseBody)>`


For more information, go to [JSON and XML Functors](json-and-xml-functors.md).
