# Pagination

For list endpoints pagination should be used with `page` and `limit` query parameters to dictate where in the list to get to. 

- `limit` should default to 30
- `limit` should not exceed 100

There are 3 Pagination headers supported in the Response bodies of Paginated APIs.
1. X-Total-Elements : Indicates the total number of entries in the Paginated response.
2. X-Page-Number : Indicates the page number currently returned for a Paginated response.
3. X-Page-Size : Indicates the number of entries per page for a Paginated response. 

For example:
```
X-Total-Elements : 30
X-Page-Number : 0
X-Page-Size : 10
```
