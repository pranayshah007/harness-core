/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.creator.variables;

import static io.harness.cdng.manifest.ManifestStoreType.isInGitSubset;
import static io.harness.cdng.manifest.ManifestStoreType.isInStorageRepository;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.manifest.ManifestType;
import io.harness.cdng.visitor.YamlTypes;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.k8s.ServiceSpecType;
import io.harness.pms.contracts.plan.YamlProperties;
import io.harness.pms.sdk.core.variables.VariableCreatorHelper;
import io.harness.pms.sdk.core.variables.beans.VariableCreationResponse;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlNode;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.experimental.UtilityClass;

@OwnedBy(HarnessTeam.CDP)
@UtilityClass
public class ServiceVariableCreator {
  public VariableCreationResponse createVariableResponse(YamlField serviceConfigField) {
    if (serviceConfigField == null) {
      return VariableCreationResponse.builder().build();
    }
    Map<String, YamlProperties> yamlPropertiesMap = new LinkedHashMap<>();
    String serviceUUID = serviceConfigField.getNode().getUuid();
    yamlPropertiesMap.put(serviceUUID, YamlProperties.newBuilder().setFqn(YamlTypes.SERVICE_CONFIG).build());

    YamlField serviceYamlNode = serviceConfigField.getNode().getField(YamlTypes.SERVICE_ENTITY);
    if (VariableCreatorHelper.isNotYamlFieldEmpty(serviceYamlNode)) {
      addVariablesForServiceYaml(serviceYamlNode, yamlPropertiesMap);
    }

    YamlField serviceRefNode = serviceConfigField.getNode().getField(YamlTypes.SERVICE_REF);
    if (serviceRefNode != null) {
      VariableCreatorHelper.addFieldToPropertiesMap(serviceRefNode, yamlPropertiesMap, YamlTypes.SERVICE_CONFIG);
    }

    YamlField serviceDefNode = serviceConfigField.getNode().getField(YamlTypes.SERVICE_DEFINITION);
    if (VariableCreatorHelper.isNotYamlFieldEmpty(serviceDefNode)
        && VariableCreatorHelper.isNotYamlFieldEmpty(serviceDefNode.getNode().getField(YamlTypes.SERVICE_SPEC))) {
      addVariablesForServiceSpec(serviceDefNode, yamlPropertiesMap);
    }
    return VariableCreationResponse.builder().yamlProperties(yamlPropertiesMap).build();
  }

  private void addVariablesForServiceYaml(YamlField serviceYamlNode, Map<String, YamlProperties> yamlPropertiesMap) {
    YamlField nameField = serviceYamlNode.getNode().getField(YAMLFieldNameConstants.NAME);
    if (nameField != null) {
      VariableCreatorHelper.addFieldToPropertiesMap(nameField, yamlPropertiesMap, YamlTypes.SERVICE_CONFIG);
    }
    YamlField descriptionField = serviceYamlNode.getNode().getField(YAMLFieldNameConstants.DESCRIPTION);
    if (descriptionField != null) {
      VariableCreatorHelper.addFieldToPropertiesMap(descriptionField, yamlPropertiesMap, YamlTypes.SERVICE_CONFIG);
    }
    YamlField tagsField = serviceYamlNode.getNode().getField(YAMLFieldNameConstants.TAGS);
    if (VariableCreatorHelper.isNotYamlFieldEmpty(tagsField)) {
      List<YamlField> fields = tagsField.getNode().fields();
      fields.forEach(field -> {
        if (!field.getName().equals(YamlTypes.UUID)) {
          VariableCreatorHelper.addFieldToPropertiesMap(field, yamlPropertiesMap, YamlTypes.SERVICE_CONFIG);
        }
      });
    }
  }

  private void addVariablesForServiceSpec(YamlField serviceDefNode, Map<String, YamlProperties> yamlPropertiesMap) {
    YamlField typeField = serviceDefNode.getNode().getField(YAMLFieldNameConstants.TYPE);
    if (typeField != null) {
      switch (typeField.getNode().getCurrJsonNode().textValue()) {
        case ServiceSpecType.KUBERNETES:
        case ServiceSpecType.NATIVE_HELM:
        case ServiceSpecType.SERVERLESS_AWS_LAMBDA:
          YamlField specNode = serviceDefNode.getNode().getField(YamlTypes.SERVICE_SPEC);
          if (specNode != null) {
            addVariablesForKubernetesHelmServerlessServiceSpec(specNode, yamlPropertiesMap);
          }
          break;
        case ServiceSpecType.SSH:
        case ServiceSpecType.WINRM:
          YamlField sshSpecNode = serviceDefNode.getNode().getField(YamlTypes.SERVICE_SPEC);
          if (sshSpecNode != null) {
            addVariablesForSshServiceSpec(sshSpecNode, yamlPropertiesMap);
          }
          break;
        default:
          throw new InvalidRequestException("Invalid service type");
      }
    }
  }

  private void addVariablesForKubernetesHelmServerlessServiceSpec(
      YamlField serviceSpecNode, Map<String, YamlProperties> yamlPropertiesMap) {
    YamlField artifactsNode = serviceSpecNode.getNode().getField(YamlTypes.ARTIFACT_LIST_CONFIG);
    if (VariableCreatorHelper.isNotYamlFieldEmpty(artifactsNode)) {
      addVariablesForArtifacts(artifactsNode, yamlPropertiesMap);
    }
    YamlField manifestsNode = serviceSpecNode.getNode().getField(YamlTypes.MANIFEST_LIST_CONFIG);
    if (manifestsNode != null) {
      addVariablesForManifests(manifestsNode, yamlPropertiesMap);
    }

    YamlField variablesField = serviceSpecNode.getNode().getField(YAMLFieldNameConstants.VARIABLES);
    if (variablesField != null) {
      VariableCreatorHelper.addVariablesForVariables(variablesField, yamlPropertiesMap, YamlTypes.SERVICE_CONFIG);
    }
  }

  private void addVariablesForSshServiceSpec(YamlField serviceSpecNode, Map<String, YamlProperties> yamlPropertiesMap) {
    YamlField artifactsNode = serviceSpecNode.getNode().getField(YamlTypes.ARTIFACT_LIST_CONFIG);
    if (VariableCreatorHelper.isNotYamlFieldEmpty(artifactsNode)) {
      addVariablesForArtifacts(artifactsNode, yamlPropertiesMap);
    }

    YamlField variablesField = serviceSpecNode.getNode().getField(YAMLFieldNameConstants.VARIABLES);
    if (variablesField != null) {
      VariableCreatorHelper.addVariablesForVariables(variablesField, yamlPropertiesMap, YamlTypes.SERVICE_CONFIG);
    }
  }

  private void addVariablesForArtifacts(YamlField artifactsNode, Map<String, YamlProperties> yamlPropertiesMap) {
    YamlField primaryNode = artifactsNode.getNode().getField(YamlTypes.PRIMARY_ARTIFACT);
    if (VariableCreatorHelper.isNotYamlFieldEmpty(primaryNode)) {
      addVariablesForPrimaryArtifact(primaryNode, yamlPropertiesMap);
    }
    YamlField sidecarsNode = artifactsNode.getNode().getField(YamlTypes.SIDECARS_ARTIFACT_CONFIG);
    if (sidecarsNode != null) {
      addVariablesForArtifactSidecars(sidecarsNode, yamlPropertiesMap);
    }
  }

  private void addVariablesForManifests(YamlField manifestsNode, Map<String, YamlProperties> yamlPropertiesMap) {
    List<YamlNode> manifestNodes = Optional.of(manifestsNode.getNode().asArray()).orElse(Collections.emptyList());
    for (YamlNode manifestNode : manifestNodes) {
      YamlField field = manifestNode.getField(YamlTypes.MANIFEST_CONFIG);
      if (VariableCreatorHelper.isNotYamlFieldEmpty(field)) {
        addVariablesForManifest(field, yamlPropertiesMap);
      }
    }
  }

  private void addVariablesForManifest(YamlField manifestNode, Map<String, YamlProperties> yamlPropertiesMap) {
    YamlField specNode = manifestNode.getNode().getField(YamlTypes.SPEC);
    if (specNode == null) {
      throw new InvalidRequestException("Invalid manifest config");
    }
    switch (manifestNode.getNode().getType()) {
      case ManifestType.K8Manifest:
      case ManifestType.Kustomize:
      case ManifestType.KustomizePatches:
      case ManifestType.OpenshiftTemplate:
        addVariablesFork8sManifest(specNode, yamlPropertiesMap);
        break;
      case ManifestType.VALUES:
      case ManifestType.OpenshiftParam:
        addVariablesForValuesManifest(specNode, yamlPropertiesMap);
        break;
      case ManifestType.HelmChart:
        addVariablesForHelmChartManifest(specNode, yamlPropertiesMap);
        break;
      case ManifestType.ServerlessAwsLambda:
        addVariablesForServerlessAwsStoreConfigYaml(specNode, yamlPropertiesMap);
        break;
      default:
        throw new InvalidRequestException("Invalid manifest type");
    }
  }

  private void addVariablesForHelmChartManifest(
      YamlField manifestSpecNode, Map<String, YamlProperties> yamlPropertiesMap) {
    YamlField storeNode = manifestSpecNode.getNode().getField(YamlTypes.STORE_CONFIG_WRAPPER);
    if (storeNode != null) {
      YamlField specNode = storeNode.getNode().getField(YamlTypes.SPEC);
      if (specNode == null) {
        throw new InvalidRequestException("Invalid store config");
      }

      if (isInGitSubset(storeNode.getNode().getType()) || isInStorageRepository(storeNode.getNode().getType())) {
        addVariablesForGitOrStorage(specNode, yamlPropertiesMap);
      } else {
        throw new InvalidRequestException("Invalid store type");
      }
    }

    List<YamlField> fields = manifestSpecNode.getNode().fields();
    fields.forEach(field -> {
      if (!field.getName().equals(YamlTypes.UUID) && !field.getName().equals(YamlTypes.STORE_CONFIG_WRAPPER)
          && !field.getName().equals(YamlTypes.COMMAND_FLAGS_WRAPPER)) {
        VariableCreatorHelper.addFieldToPropertiesMap(field, yamlPropertiesMap, YamlTypes.SERVICE_CONFIG);
      }
    });
  }

  private void addVariablesFork8sManifest(YamlField manifestSpecNode, Map<String, YamlProperties> yamlPropertiesMap) {
    addVariablesForStoreConfigYaml(manifestSpecNode, yamlPropertiesMap);

    List<YamlField> fields = manifestSpecNode.getNode().fields();
    fields.forEach(field -> {
      if (!field.getName().equals(YamlTypes.UUID) && !field.getName().equals(YamlTypes.STORE_CONFIG_WRAPPER)) {
        VariableCreatorHelper.addFieldToPropertiesMap(field, yamlPropertiesMap, YamlTypes.SERVICE_CONFIG);
      }
    });
  }

  private void addVariablesForValuesManifest(
      YamlField manifestSpecNode, Map<String, YamlProperties> yamlPropertiesMap) {
    addVariablesForStoreConfigYaml(manifestSpecNode, yamlPropertiesMap);
  }

  private void addVariablesForStoreConfigYaml(
      YamlField manifestSpecNode, Map<String, YamlProperties> yamlPropertiesMap) {
    YamlField storeNode = manifestSpecNode.getNode().getField(YamlTypes.STORE_CONFIG_WRAPPER);
    if (storeNode != null) {
      YamlField specNode = storeNode.getNode().getField(YamlTypes.SPEC);
      if (specNode == null) {
        throw new InvalidRequestException("Invalid store config");
      }
      if (isInGitSubset(storeNode.getNode().getType())) {
        addVariablesForGitOrStorage(specNode, yamlPropertiesMap);
      } else {
        throw new InvalidRequestException("Invalid store type");
      }
    }
  }

  private void addVariablesForServerlessAwsStoreConfigYaml(
      YamlField manifestSpecNode, Map<String, YamlProperties> yamlPropertiesMap) {
    addVariablesForStoreConfigYaml(manifestSpecNode, yamlPropertiesMap);
    List<YamlField> fields = manifestSpecNode.getNode().fields();
    fields.forEach(field -> {
      if (!field.getName().equals(YamlTypes.UUID) && !field.getName().equals(YamlTypes.STORE_CONFIG_WRAPPER)) {
        VariableCreatorHelper.addFieldToPropertiesMap(field, yamlPropertiesMap, YamlTypes.SERVICE_CONFIG);
      }
    });
  }

  private void addVariablesForGitOrStorage(YamlField gitNode, Map<String, YamlProperties> yamlPropertiesMap) {
    List<YamlField> fields = gitNode.getNode().fields();
    fields.forEach(field -> {
      if (!field.getName().equals(YamlTypes.UUID)) {
        VariableCreatorHelper.addFieldToPropertiesMap(field, yamlPropertiesMap, YamlTypes.SERVICE_CONFIG);
      }
    });
  }

  private void addVariablesForPrimaryArtifact(
      YamlField primaryArtifactNode, Map<String, YamlProperties> yamlPropertiesMap) {
    YamlField specNode = primaryArtifactNode.getNode().getField(YamlTypes.SPEC);
    if (specNode == null) {
      throw new InvalidRequestException("Invalid artifact config");
    }
    addVariablesForArtifact(specNode, yamlPropertiesMap);
  }

  private void addVariablesForArtifactSidecars(YamlField sidecarsNode, Map<String, YamlProperties> yamlPropertiesMap) {
    List<YamlNode> sidecarNodes = sidecarsNode.getNode().asArray();
    sidecarNodes.forEach(yamlNode -> {
      YamlField field = yamlNode.getField(YamlTypes.SIDECAR_ARTIFACT_CONFIG);
      addVariablesForPrimaryArtifact(field, yamlPropertiesMap);
    });
  }

  private void addVariablesForArtifact(YamlField artifactSpecNode, Map<String, YamlProperties> yamlPropertiesMap) {
    List<YamlField> fields = artifactSpecNode.getNode().fields();
    fields.forEach(field -> {
      if (!field.getName().equals(YamlTypes.UUID)) {
        VariableCreatorHelper.addFieldToPropertiesMap(field, yamlPropertiesMap, YamlTypes.SERVICE_CONFIG);
      }
    });
  }
}
