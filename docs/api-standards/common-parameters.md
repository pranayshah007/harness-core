# Common Parameters 




## Default Resource Fields


| Field Name  | Type              | Validation | Required      | User Defined | User Editable | Description                                                                                                                |
|-------------|-------------------|------------|---------------|--------------|---------------|----------------------------------------------------------------------------------------------------------------------------|
| name        | string            |            | yes           | yes          | yes           | Human-friendly name for the resource                                                                                       |
| slug        | string            |            | yes           | yes          | no            | URL-friendly version of the name, used to identify a resource within it's scope and so needs to be unique within the scope |
| description | string            |            | no            | yes          | yes           | Further detail on the specific resource                                                                                    |
| tags        | map[string]string |            | no            | yes          | yes           | List of labels applied to the resource                                                                                     |
| org         | string            |            | type-specific | yes          | no            | Slug field of the organization the resource is scoped to                                                                   |
| project     | string            |            | type-specific | yes          | no            | Slug field of the project the resource is scoped to                                                                        |
| created     | int64             |            | yes           | no           | no            | Unix timestamp when the resource was created in milliseconds                                                               |
| updated     | int64             |            | yes           | no           | no            | Unix timestamp when the resource was last edited in milliseconds                                                           |
|             |                   |            |               |              |               |                                                                                                                            |



## List Query Parameters

| Field Name | Type    | Default | Validation     | Description                                                                                                                                                                                     |
|------------|---------|---------|----------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| order      | string  | desc    | enum(asc,desc) | Order to sort on                                                                                                                                                                                |
| sort       | string  | none    |                | The field to sort against. Note: only fields with good indexing strategies should be allowed for sorting. Typical default to internal creation consistent order eg mongo `_id`field or similar. |
| limit      | int     | 30      | min(1)         | Pagination: Number of items to return                                                                                                                                                           |
| page       | int     | 1       | min(1)         | Pagination page number strategy: Specify the page number within the paginated collection related to the number of items in each page                                                            |
| after      |         | none    |                | Pagination cursor strategy: Returns items after the given cursor value within sort criteria.                                                                                                    |
| before     |         | none    |                | Pagination cursor strategy: Returns items before the given cursor value within sort criteria.                                                                                                   |
| org        | string  | none    |                | Limit to provided org slugs                                                                                                                                                                     |
| project    | string  | none    |                | Limit to provided project slugs                                                                                                                                                                 |
| slug       | string  | none    |                | Limit to the provided resource slugs                                                                                                                                                            |
| tag        | string  | none    |                | Limit to the provided tags                                                                                                                                                                      |
| recursive  | boolean | false   |                | Expand current scope to include all child scopes within the hierarchy                                                                                                                           |






## Item Query Parameters

| Field Name | Description | Default | Type | Validation |
|------------|-------------|---------|------|------------|
|            |             |         |      |            |


## Common Request Headers


## Common Response Headers

| Header Name  | Type   | Description |
|--------------|--------|-------------|
| X-Request-ID | string | A short random string to corolate a request to a log message within the bounds of an account and relative time. eg urlencoded base64 of 6 bytes |
