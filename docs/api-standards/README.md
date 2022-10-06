# Harness API Guidelines

This repo holds the collection of guides on writing APIs within Harness. 

## REST

Harness uses a Representational State Transfer (RESTful) style API to expose functionality. While other options are available, RESTful APIs continue to be some of the most popular and robust public HTTP based APIs and so the standard our customers are used to interacting with.

RESTful APIs are focused on resources, how to group them, and a set of actions that can be taken against them. A resource is a noun. Resources are grouped into collections and actions taken against them. Given RESTful APIs are generally built on top of the HTTP protocol, resources are mapped to URL endpoints and HTTP verbs are used to handle the default actions to be taken against a resource or collection.


## Process For Change
For creating APIs to expose new functionality as well as for redesigning currently implemented APIs, we will be using the Spec-First approach. In this approach, we first create the OpenAPI specification (in JSON or YAML form) for the resource, and then we use code generator tools like swagger-codegen to create server-stubs. 

The link for understanding how we practically do this process can be found [here](https://harness.atlassian.net/wiki/spaces/PLATFORM/pages/21071988993/Server+stub+generation).

Next, we register a new dependency in harness-core and implement the generated server-stubs to write the implementation for our resource.

_Note: As of now, the focus is on moving only our customer-facing APIs using this process._