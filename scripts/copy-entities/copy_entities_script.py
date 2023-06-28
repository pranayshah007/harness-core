# Copyright 2021 Harness Inc. All rights reserved.
# Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
# that can be found in the licenses directory at the root of this repository, also available at
# https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

import http.client
import mimetypes
import json
import yaml
import sys
from codecs import encode
from abc import ABC, abstractmethod
import ssl

accountIdentifier = sys.argv[1]
from_projectIdentifier = sys.argv[2]
to_projectIdentifier = sys.argv[3]
from_orgIdentifier = sys.argv[4]
to_orgIdentifier = sys.argv[5]
apikey = sys.argv[6]
secret_endpoint = "/gateway/ng/api/v2/secrets"
service_endpoint = "/gateway/ng/api/servicesV2"
environment_endpoint = "/gateway/ng/api/environmentsV2"
connector_endpoint = "/gateway/ng/api/connectors"
template_endpoint = "/gateway/template/api/templates"
pipeline_endpoint = "/gateway/pipeline/api/pipelines"
input_set_endpoint = "/gateway/pipeline/api/inputSets"
variable_endpoint = "gateway/ng/api/variables"
roles_endpoint = "gateway/authz/api/roles"
resourcegroup_endpoint="gateway/resourcegroup/api/v2/resourcegroup"
usergroup_list = "gateway/ng/api/aggregate/acl/usergroups"
usergroup_post = "gateway/ng/api/user-groups"
roleAssignurl = "gateway/authz/api/roleassignments/multi"

routing_and_accountId_param = "?routingId="+accountIdentifier+"&accountIdentifier="+accountIdentifier
source_query_param = routing_and_accountId_param+"&orgIdentifier="+from_orgIdentifier+"&projectIdentifier="+from_projectIdentifier
ssl._create_default_https_context = ssl._create_unverified_context
conn = http.client.HTTPSConnection("app.harness.io")
headers = {
  'x-api-key': apikey,
  'content-type': 'application/json'
  }
Entities = ["Secret","Connector","Service","Environment","Template","Pipeline","Variables","Roles","RGs","UserGroup","RoleAssignToUG"]
success_failure_count = [[0 for i in range(3)] for j in range(len(Entities))]
global success_count
global failure_count

def get_response_data(request_type, url, payload):
  conn.request(request_type, url, payload, headers)
  res = conn.getresponse()
  data = res.read()
  return json.loads(data.decode("utf-8"))

def modify_payload(payload, entity_type):
  payload[entity_type]["projectIdentifier"] = to_projectIdentifier
  payload[entity_type]["orgIdentifier"] = to_orgIdentifier
  return json.dumps(payload)

def list_entity(url, filterType):
  payload = json.dumps({"filterType":filterType})
  return get_response_data("POST", url, payload)

def create_and_print_entity(url, payload):
  response_create_dict = get_response_data("POST", url, payload)
  print(response_create_dict)
  print("\n")
  return response_create_dict

def export_input_set(pipelineIdentifier):
  url = input_set_endpoint+"?routingId="+accountIdentifier+"&accountIdentifier="+accountIdentifier+"&orgIdentifier="+from_orgIdentifier+"&projectIdentifier="+from_projectIdentifier+"&pipelineIdentifier="+pipelineIdentifier+"&pageIndex=0&pageSize=20"
  response_dict = get_response_data("GET", url, "")

  for j in range(0,response_dict["data"]["totalPages"]):
    url_paginated = input_set_endpoint+"?routingId="+accountIdentifier+"&accountIdentifier="+accountIdentifier+"&orgIdentifier="+from_orgIdentifier+"&projectIdentifier="+from_projectIdentifier+"&pipelineIdentifier="+pipelineIdentifier+"&pageIndex="+str(j)+"&pageSize=20"
    response_dict = get_response_data("GET", url_paginated, "")

    for i in range(0,response_dict["data"]["pageItemCount"]):
      url_get_input_set = input_set_endpoint +"/"+ response_dict["data"]["content"][i]["identifier"] + "?routingId="+accountIdentifier+"&accountIdentifier="+accountIdentifier+"&orgIdentifier="+from_orgIdentifier+"&pipelineIdentifier="+pipelineIdentifier+"&projectIdentifier="+from_projectIdentifier
      response_get_input_set = get_response_data("GET", url_get_input_set, "")
      new_payload_json = modify_payload(yaml.safe_load(response_get_input_set["data"]["inputSetYaml"]), "inputSet")
      url_create_input_set = input_set_endpoint + "?routingId="+accountIdentifier+"&accountIdentifier="+accountIdentifier+"&orgIdentifier="+to_orgIdentifier+"&pipelineIdentifier="+pipelineIdentifier+"&projectIdentifier=" + to_projectIdentifier
      create_and_print_entity(url_create_input_set, new_payload_json)

class ImportExport(ABC):
  @abstractmethod
  def list_entity(self):
    pass

  @abstractmethod
  def list_entity_paginated(self, j):
    pass

  @abstractmethod
  def get_entity_identifier(self, response_list_connector, i):
    pass

  @abstractmethod
  def get_entity(self, response_list_connector, i):
    pass

  @abstractmethod
  def get_payload(self, response_get_entity):
    pass

  @abstractmethod
  def create_entity(self, payload):
    pass


class Secret(ImportExport):
  def list_entity(self):
    url_secret_list = secret_endpoint + source_query_param+"&pageIndex=0&pageSize=10"
    return get_response_data("GET", url_secret_list, "")

  def list_entity_paginated(self, j):
    url_secret_list_paginated = secret_endpoint + source_query_param+"&pageIndex="+str(j)+"&pageSize=10"
    return get_response_data("GET", url_secret_list_paginated, "")

  def get_entity_identifier(self, response_list_entity, i):
    return response_list_entity["data"]["content"][i]["secret"]["identifier"]

  def get_entity(self, response_list_entity, i):
    url_get_secret = secret_endpoint + "/"+response_list_entity["data"]["content"][i]["secret"]["identifier"]+source_query_param
    return get_response_data("GET", url_get_secret, "")

  def get_payload(self, response_get_entity):
    return modify_payload(response_get_entity["data"], "secret")

  def create_entity(self, payload):
    url_create_secret = secret_endpoint + "?routingId="+accountIdentifier+"&accountIdentifier="+accountIdentifier+"&projectIdentifier="+to_projectIdentifier+"&orgIdentifier=" + to_orgIdentifier
    return create_and_print_entity(url_create_secret, payload)

class Connector(ImportExport):
  def list_entity(self):
     url_connector_list = connector_endpoint+"/listV2"+source_query_param+"&pageIndex=0&pageSize=10"
     return list_entity(url_connector_list, "Connector")

  def list_entity_paginated(self, j):
    url_connector_list_paginated = connector_endpoint+"/listV2"+source_query_param+"&pageIndex="+str(j)+"&pageSize=10"
    return list_entity(url_connector_list_paginated, "Connector")

  def get_entity_identifier(self, response_list_entity, i):
    return response_list_entity["data"]["content"][i]["connector"]["identifier"]

  def get_entity(self, response_list_connector, i):
    url_get_connector = connector_endpoint+"/"+response_list_connector["data"]["content"][i]["connector"]["identifier"]+ source_query_param
    return get_response_data("GET",url_get_connector,"")

  def get_payload(self, response_get_entity):
    return modify_payload(response_get_entity["data"],"connector")

  def create_entity(self, payload):
    if json.loads(payload)["connector"]["identifier"] == "harnessSecretManager":
      return {'status': 'SUCCESS'}
    url_create_connector = connector_endpoint+routing_and_accountId_param
    return create_and_print_entity(url_create_connector, payload)

class Environment(ImportExport):
  def list_entity(self):
    url_environment_list = environment_endpoint + source_query_param+"&page=0&size=10"
    return get_response_data("GET", url_environment_list, "")

  def list_entity_paginated(self, j):
    url_environment_list_paginated = environment_endpoint + source_query_param+"&page="+str(j)+"&size=10"
    return get_response_data("GET", url_environment_list_paginated, "")

  def get_entity_identifier(self, response_list_entity, i):
    return response_list_entity["data"]["content"][i]["environment"]["identifier"]

  def get_entity(self, response_list_entity, i):
    url_get_environment = environment_endpoint + "/" + response_list_entity["data"]["content"][i]["environment"]["identifier"]+source_query_param
    return get_response_data("GET", url_get_environment, "")

  def get_payload(self, response_get_entity):
    return json.dumps(json.loads(modify_payload(yaml.safe_load(response_get_entity["data"]["environment"]["yaml"]), "environment"))["environment"])

  def create_entity(self, payload):
    url_create_env = environment_endpoint + routing_and_accountId_param
    return create_and_print_entity(url_create_env, payload)

class Service(ImportExport):
  def list_entity(self):
    url_service_list = service_endpoint + source_query_param +"&size=10&page=0"
    return get_response_data("GET", url_service_list, "")

  def list_entity_paginated(self, j):
    url_service_list_paginated = service_endpoint + source_query_param + "&size=10&page=" + str(j)
    return get_response_data("GET", url_service_list_paginated, "")

  def get_entity_identifier(self ,response_list_entity, i):
    return response_list_entity["data"]["content"][i]["service"]["identifier"]

  def get_entity(self, response_list_entity, i):
    url_get_service = service_endpoint + "/" + response_list_entity["data"]["content"][i]["service"]["identifier"]+source_query_param
    return get_response_data("GET", url_get_service, "")

  def get_payload(self, response_get_entity):
    service_yaml = response_get_entity["data"]["service"]["yaml"]
    service_identifier = response_get_entity["data"]["service"]["identifier"]
    service_name = response_get_entity["data"]["service"]["name"]

    payload = {
      "name": service_name,
      "identifier": service_identifier,
      "tags": {},
      "projectIdentifier": to_projectIdentifier,
      "orgIdentifier": to_orgIdentifier,
      "yaml": service_yaml
    }
    return json.dumps(payload)


  def create_entity(self, payload):
    url_create_service = service_endpoint +routing_and_accountId_param
    print("create Entity Payload")
    print(payload)
    return create_and_print_entity(url_create_service, payload)



class Template(ImportExport):
  def list_entity(self):
    url_template_list = template_endpoint+"/list"+source_query_param+"&templateListType=LastUpdated&page=0&sort=lastUpdatedAt%2CDESC&size=20"
    return list_entity(url_template_list, "Template")

  def list_entity_paginated(self, j):
    url_template_list_paginated = template_endpoint+"/list?"+source_query_param+"&templateListType=LastUpdated&page="+str(j)+"&sort=lastUpdatedAt%2CDESC&size=20"
    return list_entity(url_template_list_paginated, "Template")

  def get_entity_identifier(self, response_list_entity, i):
    return response_list_entity["data"]["content"][i]["identifier"]

  def get_entity(self, response_list_template, i):
    url_template_list = template_endpoint+"/list"+source_query_param+"&templateListType=LastUpdated&page=0&sort=lastUpdatedAt%2CDESC&size=20"
    payload_template_get = json.dumps({"filterType":"Template","templateIdentifiers":[response_list_template["data"]["content"][i]["identifier"]]})
    return get_response_data("POST",url_template_list,payload_template_get)

  def get_payload(self, response_get_entity):
    return modify_payload(yaml.safe_load(response_get_entity["data"]["content"][0]["yaml"]), "template")

  def create_entity(self, payload):
    url_create_template = template_endpoint+"?accountIdentifier="+accountIdentifier+"&projectIdentifier="+to_projectIdentifier+"&orgIdentifier="+to_orgIdentifier
    return create_and_print_entity(url_create_template, payload)

class Pipeline(ImportExport):
  def list_entity(self):
    url_list_service = pipeline_endpoint+"/list"+source_query_param+"&searchTerm=&page=0&sort=lastUpdatedAt%2CDESC&size=20"
    return list_entity(url_list_service, "PipelineSetup")

  def list_entity_paginated(self, j):
    url_list_service_paginated = pipeline_endpoint+"/list"+source_query_param+"&searchTerm=&page="+str(j)+"&sort=lastUpdatedAt%2CDESC&size=20"
    return list_entity(url_list_service_paginated, "PipelineSetup")

  def get_entity_identifier(self, response_list_entity, i):
    return response_list_entity["data"]["content"][i]["identifier"]

  def get_entity(self, response_list_entity, i):
    url_get_pipeline = pipeline_endpoint+"/" + response_list_entity["data"]["content"][i]["identifier"] + "?accountIdentifier="+accountIdentifier+"&orgIdentifier="+from_orgIdentifier+"&projectIdentifier="+from_projectIdentifier+""
    return get_response_data("GET",url_get_pipeline,"")

  def get_payload(self, response_get_entity):
    return modify_payload(yaml.safe_load(response_get_entity["data"]["yamlPipeline"]), "pipeline")

  def create_entity(self, payload):
    url_create_pipeline = pipeline_endpoint+"?routingId="+accountIdentifier+"&accountIdentifier="+accountIdentifier+"&orgIdentifier="+to_orgIdentifier+"&projectIdentifier="+to_projectIdentifier+"&pipelineIdentifier="+json.loads(payload)["pipeline"]["identifier"]+"&pipelineName="+json.loads(payload)["pipeline"]["name"]

    url_create_pipeline = url_create_pipeline.replace(" ", "%20")
    response_create_pipeline = create_and_print_entity(url_create_pipeline, payload)
    export_input_set(json.loads(payload)["pipeline"]["identifier"])
    return response_create_pipeline

class Variables(ImportExport):
  def list_entity(self):
    url_variable_list = variable_endpoint + source_query_param+"&pageIndex=0&pageSize=10"
    return get_response_data("GET", url_variable_list, "")

  def list_entity_paginated(self, j):
    url_variable_list_paginated = variable_endpoint + source_query_param+"&pageIndex="+str(j)+"&pageSize=10"
    return get_response_data("GET",url_variable_list_paginated, "")

  def get_entity_identifier(self, response_list_entity, i):
    return response_list_entity["data"]["content"][i]["variable"]["identifier"]

  def get_entity(self, response_list_entity, i):
    url_get_variable = variable_endpoint + "/"+response_list_entity["data"]["content"][i]["variable"]["identifier"]+source_query_param
    return get_response_data("GET", url_get_variable, "")

  def get_payload(self, response_get_entity):
    return modify_payload(response_get_entity["data"], "variable")

  def create_entity(self, payload):
    url_create_variable = variable_endpoint + "?routingId="+accountIdentifier+"&accountIdentifier="+accountIdentifier+"&projectIdentifier="+to_projectIdentifier+"&orgIdentifier=" + to_orgIdentifier
    return create_and_print_entity(url_create_variable, payload)

class Roles(ImportExport):
  def list_entity(self):
    url_roles_list = roles_endpoint + source_query_param+"&pageIndex=0&pageSize=10"
    return get_response_data("GET", url_roles_list, "")

  def list_entity_paginated(self, j):
    url_roles_list_paginated = roles_endpoint + source_query_param+"&pageIndex="+str(j)+"&pageSize=10"
    return get_response_data("GET",url_roles_list_paginated, "")

  def get_entity_identifier(self, response_list_entity, i):
    return response_list_entity["data"]["content"][i]["role"]["identifier"]

  def get_entity(self, response_list_entity, i):
    url_get_role = roles_endpoint + "/"+response_list_entity["data"]["content"][i]["role"]["identifier"]+source_query_param
    return get_response_data("GET", url_get_role, "")

  def get_payload(self, response_get_entity):

    identifier = response_get_entity["data"]["role"]["identifier"]
    name = response_get_entity["data"]["role"]["name"]
    roles_permission = response_get_entity["data"]["role"]["permissions"]
    allowed_scope = response_get_entity["data"]["role"]["allowedScopeLevels"]
    description = response_get_entity["data"]["role"]["description"]
    tag = response_get_entity["data"]["role"]["tags"]

    payload = {
      "name": name,
      "identifier": identifier,
      "tags": tag,
      "permissions": roles_permission,
      "allowedScopeLevels" : allowed_scope
    }
    return json.dumps(payload)

  def create_entity(self, payload):
    if isinstance(payload, str):
        payload = json.loads(payload)

    identifier = payload.get("identifier")

    if identifier and identifier.startswith("_"):
        print("Skipping create_entity for the following role ID " + identifier)

        return {'status': 'SUCCESS'}
    url_create_role = roles_endpoint + "?routingId="+accountIdentifier+"&accountIdentifier="+accountIdentifier+"&projectIdentifier="+to_projectIdentifier+"&orgIdentifier=" + to_orgIdentifier
    payload = json.dumps(payload)
    return create_and_print_entity(url_create_role, payload)

class RGs(ImportExport):
  def list_entity(self):
    url_rg_list = resourcegroup_endpoint + source_query_param+"&pageIndex=0&pageSize=10"
    return get_response_data("GET", url_rg_list, "")

  def list_entity_paginated(self, j):
    url_rg_list_paginated = resourcegroup_endpoint + source_query_param+"&pageIndex="+str(j)+"&pageSize=10"
    return get_response_data("GET",url_rg_list_paginated, "")

  def get_entity_identifier(self, response_list_entity, i):
    return response_list_entity["data"]["content"][i]["resourceGroup"]["identifier"]

  def get_entity(self, response_list_entity, i):
    url_get_rg = resourcegroup_endpoint + "/"+response_list_entity["data"]["content"][i]["resourceGroup"]["identifier"]+source_query_param
    return get_response_data("GET", url_get_rg, "")

  def get_payload(self, response_get_entity):

    identifier = response_get_entity["data"]["resourceGroup"]["identifier"]
    name = response_get_entity["data"]["resourceGroup"]["name"]

    allowed_scope = response_get_entity["data"]["resourceGroup"]["allowedScopeLevels"]
    resourceFilter = response_get_entity["data"]["resourceGroup"]["resourceFilter"]
    included_scopes = response_get_entity["data"]["resourceGroup"]["includedScopes"]
    tag = response_get_entity["data"]["resourceGroup"]["tags"]

    new_included_scopes = []
    for scope in included_scopes:
        new_scope = {
            "filter": scope["filter"],
            "accountIdentifier" : sys.argv[1],
            "projectIdentifier": sys.argv[3],
            "orgIdentifier": sys.argv[5],
        }
        new_included_scopes.append(new_scope)


    payload = {
      "resourceGroup":{
       "name": name,
      "identifier": identifier,
      "accountIdentifier" : sys.argv[1],
      "projectIdentifier": sys.argv[3],
      "orgIdentifier": sys.argv[5],
      "tags": tag,
      "includedScopes" : new_included_scopes,
      "resourceFilter": resourceFilter,
      "allowedScopeLevels" : allowed_scope
      }

    }
    return json.dumps(payload)

  def create_entity(self, payload):
    if isinstance(payload, str):
        payload = json.loads(payload)

    identifier = payload.get("resourceGroup", {}).get("identifier")

    if identifier and identifier.startswith("_"):
        print("Skipping create_entity for the following role ID " + identifier)
        return {'status': 'SUCCESS'}

    url_create_rg = resourcegroup_endpoint + "?routingId="+accountIdentifier+"&accountIdentifier="+accountIdentifier+"&projectIdentifier="+to_projectIdentifier+"&orgIdentifier=" + to_orgIdentifier
    payload = json.dumps(payload)
    return create_and_print_entity(url_create_rg, payload)

class UserGroup(ImportExport):
  def list_entity(self):
    url_usergroup_list = usergroup_list + source_query_param+"&pageIndex=0&pageSize=10"
    return get_response_data("GET", url_usergroup_list, "")

  def list_entity_paginated(self, j):
    url_usergroup_list_paginated = usergroup_list + source_query_param+"&pageIndex="+str(j)+"&pageSize=10"
    return get_response_data("GET",url_usergroup_list_paginated, "")

  def get_entity_identifier(self, response_list_entity, i):
    return response_list_entity["data"]["content"][i]["userGroupDTO"]["identifier"]

  def get_entity(self, response_list_entity, i):
    url_get_usergroup = usergroup_list + "/"+response_list_entity["data"]["content"][i]["userGroupDTO"]["identifier"]+source_query_param
    return get_response_data("GET", url_get_usergroup, "")

  def get_payload(self, response_get_entity):

    identifier = response_get_entity["data"]["userGroupDTO"]["identifier"]
    name = response_get_entity["data"]["userGroupDTO"]["name"]
    roleassignment = response_get_entity["data"]["roleAssignmentsMetadataDTO"]
    notificationConfigs = response_get_entity["data"]["userGroupDTO"]["notificationConfigs"]
    user_list = response_get_entity["data"]["userGroupDTO"]["users"]
    description = response_get_entity["data"]["userGroupDTO"]["description"]
    tagList = response_get_entity["data"]["userGroupDTO"]["tags"]

    payload = {
      "name": name,
      "identifier": identifier,
      "users": user_list,
      "description": description,
      "notificationConfigs": notificationConfigs,
      "tags":tagList,
      "projectIdentifier": to_projectIdentifier,
      "orgIdentifier": to_orgIdentifier
    }
    return json.dumps(payload)

  def create_entity(self, payload):
    if isinstance(payload, str):
        payload = json.loads(payload)

    identifier = payload.get("identifier")

    if identifier and identifier.startswith("_"):
        print("Skipping create_entity for the following  ID " + identifier)

        return {'status': 'SUCCESS'}
    url_create_usergroup = usergroup_post + "?routingId="+accountIdentifier+"&accountIdentifier="+accountIdentifier+"&projectIdentifier="+to_projectIdentifier+"&orgIdentifier=" + to_orgIdentifier
    payload = json.dumps(payload)
    response = create_and_print_entity(url_create_usergroup, payload)
    return response


class RoleAssignToUG(ImportExport):
  def list_entity(self):
    url_roleassigntoUg_list = usergroup_list + source_query_param+"&pageIndex=0&pageSize=10"
    return get_response_data("GET", url_roleassigntoUg_list, "")

  def list_entity_paginated(self, j):
    url_roleassigntoUg_list_paginated = usergroup_list + source_query_param+"&pageIndex="+str(j)+"&pageSize=10"
    return get_response_data("GET",url_roleassigntoUg_list_paginated, "")

  def get_entity_identifier(self, response_list_entity, i):
    return response_list_entity["data"]["content"][i]["userGroupDTO"]["identifier"]

  def get_entity(self, response_list_entity, i):
    url_get_roleassigntoUg = usergroup_list + "/"+response_list_entity["data"]["content"][i]["userGroupDTO"]["identifier"]+source_query_param
    return get_response_data("GET", url_get_roleassigntoUg, "")

  def get_payload(self, response_get_entity):

    identifier = response_get_entity["data"]["userGroupDTO"]["identifier"]
    name = response_get_entity["data"]["userGroupDTO"]["name"]
    roleassignment = response_get_entity["data"]["roleAssignmentsMetadataDTO"]
    new_included_roleIdentifier = []
    for role in roleassignment:
        new_scope = {
            "roleIdentifier": role["roleIdentifier"],
            "resourceGroupIdentifier" : role["resourceGroupIdentifier"] ,
            "principal" : {
                "identifier" : identifier,
                "type" : "USER_GROUP"
            }
        }
        new_included_roleIdentifier.append(new_scope)

    payload = {
      "roleAssignments": new_included_roleIdentifier,
    }
    return json.dumps(payload)


  def create_entity(self, payload):
    if isinstance(payload, str):
        payload = json.loads(payload)

    identifier = payload.get("identifier")

    if identifier and identifier.startswith("_"):
        print("Skipping create_entity for the following  ID " + identifier)

        return {'status': 'SUCCESS'}

    url_create_roleAssigntoUG = roleAssignurl + "?routingId="+accountIdentifier+"&accountIdentifier="+accountIdentifier+"&projectIdentifier="+to_projectIdentifier+"&orgIdentifier=" + to_orgIdentifier
    payload = json.dumps(payload)
    return create_and_print_entity(url_create_roleAssigntoUG, payload)



def main_export(entityType):
  classname = globals()[entityType]
  x = classname()
  list_error_response = list()
  success_count = 0
  failure_count = 0
  response_list_entity = x.list_entity()

  for j in range(0, response_list_entity["data"]["totalPages"]):
    response_list_entity_paginated = x.list_entity_paginated(j)
    pageItemCount = ""
    if entityType =='Template' or entityType=='Pipeline':
      pageItemCount = "numberOfElements"
    else:
      pageItemCount = "pageItemCount"

    for i in range(0, response_list_entity_paginated["data"][pageItemCount]):
      response_get_entity = x.get_entity(response_list_entity_paginated, i)
      new_payload = x.get_payload(response_get_entity)
      response_create_entity = x.create_entity(new_payload)
      if response_create_entity["status"] == "SUCCESS":
        success_count+=1
      else:
        failure_count+=1
        identifier = x.get_entity_identifier(response_list_entity_paginated,i)
        list_error_response.append(entityType + " Identifier: " + identifier+", Error Message:" + response_create_entity["message"])
  return [success_count,failure_count,list_error_response]

for n in range(0,len(Entities)):
  success_failure_count[n] = main_export(Entities[n])

for n in range(0,len(Entities)):
  print("Successfully copied "+str(success_failure_count[n][0])+" "+ Entities[n]+" and got error while copying "+str(success_failure_count[n][1])+" "+Entities[n])
  print('\n')
  for i in range(0,success_failure_count[n][1]):
    print(success_failure_count[n][2][i])
    print('\n')
