# Pagination

Two different pagination strategies may be used depending on the needs of the specific endpoint: offset & keyset. Offset offers more flexibility but has some scalability issues, while keyset is more performant over larger collections.

In most cases offset is likely the desirable choice. 

## Offset

This allows a user to jump to any point in the collection with ease. In theory it also allows pagination to be applied on top of any existing list requests, though in practice care should still be given to what can be filtered and sorted upon. 

### Request

The following query parameters used for paging information:

- ` limit`: how many results to return in the page.
- `page`: the page number to return. 

### Response

Pagination information is returned via the `Link` header, 5 relative links are provided allowing the client to understand where in the collection the page exists and how to navigate: `self`, `next`, `prev`, `first`, `last`.

Returned `Link` header example: 

```
Link: </v1/orgs?page=2&limit=30>; rel="self", </v1/roles?page=1&limit=30>; rel="prev", </v1/roles?page=3&limit=30>; rel="next", </v1/roles?page=1&limit=30>; rel="first", </v1/roles?page=21&limit=30>; rel="last"
```

## Keyset

Many databases incur a penalty of scanning all entries up to the point of the offset for offset based pagination, keyset pagination offers an alternative whereby a cursor reference is passed with a page limit allowing a more linear performance over larger tables.

Note: the cursor itself may simply be a ordered ID stored in the db or may be a composite key eg a base32 of timestamp and unique ID. 

### Request

The following query parameters used for paging information:

- ` limit`: how many results to return in the page.
- `cursor`: the last position in the collection. 

### Response

The same 5 relative positions in the collection are provided. 

Example `Link` header response :

```
Link: </v1/orgs?cursor=30&limit=30>; rel="self", </v1/roles?cursor=0&limit=30>; rel="prev", </v1/roles?cursor=60&limit=30>; rel="next", </v1/roles?cursor=0&limit=30>; rel="first", </v1/roles?cursor=600&limit=30>; rel="last",
```
