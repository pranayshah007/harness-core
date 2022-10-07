# Handling Different Scopes for the same Resource

Since we are passing the organization and project identifiers as Path parameters, we will be creating multiple API endpoints for the same resource and functionality (for those resources that are present on more than one scope). To tackle the problem of cluttering our API documentation because of this, we will be using x-tagGroups in order to group our resource endpoint Tags together.

This gives us a near folder-like structure in our documentation which would look something like this:

```
- Secrets
  * Account Secrets
    + CRUD endpoints for Account Scoped Secrets
  * Organization Secrets
    + CRUD endpoints for Organization Scoped Secrets
  * Project Secrets
    + CRUD endpoints for Project Scoped Secrets
- Roles
  * Account Roles
    + CRUD endpoints for Account Scoped Roles
  * Organization Roles
    + CRUD endpoints for Organization Scoped Roles
  * Project Roles
    + CRUD endpoints for Project Scoped Roles
```

When adding a new Resource to the [harness-openapi](https://github.com/harness/harness-openapi) repository, details of our added x-tagGroups and Tags must be updated in the file x-tags.json inside the merge folder. This folder will be used to merge our different spec files to create a single merged file, hosted on our documentation tool, [Redocly](https://redocly.com/).

This process is automated using the [Merge Spec-First pipeline](https://app.harness.io/ng/#/account/vpCkHKsDSxK9_KYfjCTMKA/ci/orgs/default/projects/MISC/pipelines/Merge_SpecFirst/input-sets?storeType=INLINE).