# Paths and Actions 

This document describes the default URL schema for Harness APIs.

- [Canonical Paths](#canonical-paths)
- [Actions](#actions)
- [Default REST Schema](#default-rest-schema)
- [Harness Scoping and Path Namespacing](#harness-scoping-and-path-namespacing)
  * [Project Scoped](#project-scoped)
  * [Organization Scoped](#organization-scoped)
  * [Account Scoped](#account-scoped)
- [Naming Conventions and Casing](#naming-conventions-and-casing)


## Canonical Paths

In REST the basic principle is every resource has a predictable canonical URL unique to that resource with in a a named collection, to which actions can be applied. For basic operations the structure is: 

```
{action}  /{resource-collection}/{resource-name}
GET /pets/max  # retrieve an individual pet with name max
```

_Note: the resource or collection name may itself be namespaced to allow for a more human-friendly structure while also reducing the likelihood of naming conflicts e.g_

```
GET /pets/{animal}/{breed}/{pet-name}
GET /pets/dog/jack-russel/max
GET /pets/animals/{animal}/breeds/{breed}/{pet-name}
```

## Actions

Given REST is built on top of HTTP the actions typically map to HTTP verbs allowing for CRUD operations. Any actions which do not fit within the usual CRUD operations, or helper actions my be prepended to the URL of the resource it is being applied to, typically with the POST HTTP verb.

## Default REST Schema 

| Action  | Scope      | Verb   | Path      | Notes                                 |
|---------|------------|--------|-----------|---------------------------------------|
| Create  | Collection | POST   | /pets     | Create a new pet resource             |
| List    | Collection | GET    | /pets     | Return a list of pet resources        |
| Show    | Resource   | GET    | /pets/max | Return the single pet with name “max” |
| Update  | Resource   | PATCH  | /pets/max | Update pet “max”                      |
| Upsert  |  Resource  | PUT    | /pets/max | Create or update pet “max”            |
| Destroy | Resource   | DELETE | /pets/max | Destroy pet “max”                     |

## Harness Scoping and Path Namespacing

Within each account end users can set up organizations and projects, depending on the resource type resources may be enforced to a level within this hierarchy or be available at any level [(ng docs)](https://ngdocs.harness.io/article/7fibxie636-projects-and-organizations). These act as namespaces for resources and allow for human-friendly named resources.

In order to best balance predictability and readiblity across all Harness respources the following schema should be used:

### Project Scoped

```
/orgs/{org}/projects/{project}/{resource-collection}/{resource-slug}
/orgs/north-america/projects/billing/secrets/passwords
```

### Organization Scoped

```
/orgs/{org}/{resource-collection}/{resource-slug}
/orgs/north-america/secrets/passwords
```

### Account Scoped

```
/{resource-collection}/{resource-name}
/secrets/passwords
```

## Naming Conventions and Casing 

Resources are things and so endpoint names should be nouns e.g. 

```
use: /pipelines/pipeline-name
not: /getPipeline/pipeline-name
```

Collections are plural and so endpoint names should be plural e.g.

```
use: /policies/policy-name
not: /policy/policy-name
```

Use kebab-case in resource collection names over snake_case or camelCase
```
use: /audit-trails/audit-trail-name
not: /audit_trails/audit-trail-name
not: /auditTrails/audit-trail-name
```
