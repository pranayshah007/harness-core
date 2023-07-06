## Prerequisites to run the Script:
1. Generate an API key with administrative rights (or enough rights to perform read and write operations in both the Orgs / projects)
2. All Secret Managers created inside the project must be migrated manually - this is needed to ensure that secrets required to create other entities can be created before migration of entities
3. If Org level secrets are being used in the project, they must be migrated manually if the new project is in different org


## Limitations of the Script:

1. The script will not migrate any inline secrets created in any Secret Manager.
2. Any Org level entities that are being referenced in the project will not be migrated (they must be migrated manually if the target project is in different org)
3. All remote Pipelines will be converted to inline.
4. All remote templates will be converted to inline.
5. If the SM is not migrated, the secrets within it cannot be migrated.
6. It’s important to maintain the same identifiers when creating prerequisite data.
7. If a Resource Group contains reference to specific entities (e.g. specified connectors) then only those references which are already copied in the destination project will be maintained (this can result in modification of the Resource Group)


## Supported Entities:

1. Secrets 
2. Connectors
3. Service
4. Environment
5. Template
6. Pipeline
7. Variables
8. Roles
9. Resource Groups
10. Users
11. UserGroups


### Script Execution Order:
Script execution order has been created keeping in mind the dependencies that need to be present in the project before dependent entities are copied.

The script migrates the entities in the following order: Secrets -> Connector -> Service -> Environment -> Template -> Pipeline -> Roles -> Resource Groups -> Users -> UserGroups.


### Command to Execute the Script:

python3 script.py accountIdentifier from_projectIdentifier to_ProjectIdentifier from_orgIdentifier to_orgIdentifier x-api-key

### Instructions to specify entities that you want to copy

Follow these steps to modify the script:
Navigate to Line No **52** in the script and modify the “Entities” array to specify only those entities (from the available Enums) that you want to copy

```python
E.g. Entities = [“Variable”]
```

### Important Note
When you are copying only specific entities, ensure that their dependencies are also included in your selection or are already available in the target project.

For example,
If you want to copy the role-assignments to user-groups, then user-groups must already exist in the project or must be among the entities being copied.

