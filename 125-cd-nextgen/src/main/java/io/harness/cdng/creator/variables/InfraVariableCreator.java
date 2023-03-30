/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.creator.variables;

import io.harness.cdng.visitor.YamlTypes;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.infrastructure.InfrastructureKind;
import io.harness.pms.contracts.plan.YamlProperties;
import io.harness.pms.sdk.core.variables.VariableCreatorHelper;
import io.harness.pms.sdk.core.variables.beans.VariableCreationResponse;
import io.harness.pms.yaml.DependenciesUtils;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlNode;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.experimental.UtilityClass;

@UtilityClass
public class InfraVariableCreator {
  public VariableCreationResponse createVariableResponse(YamlField infraField) {
    if (infraField == null) {
      return VariableCreationResponse.builder().build();
    }

    Map<String, YamlProperties> yamlPropertiesMap = new LinkedHashMap<>();
    Map<String, YamlField> dependenciesMap = new LinkedHashMap<>();
    String infraUUID = infraField.getNode().getUuid();
    yamlPropertiesMap.put(infraUUID, YamlProperties.newBuilder().setFqn(YamlTypes.PIPELINE_INFRASTRUCTURE).build());

    YamlField infraDefNode = infraField.getNode().getField(YamlTypes.INFRASTRUCTURE_DEF);
    if (VariableCreatorHelper.isNotYamlFieldEmpty(infraDefNode)
        && VariableCreatorHelper.isNotYamlFieldEmpty(infraDefNode.getNode().getField(YamlTypes.SPEC))) {
      addVariablesForInfraDef(infraDefNode, yamlPropertiesMap);

      YamlField provisionerField = infraDefNode.getNode().getField(YAMLFieldNameConstants.PROVISIONER);
      if (provisionerField != null) {
        dependenciesMap.putAll(addDependencyForProvisionerSteps(provisionerField));
      }
    }
    YamlField envField = infraField.getNode().getField(YamlTypes.ENVIRONMENT_YAML);
    if (VariableCreatorHelper.isNotYamlFieldEmpty(envField)) {
      addVariablesForEnv(envField, yamlPropertiesMap);
    }
    YamlField envRefField = infraField.getNode().getField(YamlTypes.ENVIRONMENT_REF);
    if (envRefField != null) {
      VariableCreatorHelper.addFieldToPropertiesMap(envRefField, yamlPropertiesMap, YamlTypes.PIPELINE_INFRASTRUCTURE);
    }
    return VariableCreationResponse.builder()
        .yamlProperties(yamlPropertiesMap)
        .dependencies(DependenciesUtils.toDependenciesProto(dependenciesMap))
        .build();
  }

  public static Map<String, YamlField> addDependencyForProvisionerSteps(YamlField provisionerField) {
    Map<String, YamlField> stepsDependencyMap = new HashMap<>();
    List<YamlField> stepYamlFields = VariableCreatorHelper.getStepYamlFields(provisionerField);
    for (YamlField stepYamlField : stepYamlFields) {
      stepsDependencyMap.put(stepYamlField.getNode().getUuid(), stepYamlField);
    }

    YamlField rollbackStepsField = provisionerField.getNode().getField(YAMLFieldNameConstants.ROLLBACK_STEPS);
    if (rollbackStepsField != null) {
      List<YamlNode> yamlNodes = rollbackStepsField.getNode().asArray();
      List<YamlField> rollbackStepYamlFields = VariableCreatorHelper.getStepYamlFields(yamlNodes);
      for (YamlField stepYamlField : rollbackStepYamlFields) {
        stepsDependencyMap.put(stepYamlField.getNode().getUuid(), stepYamlField);
      }
    }
    return stepsDependencyMap;
  }

  private void addVariablesForEnv(YamlField envNode, Map<String, YamlProperties> yamlPropertiesMap) {
    YamlField nameField = envNode.getNode().getField(YAMLFieldNameConstants.NAME);
    if (nameField != null) {
      VariableCreatorHelper.addFieldToPropertiesMap(nameField, yamlPropertiesMap, YamlTypes.PIPELINE_INFRASTRUCTURE);
    }
    YamlField descriptionNode = envNode.getNode().getField(YAMLFieldNameConstants.DESCRIPTION);
    if (descriptionNode != null) {
      VariableCreatorHelper.addFieldToPropertiesMap(
          descriptionNode, yamlPropertiesMap, YamlTypes.PIPELINE_INFRASTRUCTURE);
    }
    YamlField tagsField = envNode.getNode().getField(YAMLFieldNameConstants.TAGS);
    if (VariableCreatorHelper.isNotYamlFieldEmpty(tagsField)) {
      List<YamlField> fields = tagsField.getNode().fields();
      fields.forEach(field -> {
        if (!field.getName().equals(YamlTypes.UUID)) {
          VariableCreatorHelper.addFieldToPropertiesMap(field, yamlPropertiesMap, YamlTypes.PIPELINE_INFRASTRUCTURE);
        }
      });
    }
  }

  private void addVariablesForInfraDef(YamlField infraDefNode, Map<String, YamlProperties> yamlPropertiesMap) {
    String type = infraDefNode.getNode().getType();
    if (type != null) {
      switch (type) {
        case InfrastructureKind.KUBERNETES_DIRECT:
          addVariablesForKubernetesInfra(infraDefNode, yamlPropertiesMap);
          break;

        case InfrastructureKind.KUBERNETES_GCP:
          addVariablesForKubernetesGcpInfra(infraDefNode, yamlPropertiesMap);
          break;

        case InfrastructureKind.KUBERNETES_AZURE:
          addVariablesForKubernetesAzureInfra(infraDefNode, yamlPropertiesMap);
          break;

        case InfrastructureKind.PDC:
          addVariablesForPhysicalDataCenterInfra(infraDefNode, yamlPropertiesMap);
          break;

        case InfrastructureKind.SERVERLESS_AWS_LAMBDA:
          addVariablesForServerlessAwsInfra(infraDefNode, yamlPropertiesMap);
          break;

        case InfrastructureKind.SSH_WINRM_AWS:
          addVariablesForSshWinRmAwsInfra(infraDefNode, yamlPropertiesMap);
          break;

        case InfrastructureKind.AZURE_WEB_APP:
          addVariablesForAzureWebAppInfra(infraDefNode, yamlPropertiesMap);
          break;

        case InfrastructureKind.ECS:
          addVariablesForEcsAwsInfra(infraDefNode, yamlPropertiesMap);
          break;

        case InfrastructureKind.ELASTIGROUP:
          addVariablesForElastigroupInfra(infraDefNode, yamlPropertiesMap);
          break;

        case InfrastructureKind.TAS:
          addVariablesForTASInfra(infraDefNode, yamlPropertiesMap);
          break;

        case InfrastructureKind.ASG:
          addVariablesForAsgInfra(infraDefNode, yamlPropertiesMap);
          break;

        case InfrastructureKind.GOOGLE_CLOUD_FUNCTIONS:
          addVariablesForGoogleCloudFunctionsInfra(infraDefNode, yamlPropertiesMap);
          break;

        case InfrastructureKind.AWS_LAMBDA:
          addVariablesForAwsLambdaInfra(infraDefNode, yamlPropertiesMap);
          break;

        case InfrastructureKind.KUBERNETES_AWS:
          addVariablesForKubernetesAwsInfra(infraDefNode, yamlPropertiesMap);
          break;

        default:
          throw new InvalidRequestException("Invalid infra definition type");
      }
    }
  }

  private void addVariablesForTASInfra(YamlField infraDefNode, Map<String, YamlProperties> yamlPropertiesMap) {
    YamlField infraSpecNode = infraDefNode.getNode().getField(YamlTypes.SPEC);
    if (infraSpecNode == null) {
      return;
    }

    addVariableForYamlType(YamlTypes.CONNECTOR_REF, infraSpecNode, yamlPropertiesMap);
    addVariableForYamlType(YamlTypes.ORG, infraSpecNode, yamlPropertiesMap);
    addVariableForYamlType(YamlTypes.SPACE, infraSpecNode, yamlPropertiesMap);
  }

  private void addVariablesForKubernetesInfra(YamlField infraDefNode, Map<String, YamlProperties> yamlPropertiesMap) {
    YamlField infraSpecNode = infraDefNode.getNode().getField(YamlTypes.SPEC);
    if (infraSpecNode == null) {
      return;
    }

    addVariableForYamlType(YamlTypes.CONNECTOR_REF, infraSpecNode, yamlPropertiesMap);
    addVariableForYamlType(YamlTypes.NAMESPACE, infraSpecNode, yamlPropertiesMap);
    addVariableForYamlType(YamlTypes.RELEASE_NAME, infraSpecNode, yamlPropertiesMap);
  }

  private void addVariablesForKubernetesGcpInfra(
      YamlField infraDefNode, Map<String, YamlProperties> yamlPropertiesMap) {
    YamlField infraSpecNode = infraDefNode.getNode().getField(YamlTypes.SPEC);
    if (infraSpecNode == null) {
      return;
    }

    addVariableForYamlType(YamlTypes.CONNECTOR_REF, infraSpecNode, yamlPropertiesMap);
    addVariableForYamlType(YamlTypes.NAMESPACE, infraSpecNode, yamlPropertiesMap);
    addVariableForYamlType(YamlTypes.RELEASE_NAME, infraSpecNode, yamlPropertiesMap);
    addVariableForYamlType(YamlTypes.CLUSTER, infraSpecNode, yamlPropertiesMap);
  }

  private void addVariablesForKubernetesAzureInfra(
      YamlField infraDefNode, Map<String, YamlProperties> yamlPropertiesMap) {
    YamlField infraSpecNode = infraDefNode.getNode().getField(YamlTypes.SPEC);
    if (infraSpecNode == null) {
      return;
    }

    addVariableForYamlType(YamlTypes.CONNECTOR_REF, infraSpecNode, yamlPropertiesMap);
    addVariableForYamlType(YamlTypes.SUBSCRIPTION, infraSpecNode, yamlPropertiesMap);
    addVariableForYamlType(YamlTypes.RESOURCE_GROUP, infraSpecNode, yamlPropertiesMap);
    addVariableForYamlType(YamlTypes.CLUSTER, infraSpecNode, yamlPropertiesMap);
    addVariableForYamlType(YamlTypes.NAMESPACE, infraSpecNode, yamlPropertiesMap);
    addVariableForYamlType(YamlTypes.RELEASE_NAME, infraSpecNode, yamlPropertiesMap);
  }

  private void addVariablesForAzureWebAppInfra(YamlField infraDefNode, Map<String, YamlProperties> yamlPropertiesMap) {
    YamlField infraSpecNode = infraDefNode.getNode().getField(YamlTypes.SPEC);
    if (infraSpecNode == null) {
      return;
    }

    addVariableForYamlType(YamlTypes.CONNECTOR_REF, infraSpecNode, yamlPropertiesMap);
    addVariableForYamlType(YamlTypes.SUBSCRIPTION, infraSpecNode, yamlPropertiesMap);
    addVariableForYamlType(YamlTypes.RESOURCE_GROUP, infraSpecNode, yamlPropertiesMap);
    addVariableForYamlType(YamlTypes.APP_SERVICE, infraSpecNode, yamlPropertiesMap);
    addVariableForYamlType(YamlTypes.DEPLOYMENT_SLOT, infraSpecNode, yamlPropertiesMap);
  }

  private static void addVariablesForPhysicalDataCenterInfra(
      YamlField infraDefNode, Map<String, YamlProperties> yamlPropertiesMap) {
    YamlField infraSpecNode = infraDefNode.getNode().getField(YamlTypes.SPEC);
    if (infraSpecNode == null) {
      return;
    }

    addVariableForYamlType(YamlTypes.CREDENTIALS_REF, infraSpecNode, yamlPropertiesMap);
    addVariableForYamlType(YamlTypes.HOSTS, infraSpecNode, yamlPropertiesMap);
    addVariableForYamlType(YamlTypes.CONNECTOR_REF, infraSpecNode, yamlPropertiesMap);
    addVariableForYamlType(YamlTypes.HOST_FILTER, infraSpecNode, yamlPropertiesMap);
    addVariableForYamlType(YamlTypes.DELEGATE_SELECTORS, infraSpecNode, yamlPropertiesMap);
  }

  private static void addVariablesForSshWinRmAwsInfra(
      YamlField infraDefNode, Map<String, YamlProperties> yamlPropertiesMap) {
    YamlField infraSpecNode = infraDefNode.getNode().getField(YamlTypes.SPEC);
    if (infraSpecNode == null) {
      return;
    }

    addVariableForYamlType(YamlTypes.CONNECTOR_REF, infraSpecNode, yamlPropertiesMap);
    addVariableForYamlType(YamlTypes.REGION, infraSpecNode, yamlPropertiesMap);
    addVariableForYamlType(YamlTypes.LOAD_BALANCER, infraSpecNode, yamlPropertiesMap);
    addVariableForYamlType(YamlTypes.HOST_NAME_CONVENTION, infraSpecNode, yamlPropertiesMap);
  }

  private void addVariablesForServerlessAwsInfra(
      YamlField infraDefNode, Map<String, YamlProperties> yamlPropertiesMap) {
    YamlField infraSpecNode = infraDefNode.getNode().getField(YamlTypes.SPEC);
    if (infraSpecNode == null) {
      return;
    }

    addVariableForYamlType(YamlTypes.CONNECTOR_REF, infraSpecNode, yamlPropertiesMap);
    addVariableForYamlType(YamlTypes.REGION, infraSpecNode, yamlPropertiesMap);
    addVariableForYamlType(YamlTypes.STAGE, infraSpecNode, yamlPropertiesMap);
  }

  private void addVariablesForEcsAwsInfra(YamlField infraDefNode, Map<String, YamlProperties> yamlPropertiesMap) {
    YamlField infraSpecNode = infraDefNode.getNode().getField(YamlTypes.SPEC);
    if (infraSpecNode == null) {
      return;
    }

    addVariableForYamlType(YamlTypes.CONNECTOR_REF, infraSpecNode, yamlPropertiesMap);
    addVariableForYamlType(YamlTypes.REGION, infraSpecNode, yamlPropertiesMap);
    addVariableForYamlType(YamlTypes.CLUSTER, infraSpecNode, yamlPropertiesMap);
  }

  private void addVariablesForGoogleCloudFunctionsInfra(
      YamlField infraDefNode, Map<String, YamlProperties> yamlPropertiesMap) {
    YamlField infraSpecNode = infraDefNode.getNode().getField(YamlTypes.SPEC);
    if (infraSpecNode == null) {
      return;
    }

    addVariableForYamlType(YamlTypes.CONNECTOR_REF, infraSpecNode, yamlPropertiesMap);
    addVariableForYamlType(YamlTypes.REGION, infraSpecNode, yamlPropertiesMap);
    addVariableForYamlType(YamlTypes.PROJECT, infraSpecNode, yamlPropertiesMap);
  }

  private void addVariablesForElastigroupInfra(YamlField infraDefNode, Map<String, YamlProperties> yamlPropertiesMap) {
    YamlField infraSpecNode = infraDefNode.getNode().getField(YamlTypes.SPEC);
    if (infraSpecNode == null) {
      return;
    }

    addVariableForYamlType(YamlTypes.CONNECTOR_REF, infraSpecNode, yamlPropertiesMap);
    addVariableForYamlType(YamlTypes.CONFIGURATION, infraSpecNode, yamlPropertiesMap);
  }

  private void addVariablesForAsgInfra(YamlField infraDefNode, Map<String, YamlProperties> yamlPropertiesMap) {
    YamlField infraSpecNode = infraDefNode.getNode().getField(YamlTypes.SPEC);
    if (infraSpecNode == null) {
      return;
    }

    addVariableForYamlType(YamlTypes.CONNECTOR_REF, infraSpecNode, yamlPropertiesMap);
    addVariableForYamlType(YamlTypes.REGION, infraSpecNode, yamlPropertiesMap);
  }

  private void addVariableForYamlType(
      String yamlTypes, YamlField infraSpecNode, Map<String, YamlProperties> yamlPropertiesMap) {
    YamlField yamlNode = infraSpecNode.getNode().getField(yamlTypes);
    if (yamlNode != null) {
      VariableCreatorHelper.addFieldToPropertiesMap(yamlNode, yamlPropertiesMap, YamlTypes.PIPELINE_INFRASTRUCTURE);
    }
  }

  private void addVariablesForAwsLambdaInfra(YamlField infraDefNode, Map<String, YamlProperties> yamlPropertiesMap) {
    YamlField infraSpecNode = infraDefNode.getNode().getField(YamlTypes.SPEC);
    if (infraSpecNode == null) {
      return;
    }

    addVariableForYamlType(YamlTypes.CONNECTOR_REF, infraSpecNode, yamlPropertiesMap);
    addVariableForYamlType(YamlTypes.REGION, infraSpecNode, yamlPropertiesMap);
  }

  private void addVariablesForKubernetesAwsInfra(
      YamlField infraDefNode, Map<String, YamlProperties> yamlPropertiesMap) {
    YamlField infraSpecNode = infraDefNode.getNode().getField(YamlTypes.SPEC);
    if (infraSpecNode == null) {
      return;
    }

    addVariableForYamlType(YamlTypes.CONNECTOR_REF, infraSpecNode, yamlPropertiesMap);
    addVariableForYamlType(YamlTypes.CLUSTER, infraSpecNode, yamlPropertiesMap);
    addVariableForYamlType(YamlTypes.NAMESPACE, infraSpecNode, yamlPropertiesMap);
    addVariableForYamlType(YamlTypes.RELEASE_NAME, infraSpecNode, yamlPropertiesMap);
  }
}
