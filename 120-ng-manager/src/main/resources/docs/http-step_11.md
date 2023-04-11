## Outputs

In the following examples, the Id of the HTTP step is `HTTP`.

| **Output** | **Output Reference Example** | **Output Value Example** |
| --- | --- | --- |
| httpUrl | `<+pipeline.stages.HTTP.spec.execution.steps.HTTP.output.httpUrl>` | `https://www.google.com/search?q=` |
| httpMethod | `<+pipeline.stages.HTTP.spec.execution.steps.HTTP.output.httpMethod>` | `GET` |
| httpResponseCode | `<+pipeline.stages.HTTP.spec.execution.steps.HTTP.output.httpResponseCode>` | `200` |
| httpResponseBody | `<+pipeline.stages.HTTP.spec.execution.steps.HTTP.output.httpResponseBody>` | `Hello` |
| status | `<+pipeline.stages.HTTP.spec.execution.steps.HTTP.output.status>` | `SUCCESS` |
