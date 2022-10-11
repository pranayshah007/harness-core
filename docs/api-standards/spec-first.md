# Spec-first API development
For creating APIs to expose new functionality as well as for redesigning currently implemented APIs, we will be using the Spec-First approach. In this approach, we first create the OpenAPI specification (in JSON or YAML form) for the resource, and then we use code generator tools like swagger-codegen to create server-stubs. 

As part of the standards, we are passing the organization and project identifiers as Path parameters and we will be creating multiple API endpoints for the same resource and functionality (for those resources that are present on more than one scope). To tackle the problem of cluttering our API documentation because of this, we will be using x-tagGroups in order to group our resource endpoint Tags together.

This gives us a near folder-like structure in our documentation which would look something like this:

```
- Connectors
  * Account Connectors
    + CRUD endpoints for Account Scoped Connectors
  * Organization Connectors
    + CRUD endpoints for Organization Scoped Connectors
  * Project Connectors
    + CRUD endpoints for Project Scoped Connectors
- Pipelines
  * Pipelines
    + CRUD endpoints for Pipelines
  * Executions
    + Execution related endpoints for Pipelines
```