# HTTP Methods

Ideally, our HTTP calls should follow the following guidelines:

- GET 
  * To be used for retrieving data for a resource as well as for Listing / Filtering resources.
  * Endpoints should not contain a request body. 
  
- POST / PUT
  * To be used for Creating and Updating details for a resource respectively. 
  * Endpoints should preferably not contain any query parameters.

- Exceptions
  * Filtering
    + Should be done using a GET endpoint with simple field based filters, but we can have complex operator/fiql based filtering as well in query parameters.
    + Exceptionally we can have a POST call if it can be sufficiently justified, but this should be added as a separate Filter endpoint. 