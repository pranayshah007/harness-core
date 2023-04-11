## Sample response for project level migration request

```json
{
    "status": "SUCCESS",
    "data": {
        "failures": [
            {
                "orgIdentifier": "org_identifier",
                "projectIdentifier": "project_identifier",
                "pipelineIdentifier": "pipeline_identifier",
                "stageIdentifier": "stage_identifier",
                "failureReason": "service of type v1 doesn't exist in stage yaml"
            }
        ],
        "migratedPipelines": ["def"]
    },
    "metaData": null,
    "correlationId": "9ed00aca-d788-441e-a636-58661ef36efe"
}
```
