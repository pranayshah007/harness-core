## Sample request for project level migration API

The `<base_url>` is usually `app.harness.io`.

```curl
curl --location --request POST 'https://<base_url>/gateway/ng/api/service-env-migration/project?accountIdentifier=account_id' \
--header 'content-type: application/yaml' \
--header 'Authorization: auth_token' \
--data-raw '{
 "orgIdentifier": '\''org_identifier'\'',
 "projectIdentifier": '\''project_identifier'\'',
 "infraIdentifierFormat": '\''<+stage.identifier>_<+pipeline.identifier>_infra'\'',
  "isUpdatePipeline": true,
  "templateMap" : 
      {
          "source_template_ref@ source_template_version": {
              "templateRef" : "target_template_version",
              "versionLabel" : "v1"
          }
      },
      "skipInfras": ["abc"],
      "skipServices": ["abc"],
      "skipPipelines": ["def"]
}'
```
