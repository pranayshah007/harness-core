# Pagination

For list endpoints pagination should be used with `page` and `limit` query parameters to dictate where in the list to get to. 

- `limit` should default to 30
- `limit` should not exceed 100

Link headers should be used for metadata and related links this can be useful for clients to move between portions of the overall list. 

```
Link: </v1/roles?page=2&limit=30>; rel="next", </v1/roles?page=1&limit=30>; rel="self", </v1/roles?page=0&limit=30>; rel="previous",
```
