### Sample payload request

```json
{
  "identifier": "string",
  "orgIdentifier": "string",
  "projectIdentifier": "string",
  "name": "string",
  "description": "string",
  "tags": {
    "property1": "string",
    "property2": "string"
  },
  
// NEW PART OF THE SERVICE API PAYLOAD
// The YAML is optional when using the API for service creation
// The YAML is mandatory if the service will be used in a pipeline
// YAML is the service definition YAML passed as a string

  "yaml": "string" 
}
```
