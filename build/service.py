import sys

def modify_service_name(service_name):
    modified_service_name = {
        "ng-manager": "120-ng-manager",
        "migrator": "100-migrator",
        "manager": "360-cg-manager",
        "change-data-capture": "110-change-data-capture",
        "iacm-manager": "310-iacm-manager",
        "sto-manager": "315-sto-manager",
        "ci-manager": "332-ci-manager"
    }
    modified_service_name1 = {"310-iacm-manager", "315-sto-manager", "332-ci-manager"}
    modified_service_name2 = {"debezium-service", "pipeline-service", "platform-service", "template-service", "access-control", "batch-processing"}

    modified_service_name = modified_service_name.get(service_name, service_name)

    if modified_service_name in modified_service_name1:
        modified_service_name += "/app"
    elif modified_service_name in modified_service_name2:
        modified_service_name += "/service"

    return modified_service_name


# Retrieve the service name from command-line argument
service_name = sys.argv[1]

# Call the function with the service name argument
modified_service_name = modify_service_name(service_name)
