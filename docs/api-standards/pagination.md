# Pagination

For list endpoints pagination should be used with `page` and `page_size` query parameters to dictate where in the ist to get to. 

- `page_size` should default to 30
- `page_size` should not exceed 100

Link headers should be used for metadata and related links this can be useful for clients to move between portions of the overall list. 

```
Link: </pipelines?page=2&page_size=30>; rel="self", </pipelines?page=1&page_size=30>; rel="previous", </pipelines?page=3&page_size=30>; rel="next"
```
