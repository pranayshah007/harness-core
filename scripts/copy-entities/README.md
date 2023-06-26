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
6. It’s crucial to maintain same identifiers when creating prerequisite data.


## Supported Entities:

1. Secrets 
2. Connectors
3. Service
4. Environment
5. Template
6. Pipeline

### Script Execution Order:

The script migrates the entities in the following order: Secrets -> Connector -> Service -> Environment -> Template -> Pipeline.


### Command to Execute the Script:

python3 script.py accountIdentifier from_projectIdentifier to_ProjectIdentifier from_orgIdentifier to_orgIdentifier x-api-key
