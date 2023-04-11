#### Environment REST Request changes

```json
{
  "orgIdentifier": "string",
  "projectIdentifier": "string",
  "identifier": "string",
  "tags": {
    "property1": "string",
    "property2": "string"
  },
  "name": "string",
  "description": "string",
  "color": "string",
  "type": "PreProduction",
  
// ENVIRONMENT v2 UPDATE
// You can now pass in the environment variables and overrides via YAML payload 
// NOTE: This field is not mandatory for environment creation or usage in a pipeline

  "yaml": "string"
}
```
