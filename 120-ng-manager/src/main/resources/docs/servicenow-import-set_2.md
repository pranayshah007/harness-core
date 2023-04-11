# Required ServiceNow roles

Here are the minimal permissions that the integration user must have for executing a ServiceNow import set step:

- import_transformer.
- role mapping to CRUD permissions on staging table.
- roles mapping to READ permissions for each of the target tables that have an existing transform map from the specified staging table to the target table. 
- (if required) permissions for fetching specific staging tables.
- permission to access table `sys_db_object`. This is optional because you can enter your own staging table instead of selecting prefetched values.
