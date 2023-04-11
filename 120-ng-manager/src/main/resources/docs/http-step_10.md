## Inputs

In the following examples, the Id of the HTTP step is `HTTP`.

| **Input Name** | **Input Reference Example** | **Input Value Example** |
| --- | --- | --- |
| identifier | `<+pipeline.stages.HTTP.spec.execution.steps.check_response.identifier>` | `check\_response` |
| name | `<+pipeline.stages.HTTP.spec.execution.steps.check_response.name>` | `check response` |
| timeout | `<+pipeline.stages.HTTP.spec.execution.steps.check_response.timeout>` | `10s` |
| type | `<+pipeline.stages.HTTP.spec.execution.steps.check_response.type>` | `Http` |
| url | `<+pipeline.stages.HTTP.spec.execution.steps.check_response.spec.url>` | `https://www.google.com/search?q=` |
| method | `<+pipeline.stages.HTTP.spec.execution.steps.check_response.spec.method>` | `GET` |
| requestBody | `<+pipeline.stages.HTTP.spec.execution.steps.check_response.spec.requestBody>` | `current+date` |
| assertion | `<+pipeline.stages.HTTP.spec.execution.steps.check_response.spec.assertion>` | `<+httpResponseCode> == 200` |
