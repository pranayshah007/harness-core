# Copyright 2022 Harness Inc. All rights reserved.
# Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
# that can be found in the licenses directory at the root of this repository, also available at
# https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

from pymongo import MongoClient
from enum import Enum
import requests
import yaml

INPUT_ACCOUNT_ID = "kmpySmUISimoRrJL6NL73w"
INPUT_ORG_ID = "default"
INPUT_PROJECT_ID = "sept_13_project_3"
LOCAL_API_KEY = "pat.kmpySmUISimoRrJL6NL73w.62a7a3ee66425e616acf6629.ccm4qJhiI42DcOwRtGE3"

HARNESS_SUPPORT_USER_ACCOUNT_ID = "kmpySmUISimoRrJL6NL73w"

PMS_DB_NAME = "pms-harness"
NG_MANAGER_DB_NAME = "ng-harness"

STORE_TYPE_REMOTE = "REMOTE"
PIPELINE = "PIPELINE"
INPUTSET = "INPUTSET"

class DBKeys(Enum):
    ACCOUNT_ID = "accountId"
    ACCOUNT_IDENTIFIER = "accountIdentifier"
    ORG_IDENTIFIER = "orgIdentifier"
    PROJECT_IDENTIFIER = "projectIdentifier"
    IDENTIFIER = "identifier"
    REPO = "repo"
    GIT_CONNECTOR_REF = "gitConnectorRef"
    IS_FROM_DEFAULT_BRANCH = "isFromDefaultBranch"
    FILE_PATH = "filePath"
    ROOT_FOLDER = "rootFolder"
    STORE_TYPE = "storeType"
    CONNECTOR_REF = "connectorRef"
    YAML_GIT_CONFIG_REF = "yamlGitConfigRef"
    BRANCH = "branch"
    OBJECT_ID_OF_YAML = "objectIdOfYaml"
    YAML = "yaml"
    TARGET_IDENTIFIER = "targetIdentifier"
    PIPELINE_BRANCH_NAME = "pipelineBranchName"
    TRIGGER = "trigger"
    SETTINGS = "settings"


yaml_git_config_list = []
migrated_pipelines = {}

class YamlGitConfig:
    def __init__(self, identifier, repo_url, connector_ref):
        self.identifier = identifier
        self.repo_url = repo_url
        self.connector_ref = connector_ref

    def __repr__(self):
        return "%s | %s | %s" % (self.identifier, self.repo_url, self.connector_ref)

#############################################################################

def get_repo_from_repo_url(repo_url):
    delimiter_index = repo_url.rfind("/")
    return repo_url[delimiter_index+1:]

def prepare_key(identifier, entity):
    return identifier + "_" + entity

def setup_mongo_client():
    global mongo_client
    global pms_db
    global ng_manager_db
    mongo_client = MongoClient(host="localhost", port=27017)
    pms_db = mongo_client.get_database(PMS_DB_NAME)
    ng_manager_db = mongo_client.get_database(NG_MANAGER_DB_NAME)


def prepare_yaml_git_config_list():
    ng_manager_db = mongo_client.get_database(NG_MANAGER_DB_NAME)
    yaml_git_config_collection = ng_manager_db.yamlGitConfigs

    records = yaml_git_config_collection.find({
        DBKeys.ACCOUNT_ID.value: INPUT_ACCOUNT_ID,
        DBKeys.ORG_IDENTIFIER.value: INPUT_ORG_ID,
        DBKeys.PROJECT_IDENTIFIER.value: INPUT_PROJECT_ID
    })

    for record in records:
        identifier = record.get(DBKeys.IDENTIFIER.value)
        yaml_git_config_list.append(YamlGitConfig(identifier, record.get(DBKeys.REPO.value), record.get(DBKeys.GIT_CONNECTOR_REF.value)))

    print(yaml_git_config_list)


def cache_pipeline_locally(record, entity):
    migrated_pipelines[prepare_key(record.get(DBKeys.IDENTIFIER.value), entity)] = record


def migrate_records(collection, yaml_git_config, entity):
    query = {
        DBKeys.ACCOUNT_ID.value: INPUT_ACCOUNT_ID,
        DBKeys.ORG_IDENTIFIER.value: INPUT_ORG_ID,
        DBKeys.PROJECT_IDENTIFIER.value: INPUT_PROJECT_ID,
        DBKeys.YAML_GIT_CONFIG_REF.value: yaml_git_config.identifier,
    }
    records = collection.find(query)

    for record in records:
        old_file_path = record.get(DBKeys.FILE_PATH.value)
        old_root_folder = record.get(DBKeys.ROOT_FOLDER.value)
        # remove starting slash from root folder

        record[DBKeys.REPO.value] = get_repo_from_repo_url(yaml_git_config.repo_url)
        record[DBKeys.STORE_TYPE.value] = STORE_TYPE_REMOTE
        record[DBKeys.CONNECTOR_REF.value] = yaml_git_config.connector_ref
        record[DBKeys.FILE_PATH.value] = old_root_folder[1:] + old_file_path

        collection.update_one({"_id": record.get("_id")}, {"$set": record}, upsert=False)
        cache_pipeline_locally(record, entity)

    collection.update_many(query, {"$unset": {
                                    DBKeys.ROOT_FOLDER.value: 1,
                                    DBKeys.YAML_GIT_CONFIG_REF.value: 1,
                                    DBKeys.BRANCH.value: 1,
                                    DBKeys.IS_FROM_DEFAULT_BRANCH.value: 1,
                                    DBKeys.OBJECT_ID_OF_YAML.value: 1,
                                    DBKeys.YAML.value: 1
                                    }
                              })


def migrate_records_from_inline_to_remote():
    for yaml_git_config in yaml_git_config_list:
        print(yaml_git_config)
        migrate_records(pms_db.pipelinesPMS, yaml_git_config, PIPELINE)
        migrate_records(pms_db.inputSetsPMS, yaml_git_config, INPUTSET)


def delete_non_default_pipelines_input_sets():
    pipeline_collection = pms_db.pipelinesPMS
    input_sets_collection = pms_db.inputSetsPMS
    query_condition = {
        DBKeys.ACCOUNT_ID.value: INPUT_ACCOUNT_ID,
        DBKeys.ORG_IDENTIFIER.value: INPUT_ORG_ID,
        DBKeys.PROJECT_IDENTIFIER.value: INPUT_PROJECT_ID,
        DBKeys.IS_FROM_DEFAULT_BRANCH.value: False
    }
    pipeline_collection.delete_many(query_condition)
    input_sets_collection.delete_many(query_condition)


def delete_non_default_connectors():
    connectors_collection = ng_manager_db.connectors
    query_condition = {
        DBKeys.ACCOUNT_IDENTIFIER.value: INPUT_ACCOUNT_ID,
        DBKeys.ORG_IDENTIFIER.value: INPUT_ORG_ID,
        DBKeys.PROJECT_IDENTIFIER.value: INPUT_PROJECT_ID,
        DBKeys.IS_FROM_DEFAULT_BRANCH.value: False
    }
    connectors_collection.delete_many(query_condition)


def delete_non_default_branch_entities():
    delete_non_default_pipelines_input_sets()
    delete_non_default_connectors()


def cleanup_connector_entities():
    collection = ng_manager_db.connectors
    for yaml_git_config in yaml_git_config_list:
        query = {
            DBKeys.ACCOUNT_IDENTIFIER.value: INPUT_ACCOUNT_ID,
            DBKeys.ORG_IDENTIFIER.value: INPUT_ORG_ID,
            DBKeys.PROJECT_IDENTIFIER.value: INPUT_PROJECT_ID,
            DBKeys.YAML_GIT_CONFIG_REF.value: yaml_git_config.identifier,
        }

        collection.update_many(query, {"$unset": {
                    DBKeys.ROOT_FOLDER.value: 1,
                    DBKeys.YAML_GIT_CONFIG_REF.value: 1,
                    DBKeys.BRANCH.value: 1,
                    DBKeys.IS_FROM_DEFAULT_BRANCH.value: 1,
                    DBKeys.OBJECT_ID_OF_YAML.value: 1,
                    DBKeys.YAML.value: 1,
                    DBKeys.FILE_PATH.value: 1
                }
                })


def reset_git_sync_sdk_cache():
    url = "https://localhost:8181/ng/api/git-sync/reset-cache?accountIdentifier=%s&targetAccountIdentifier=%s&targetOrgIdentifier=%s&targetProjectIdentifier=%s" % (
        HARNESS_SUPPORT_USER_ACCOUNT_ID, INPUT_ACCOUNT_ID, INPUT_ORG_ID, INPUT_PROJECT_ID)
    headers = {'x-api-key': LOCAL_API_KEY}
    resp = requests.post(url, headers=headers, verify=False)
    print(resp.status_code)


def enable_new_gitx():
    ng_manager_db = mongo_client.get_database(NG_MANAGER_DB_NAME)
    git_sync_settings_collection = ng_manager_db.gitSyncSettings

    find_criteria = {
        DBKeys.ACCOUNT_IDENTIFIER.value: INPUT_ACCOUNT_ID,
        DBKeys.ORG_IDENTIFIER.value: INPUT_ORG_ID,
        DBKeys.PROJECT_IDENTIFIER.value: INPUT_PROJECT_ID,
    }

    settings_value = {
        "isGitSimplificationEnabled": "true"
    }

    git_sync_settings_collection.update_one(find_criteria, {"$set": {DBKeys.SETTINGS.value: settings_value}})


def delete_yaml_git_configs():
    ng_manager_db = mongo_client.get_database(NG_MANAGER_DB_NAME)
    yaml_git_config_collection = ng_manager_db.yamlGitConfigs

    find_criteria = {
        DBKeys.ACCOUNT_ID.value: INPUT_ACCOUNT_ID,
        DBKeys.ORG_IDENTIFIER.value: INPUT_ORG_ID,
        DBKeys.PROJECT_IDENTIFIER.value: INPUT_PROJECT_ID
    }
    yaml_git_config_collection.delete_many(find_criteria)


def update_triggers():
    query = {
        DBKeys.ACCOUNT_ID.value: INPUT_ACCOUNT_ID,
        DBKeys.ORG_IDENTIFIER.value: INPUT_ORG_ID,
        DBKeys.PROJECT_IDENTIFIER.value: INPUT_PROJECT_ID,
    }
    triggers = pms_db.triggersNG.find(query)

    for trigger in triggers:
        pipelineId = trigger.get(DBKeys.TARGET_IDENTIFIER.value)
        pipeline = migrated_pipelines.get(prepare_key(pipelineId, PIPELINE))
        if pipeline is None:
            continue

        pipelineBranchName = pipeline[DBKeys.BRANCH.value]
        trigger_yaml = yaml.safe_load(trigger.get(DBKeys.YAML.value))
        trigger_yaml[DBKeys.TRIGGER.value][DBKeys.PIPELINE_BRANCH_NAME.value] = pipelineBranchName
        trigger[DBKeys.YAML.value] = yaml.dump(trigger_yaml,  sort_keys=False)
        pms_db.triggersNG.update_one({"_id": trigger.get("_id")}, {"$set": trigger}, upsert=False)


if __name__ == "__main__":
    setup_mongo_client()
    prepare_yaml_git_config_list()
    delete_yaml_git_configs()
    delete_non_default_branch_entities()
    enable_new_gitx()
    migrate_records_from_inline_to_remote()
    update_triggers()
    cleanup_connector_entities()
    # reset_git_sync_sdk_cache()
