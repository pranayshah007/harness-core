### Sample cURL command for pipeline level migration

The `<base_url>` is usually `app.harness.io`.

```curl
curl --location --request POST 'https://<base_url>/gateway/ng/api/service-env-migration/pipeline?accountIdentifier=account_identifier' \
--header 'content-type: application/yaml' \
--header 'Authorization: auth_token' \
--data-raw '{
 "orgIdentifier": '\''org_identifier'\'',
 "projectIdentifier": '\''project_identifier'\'',
 "infraIdentifierFormat": '\''<+stage.identifier>_<+pipeline.identifier>_infra'\'',
 "pipelineIdentifier": '\''pipeline_identifier'\'',
 "isUpdatePipeline": true,
 "templateMap" : 
      {
          "source_template_ref@ source_template_version": {
              "templateRef" : "target_template_ref",
              "versionLabel" : "target_template_version"
          }
      },
      "skipInfras": ["abc"],
      "skipServices": ["abc"],
}'
```
