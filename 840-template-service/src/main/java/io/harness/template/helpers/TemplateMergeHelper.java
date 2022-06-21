/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.template.helpers;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.exception.WingsException.USER;
import static io.harness.pms.merger.helpers.MergeHelper.mergeInputSetFormatYamlToOriginYaml;
import static io.harness.pms.yaml.validation.RuntimeInputValuesValidator.validateStaticValues;
import static io.harness.template.beans.NGTemplateConstants.DUMMY_NODE;
import static io.harness.template.beans.NGTemplateConstants.SPEC;
import static io.harness.template.beans.NGTemplateConstants.TEMPLATE;
import static io.harness.template.beans.NGTemplateConstants.TEMPLATE_INPUTS;
import static io.harness.template.beans.NGTemplateConstants.TEMPLATE_REF;
import static io.harness.template.beans.NGTemplateConstants.TEMPLATE_VERSION_LABEL;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.IdentifierRef;
import io.harness.common.NGExpressionUtils;
import io.harness.exception.ngexception.NGTemplateException;
import io.harness.exception.ngexception.beans.templateservice.TemplateInputsErrorDTO;
import io.harness.exception.ngexception.beans.templateservice.TemplateInputsErrorMetadataDTO;
import io.harness.ng.core.template.TemplateMergeResponseDTO;
import io.harness.ng.core.template.TemplateReferenceSummary;
import io.harness.ng.core.template.exception.NGTemplateResolveException;
import io.harness.pms.merger.YamlConfig;
import io.harness.pms.merger.fqn.FQN;
import io.harness.pms.merger.fqn.FQNNode;
import io.harness.pms.merger.fqn.FQNNode.NodeType;
import io.harness.pms.merger.helpers.RuntimeInputFormHelper;
import io.harness.pms.merger.helpers.YamlSubMapExtractor;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlNode;
import io.harness.pms.yaml.YamlUtils;
import io.harness.serializer.JsonUtils;
import io.harness.template.beans.yaml.NGTemplateConfig;
import io.harness.template.entity.TemplateEntity;
import io.harness.template.services.NGTemplateService;
import io.harness.utils.IdentifierRefHelper;
import io.harness.utils.YamlPipelineUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.CDC)
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
public class TemplateMergeHelper {
  private NGTemplateService templateService;
  private TemplateMergeServiceHelper templateMergeServiceHelper;

  public String getTemplateInputs(String accountId, String orgIdentifier, String projectIdentifier,
      String templateIdentifier, String versionLabel) {
    Optional<TemplateEntity> optionalTemplateEntity = templateService.getOrThrowExceptionIfInvalid(
        accountId, orgIdentifier, projectIdentifier, templateIdentifier, versionLabel, false);
    if (!optionalTemplateEntity.isPresent()) {
      throw new NGTemplateException("Template to fetch template inputs does not exist.");
    }
    return createTemplateInputsFromTemplate(optionalTemplateEntity.get().getYaml());
  }

  /**
   * This method gets the template inputs from template.spec in template yaml.
   * For eg: Template Yaml:
   * template:
   *   identifier: httpTemplate
   *   versionLabel: 1
   *   name: template1
   *   type: Step
   *   spec:
   *     type: Http
   *     spec:
   *       url: <+input>
   *       method: GET
   *     timeout: <+input>
   *
   * Output template inputs yaml:
   * type: Http
   * spec:
   *   url: <+input>
   * timeout: <+input>
   *
   * @param yaml - template yaml
   * @return template inputs yaml
   */
  @VisibleForTesting
  String createTemplateInputsFromTemplate(String yaml) {
    try {
      if (isEmpty(yaml)) {
        throw new NGTemplateException("Template yaml to create template inputs cannot be empty");
      }
      YamlField templateYamlField = YamlUtils.readTree(yaml).getNode().getField(TEMPLATE);
      if (templateYamlField == null) {
        log.error("Yaml provided is not a template yaml. Yaml:\n" + yaml);
        throw new NGTemplateException("Yaml provided is not a template yaml.");
      }
      ObjectNode templateNode = (ObjectNode) templateYamlField.getNode().getCurrJsonNode();
      String templateSpec = templateNode.retain(SPEC).toString();
      if (isEmpty(templateSpec)) {
        log.error("Template yaml provided does not have spec in it.");
        throw new NGTemplateException("Template yaml provided does not have spec in it.");
      }
      String templateInputsYamlWithSpec = RuntimeInputFormHelper.createTemplateFromYaml(templateSpec);
      if (isEmpty(templateInputsYamlWithSpec)) {
        return templateInputsYamlWithSpec;
      }
      JsonNode templateInputsYaml =
          YamlUtils.readTree(templateInputsYamlWithSpec).getNode().getCurrJsonNode().get(SPEC);
      return convertToYaml(templateInputsYaml);
    } catch (IOException e) {
      log.error("Error occurred while creating template inputs " + e);
      throw new NGTemplateException("Error occurred while creating template inputs ", e);
    }
  }

  private String convertToYaml(Object object) {
    return YamlUtils.write(object).replace("---\n", "");
  }

  /**
   * The method replaces all template occurrences in yaml with information present under template.spec in
   * template yaml. Before we replace template occurrence in yaml, we merge template inputs provided in
   * yaml to template.spec in template yaml
   * @param accountId - accountId of pipeline
   * @param orgId - organisationId of pipeline
   * @param projectId - projectId of pipeline
   * @param yaml - yaml
   * @param getMergedYamlWithTemplateField - Returns merged Yaml with templates Fields as well OPA policies If set
   * @return final yaml with all template occurrences replaced with actual template information.
   */
  public TemplateMergeResponseDTO applyTemplatesToYaml(
      String accountId, String orgId, String projectId, String yaml, boolean getMergedYamlWithTemplateField) {
    if (isEmpty(yaml)) {
      throw new NGTemplateException("Yaml to applyTemplates cannot be empty.");
    }
    YamlNode yamlNode;
    try {
      yamlNode = YamlUtils.readTree(yaml).getNode();
    } catch (IOException e) {
      log.error("Could not convert yaml to JsonNode. Yaml:\n" + yaml, e);
      throw new NGTemplateException("Could not convert yaml to JsonNode: " + e.getMessage());
    }

    Map<String, TemplateEntity> templateCacheMap = new HashMap<>();
    TemplateInputsErrorMetadataDTO errorResponse =
        validateLinkedTemplateInputsInYaml(accountId, orgId, projectId, yamlNode, templateCacheMap);
    if (errorResponse != null) {
      throw new NGTemplateResolveException("Exception in resolving template refs in given yaml.", USER, errorResponse);
    }
    Map<String, Object> resMap;
    MergeTemplateInputsInObject mergeTemplateInputsInObject = null;
    if (!getMergedYamlWithTemplateField) {
      resMap = mergeTemplateInputsInObject(accountId, orgId, projectId, yamlNode, templateCacheMap);
    } else {
      mergeTemplateInputsInObject =
          mergeTemplateInputsInObjectAlongWithOpaPolicy(accountId, orgId, projectId, yamlNode, templateCacheMap);
      resMap = mergeTemplateInputsInObject.getResMap();
    }

    List<TemplateReferenceSummary> templateReferenceSummaries =
        getTemplateReferenceSummaries(accountId, orgId, projectId, yaml);
    return TemplateMergeResponseDTO.builder()
        .mergedPipelineYaml(convertToYaml(resMap))
        .templateReferenceSummaries(templateReferenceSummaries)
        .mergedPipelineYamlWithTemplateRef(mergeTemplateInputsInObject == null
                ? null
                : convertToYaml(mergeTemplateInputsInObject.getResMapWithOpaResponse()))
        .build();
  }

  private List<TemplateReferenceSummary> getTemplateReferenceSummaries(
      String accountId, String orgId, String projectId, String pipelineYaml) {
    YamlConfig yamlConfig = new YamlConfig(pipelineYaml);
    Map<FQN, Object> fqnToValueMap = yamlConfig.getFqnToValueMap();
    Set<FQN> fqnSet = new LinkedHashSet<>(yamlConfig.getFqnToValueMap().keySet());
    List<TemplateReferenceSummary> templateReferenceSummaries = new ArrayList<>();
    fqnSet.forEach(key -> {
      if (key.getFqnList().size() >= 2) {
        List<FQNNode> fqnList = new ArrayList<>(key.getFqnList());
        FQNNode lastNode = fqnList.get(fqnList.size() - 1);
        FQNNode secondLastNode = fqnList.get(fqnList.size() - 2);
        if (TEMPLATE_REF.equals(lastNode.getKey()) && TEMPLATE.equals(secondLastNode.getKey())) {
          String identifier = ((JsonNode) fqnToValueMap.get(key)).asText();
          IdentifierRef templateIdentifierRef =
              IdentifierRefHelper.getIdentifierRef(identifier, accountId, orgId, projectId);

          // remove templateRef from FQN and add versionLabel to FQN to fetch corresponding template version.
          fqnList.remove(fqnList.size() - 1);
          fqnList.add(FQNNode.builder().nodeType(NodeType.KEY).key(TEMPLATE_VERSION_LABEL).build());
          JsonNode versionLabelNode = (JsonNode) fqnToValueMap.get(FQN.builder().fqnList(fqnList).build());
          String versionLabel = "";
          boolean isStableTemplate = false;
          if (versionLabelNode == null) {
            isStableTemplate = true;
            Optional<TemplateEntity> templateEntity =
                templateService.getOrThrowExceptionIfInvalid(templateIdentifierRef.getAccountIdentifier(),
                    templateIdentifierRef.getOrgIdentifier(), templateIdentifierRef.getProjectIdentifier(),
                    templateIdentifierRef.getIdentifier(), versionLabel, false);
            if (templateEntity.isPresent()) {
              versionLabel = templateEntity.get().getVersionLabel();
            }
          } else {
            versionLabel = versionLabelNode.asText();
          }

          // remove template and versionLabel from FQN to construct base FQN.
          fqnList.remove(fqnList.size() - 1);
          fqnList.remove(fqnList.size() - 1);
          TemplateReferenceSummary templateReferenceSummary =
              TemplateReferenceSummary.builder()
                  .fqn(FQN.builder().fqnList(fqnList).build().getExpressionFqn())
                  .templateIdentifier(templateIdentifierRef.getIdentifier())
                  .versionLabel(versionLabel)
                  .scope(templateIdentifierRef.getScope())
                  .stableTemplate(isStableTemplate)
                  .build();
          templateReferenceSummaries.add(templateReferenceSummary);
        }
      }
    });
    return templateReferenceSummaries;
  }

  /**
   * This method iterates recursively on pipeline yaml. Whenever we find a key with "template" we call
   * replaceTemplateOccurrenceWithTemplateSpecYaml() to get the actual template.spec in template yaml.
   */
  private Map<String, Object> mergeTemplateInputsInObject(String accountId, String orgId, String projectId,
      YamlNode yamlNode, Map<String, TemplateEntity> templateCacheMap) {
    Map<String, Object> resMap = new LinkedHashMap<>();
    for (YamlField childYamlField : yamlNode.fields()) {
      String fieldName = childYamlField.getName();
      JsonNode value = childYamlField.getNode().getCurrJsonNode();
      boolean isTemplatePresent = templateMergeServiceHelper.isTemplatePresent(fieldName, value);
      if (isTemplatePresent) {
        value = replaceTemplateOccurrenceWithTemplateSpecYaml(accountId, orgId, projectId, value, templateCacheMap);
      }
      if (value.isValueNode() || YamlUtils.checkIfNodeIsArrayWithPrimitiveTypes(value)) {
        resMap.put(fieldName, value);
      } else if (value.isArray()) {
        resMap.put(fieldName,
            mergeTemplateInputsInArray(accountId, orgId, projectId, childYamlField.getNode(), templateCacheMap));
      } else {
        // If it was template key in yaml, we have replace it with the fields in template.spec in template yaml.
        // Hence, we directly put all the keys returned in map, after iterating over them.
        if (isTemplatePresent) {
          Map<String, Object> temp = mergeTemplateInputsInObject(accountId, orgId, projectId,
              new YamlNode(fieldName, value, childYamlField.getNode().getParentNode()), templateCacheMap);
          resMap.putAll(temp);
        } else {
          resMap.put(fieldName,
              mergeTemplateInputsInObject(accountId, orgId, projectId, childYamlField.getNode(), templateCacheMap));
        }
      }
    }
    return resMap;
  }

  private List<Object> mergeTemplateInputsInArray(String accountId, String orgId, String projectId, YamlNode yamlNode,
      Map<String, TemplateEntity> templateCacheMap) {
    List<Object> arrayList = new ArrayList<>();
    for (YamlNode arrayElement : yamlNode.asArray()) {
      if (yamlNode.getCurrJsonNode().isValueNode()) {
        arrayList.add(arrayElement);
      } else if (arrayElement.isArray()) {
        arrayList.add(mergeTemplateInputsInArray(accountId, orgId, projectId, arrayElement, templateCacheMap));
      } else {
        arrayList.add(mergeTemplateInputsInObject(accountId, orgId, projectId, arrayElement, templateCacheMap));
      }
    }
    return arrayList;
  }

  /**
   * This method Provides all the information from mergeTemplateInputsInObject method along with template references.
   */
  private MergeTemplateInputsInObject mergeTemplateInputsInObjectAlongWithOpaPolicy(String accountId, String orgId,
      String projectId, YamlNode yamlNode, Map<String, TemplateEntity> templateCacheMap) {
    Map<String, Object> resMap = new LinkedHashMap<>();
    Map<String, Object> resMapWithTemplateRef = new LinkedHashMap<>();
    for (YamlField childYamlField : yamlNode.fields()) {
      String fieldName = childYamlField.getName();
      JsonNode value = childYamlField.getNode().getCurrJsonNode();
      boolean isTemplatePresent = templateMergeServiceHelper.isTemplatePresent(fieldName, value);
      if (isTemplatePresent) {
        Map<String, Object> result = JsonUtils.jsonNodeToMap(value);
        resMapWithTemplateRef.put(fieldName, result);
        value = replaceTemplateOccurrenceWithTemplateSpecYaml(accountId, orgId, projectId, value, templateCacheMap);
      }
      if (value.isValueNode() || YamlUtils.checkIfNodeIsArrayWithPrimitiveTypes(value)) {
        resMap.put(fieldName, value);
        resMapWithTemplateRef.put(fieldName, value);
      } else if (value.isArray()) {
        ArrayListForMergedTemplateRef arrayLists = mergeTemplateInputsInArrayWithOpaPolicy(
            accountId, orgId, projectId, childYamlField.getNode(), templateCacheMap);
        resMap.put(fieldName, arrayLists.getArrayList());
        resMapWithTemplateRef.put(fieldName, arrayLists.getArrayListWithTemplateRef());
      } else {
        // If it was template key in yaml, we have replace it with the fields in template.spec in template yaml.
        // Hence, we directly put all the keys returned in map, after iterating over them.
        if (isTemplatePresent) {
          MergeTemplateInputsInObject temp = mergeTemplateInputsInObjectAlongWithOpaPolicy(accountId, orgId, projectId,
              new YamlNode(fieldName, value, childYamlField.getNode().getParentNode()), templateCacheMap);
          resMap.putAll(temp.getResMap());
          resMapWithTemplateRef.putAll(temp.getResMapWithOpaResponse());
        } else {
          MergeTemplateInputsInObject temp = mergeTemplateInputsInObjectAlongWithOpaPolicy(
              accountId, orgId, projectId, childYamlField.getNode(), templateCacheMap);
          resMap.put(fieldName, temp.getResMap());
          resMapWithTemplateRef.put(fieldName, temp.getResMapWithOpaResponse());
        }
      }
    }
    return MergeTemplateInputsInObject.builder().resMap(resMap).resMapWithOpaResponse(resMapWithTemplateRef).build();
  }

  private ArrayListForMergedTemplateRef mergeTemplateInputsInArrayWithOpaPolicy(String accountId, String orgId,
      String projectId, YamlNode yamlNode, Map<String, TemplateEntity> templateCacheMap) {
    List<Object> arrayList = new ArrayList<>();
    List<Object> arrayListWithTemplateRef = new ArrayList<>();
    for (YamlNode arrayElement : yamlNode.asArray()) {
      if (yamlNode.getCurrJsonNode().isValueNode()) {
        arrayList.add(arrayElement);
        arrayListWithTemplateRef.add(arrayElement);
      } else if (arrayElement.isArray()) {
        ArrayListForMergedTemplateRef arrayListForMergedTemplateRef =
            mergeTemplateInputsInArrayWithOpaPolicy(accountId, orgId, projectId, arrayElement, templateCacheMap);
        arrayList.add(arrayListForMergedTemplateRef.getArrayList());
        arrayListWithTemplateRef.add(arrayListForMergedTemplateRef.getArrayListWithTemplateRef());
      } else {
        MergeTemplateInputsInObject temp =
            mergeTemplateInputsInObjectAlongWithOpaPolicy(accountId, orgId, projectId, arrayElement, templateCacheMap);
        arrayList.add(temp.getResMap());
        arrayListWithTemplateRef.add(temp.getResMapWithOpaResponse());
      }
    }
    return ArrayListForMergedTemplateRef.builder()
        .arrayList(arrayList)
        .arrayListWithTemplateRef(arrayListWithTemplateRef)
        .build();
  }

  /**
   * This method gets the TemplateEntity from database. Further it gets template yaml and merge template inputs present
   * in pipeline to template.spec in template yaml
   * @param template - template json node present in pipeline yaml
   * @param templateCacheMap
   * @return jsonNode of merged yaml
   */
  private JsonNode replaceTemplateOccurrenceWithTemplateSpecYaml(String accountId, String orgId, String projectId,
      JsonNode template, Map<String, TemplateEntity> templateCacheMap) {
    JsonNode templateInputs = template.get(TEMPLATE_INPUTS);

    TemplateEntity templateEntity =
        templateMergeServiceHelper.getLinkedTemplateEntity(accountId, orgId, projectId, template, templateCacheMap);
    String templateYaml = templateEntity.getYaml();

    JsonNode templateSpec;
    try {
      NGTemplateConfig templateConfig = YamlPipelineUtils.read(templateYaml, NGTemplateConfig.class);
      templateSpec = templateConfig.getTemplateInfoConfig().getSpec();
    } catch (IOException e) {
      log.error("Could not read template yaml", e);
      throw new NGTemplateException("Could not read template yaml: " + e.getMessage());
    }

    return mergeTemplateInputsToTemplateSpecInTemplateYaml(templateInputs, templateSpec);
  }

  /**
   * This method merges template inputs provided in pipeline yaml to template spec in template yaml.
   * @param templateInputs - template runtime info provided in pipeline yaml
   * @param templateSpec - template spec present in template yaml
   * @return jsonNode of merged yaml
   */
  private JsonNode mergeTemplateInputsToTemplateSpecInTemplateYaml(JsonNode templateInputs, JsonNode templateSpec) {
    Map<String, JsonNode> dummyTemplateSpecMap = new LinkedHashMap<>();
    dummyTemplateSpecMap.put(DUMMY_NODE, templateSpec);
    String dummyTemplateSpecYaml = convertToYaml(dummyTemplateSpecMap);

    String mergedYaml = dummyTemplateSpecYaml;
    String dummyTemplateInputsYaml = "";
    if (templateInputs != null) {
      Map<String, JsonNode> dummyTemplateInputsMap = new LinkedHashMap<>();
      dummyTemplateInputsMap.put(DUMMY_NODE, templateInputs);
      dummyTemplateInputsYaml = convertToYaml(dummyTemplateInputsMap);

      mergedYaml = mergeInputSetFormatYamlToOriginYaml(dummyTemplateSpecYaml, dummyTemplateInputsYaml);
    }

    try {
      String finalMergedYaml = removeOmittedRuntimeInputsFromMergedYaml(mergedYaml, dummyTemplateInputsYaml);
      return YamlUtils.readTree(finalMergedYaml).getNode().getCurrJsonNode().get(DUMMY_NODE);
    } catch (IOException e) {
      log.error("Could not convert merged yaml to JsonNode. Yaml:\n" + mergedYaml, e);
      throw new NGTemplateException("Could not convert merged yaml to JsonNode: " + e.getMessage());
    }
  }

  private String removeOmittedRuntimeInputsFromMergedYaml(String mergedYaml, String templateInputsYaml)
      throws IOException {
    JsonNode mergedYamlNode = YamlUtils.readTree(mergedYaml).getNode().getCurrJsonNode();

    YamlConfig mergedYamlConfig = new YamlConfig(mergedYaml);
    Map<FQN, Object> mergedYamlConfigMap = mergedYamlConfig.getFqnToValueMap();
    Map<FQN, Object> templateInputsYamlConfigMap = new HashMap<>();
    if (isNotEmpty(templateInputsYaml)) {
      YamlConfig templateInputsYamlConfig = new YamlConfig(templateInputsYaml);
      templateInputsYamlConfigMap = templateInputsYamlConfig.getFqnToValueMap();
    }
    Map<FQN, Object> resMap = new LinkedHashMap<>();

    for (FQN key : mergedYamlConfigMap.keySet()) {
      Object value = mergedYamlConfigMap.get(key);
      if (!templateInputsYamlConfigMap.containsKey(key) && !(value instanceof ArrayNode)) {
        String mergedValue = ((JsonNode) value).asText();
        if (!NGExpressionUtils.matchesInputSetPattern(mergedValue)) {
          resMap.put(key, value);
        }
      } else {
        resMap.put(key, value);
      }
    }

    return (new YamlConfig(resMap, mergedYamlNode)).getYaml();
  }

  /**
   * This method validates the template inputs in linked templates in yaml
   * @param yamlNode - YamlNode on which we need to validate template inputs in linked template.
   * @param accountId
   * @param orgId
   * @param projectId
   * @param templateCacheMap
   * @return
   */
  public TemplateInputsErrorMetadataDTO validateLinkedTemplateInputsInYaml(String accountId, String orgId,
      String projectId, YamlNode yamlNode, Map<String, TemplateEntity> templateCacheMap) {
    Map<String, TemplateInputsErrorDTO> templateInputsErrorMap = new LinkedHashMap<>();
    Map<String, Object> errorYamlMap =
        validateTemplateInputsInObject(accountId, orgId, projectId, yamlNode, templateInputsErrorMap, templateCacheMap);
    if (isEmpty(templateInputsErrorMap)) {
      return null;
    }
    String errorYaml = convertToYaml(errorYamlMap);
    String errorTemplateYaml = convertUuidErrorMapToFqnErrorMap(errorYaml, templateInputsErrorMap);
    return new TemplateInputsErrorMetadataDTO(errorTemplateYaml, templateInputsErrorMap);
  }

  private Map<String, Object> validateTemplateInputsInObject(String accountId, String orgId, String projectId,
      YamlNode yamlNode, Map<String, TemplateInputsErrorDTO> templateInputsErrorMap,
      Map<String, TemplateEntity> templateCacheMap) {
    Map<String, Object> resMap = new LinkedHashMap<>();
    for (YamlField childYamlField : yamlNode.fields()) {
      String fieldName = childYamlField.getName();
      JsonNode value = childYamlField.getNode().getCurrJsonNode();
      if (templateMergeServiceHelper.isTemplatePresent(fieldName, value)) {
        resMap.put(fieldName,
            validateTemplateInputs(accountId, orgId, projectId, value, templateInputsErrorMap, templateCacheMap));
        continue;
      }
      if (value.isValueNode() || YamlUtils.checkIfNodeIsArrayWithPrimitiveTypes(value)) {
        resMap.put(fieldName, value);
      } else if (value.isArray()) {
        resMap.put(fieldName,
            validateTemplateInputsInArray(
                accountId, orgId, projectId, childYamlField.getNode(), templateInputsErrorMap, templateCacheMap));
      } else {
        resMap.put(fieldName,
            validateTemplateInputsInObject(
                accountId, orgId, projectId, childYamlField.getNode(), templateInputsErrorMap, templateCacheMap));
      }
    }
    return resMap;
  }

  private Object validateTemplateInputsInArray(String accountId, String orgId, String projectId, YamlNode yamlNode,
      Map<String, TemplateInputsErrorDTO> templateInputsErrorMap, Map<String, TemplateEntity> templateCacheMap) {
    List<Object> arrayList = new ArrayList<>();
    for (YamlNode arrayElement : yamlNode.asArray()) {
      if (yamlNode.getCurrJsonNode().isValueNode()) {
        arrayList.add(arrayElement);
      } else if (arrayElement.isObject()) {
        arrayList.add(validateTemplateInputsInObject(
            accountId, orgId, projectId, arrayElement, templateInputsErrorMap, templateCacheMap));
      } else {
        arrayList.add(validateTemplateInputsInArray(
            accountId, orgId, projectId, arrayElement, templateInputsErrorMap, templateCacheMap));
      }
    }
    return arrayList;
  }

  private String convertUuidErrorMapToFqnErrorMap(
      String errorYaml, Map<String, TemplateInputsErrorDTO> uuidToErrorMap) {
    YamlConfig yamlConfig = new YamlConfig(errorYaml);
    Map<FQN, Object> fullMap = yamlConfig.getFqnToValueMap();
    Map<FQN, Object> templateMap = new LinkedHashMap<>();
    Set<String> uuidToErrorMapKeySet = uuidToErrorMap.keySet();
    fullMap.keySet().forEach(key -> {
      String value = fullMap.get(key).toString().replace("\"", "");
      if (uuidToErrorMapKeySet.contains(value)) {
        String uuid = key.getExpressionFqn();
        TemplateInputsErrorDTO templateInputsErrorDTO = uuidToErrorMap.get(value);
        templateInputsErrorDTO.setFieldName(key.getFieldName());
        templateMap.put(key, uuid);
        uuidToErrorMap.put(uuid, templateInputsErrorDTO);
        uuidToErrorMap.remove(value);
      }
    });
    return (new YamlConfig(templateMap, yamlConfig.getYamlMap())).getYaml();
  }

  private JsonNode validateTemplateInputs(String accountId, String orgId, String projectId, JsonNode linkedTemplate,
      Map<String, TemplateInputsErrorDTO> errorMap, Map<String, TemplateEntity> templateCacheMap) {
    String identifier = linkedTemplate.get(TEMPLATE_REF).asText();
    TemplateEntity templateEntity = templateMergeServiceHelper.getLinkedTemplateEntity(
        accountId, orgId, projectId, linkedTemplate, templateCacheMap);
    JsonNode linkedTemplateInputs = linkedTemplate.get(TEMPLATE_INPUTS);
    if (linkedTemplateInputs == null) {
      return linkedTemplate;
    }

    String templateYaml = templateEntity.getYaml();
    String templateSpecInputSetFormatYaml = createTemplateInputsFromTemplate(templateYaml);

    try {
      Map<String, JsonNode> dummyLinkedTemplateInputsMap = new LinkedHashMap<>();
      dummyLinkedTemplateInputsMap.put(DUMMY_NODE, linkedTemplateInputs);
      String dummyLinkedTemplateInputsYaml = convertToYaml(dummyLinkedTemplateInputsMap);

      Map<String, TemplateInputsErrorDTO> uuidToErrorMessageMap = new LinkedHashMap<>();
      String invalidLinkedTemplateInputsYaml;
      if (isNotEmpty(templateSpecInputSetFormatYaml)) {
        JsonNode templateSpecInputSetFormatNode =
            YamlUtils.readTree(templateSpecInputSetFormatYaml).getNode().getCurrJsonNode();
        Map<String, JsonNode> dummyTemplateSpecMap = new LinkedHashMap<>();
        dummyTemplateSpecMap.put(DUMMY_NODE, templateSpecInputSetFormatNode);
        invalidLinkedTemplateInputsYaml = getInvalidInputValuesYaml(
            convertToYaml(dummyTemplateSpecMap), dummyLinkedTemplateInputsYaml, uuidToErrorMessageMap, identifier);
      } else {
        invalidLinkedTemplateInputsYaml = getInvalidInputValuesYaml(
            templateSpecInputSetFormatYaml, dummyLinkedTemplateInputsYaml, uuidToErrorMessageMap, identifier);
      }

      if (isEmpty(uuidToErrorMessageMap)) {
        return linkedTemplate;
      }
      errorMap.putAll(uuidToErrorMessageMap);
      JsonNode invalidLinkedTemplateInputsNode =
          YamlUtils.readTree(invalidLinkedTemplateInputsYaml).getNode().getCurrJsonNode().get(DUMMY_NODE);

      Map<String, Object> originalTemplateMap = JsonUtils.jsonNodeToMap(linkedTemplate);
      originalTemplateMap.put(TEMPLATE_INPUTS, invalidLinkedTemplateInputsNode);
      return YamlUtils.readTree(convertToYaml(originalTemplateMap)).getNode().getCurrJsonNode();
    } catch (IOException e) {
      log.error("Error while validating template inputs yaml ", e);
      throw new NGTemplateException("Error while validating template inputs yaml: " + e.getMessage());
    }
  }

  private String getInvalidInputValuesYaml(String templateSpecInputSetFormatYaml, String linkedTemplateInputsYaml,
      Map<String, TemplateInputsErrorDTO> uuidToErrorMessageMap, String templateRef) {
    YamlConfig linkedTemplateInputsConfig = new YamlConfig(linkedTemplateInputsYaml);
    Set<FQN> linkedTemplateInputsFQNs = new LinkedHashSet<>(linkedTemplateInputsConfig.getFqnToValueMap().keySet());
    if (isEmpty(templateSpecInputSetFormatYaml)) {
      return markAllRuntimeInputsInvalid(uuidToErrorMessageMap, templateRef, linkedTemplateInputsConfig,
          linkedTemplateInputsFQNs, "Template no longer contains any runtime input");
    }

    YamlConfig templateSpecInputSetFormatConfig = new YamlConfig(templateSpecInputSetFormatYaml);

    templateSpecInputSetFormatConfig.getFqnToValueMap().keySet().forEach(key -> {
      if (linkedTemplateInputsFQNs.contains(key)) {
        Object templateValue = templateSpecInputSetFormatConfig.getFqnToValueMap().get(key);
        Object linkedTemplateInputValue = linkedTemplateInputsConfig.getFqnToValueMap().get(key);
        if (key.isType() || key.isIdentifierOrVariableName()) {
          if (!linkedTemplateInputValue.toString().equals(templateValue.toString())) {
            String randomUuid = UUID.randomUUID().toString();
            linkedTemplateInputsConfig.getFqnToValueMap().put(key, randomUuid);
            TemplateInputsErrorDTO errorDTO = TemplateInputsErrorDTO.builder()
                                                  .fieldName(randomUuid)
                                                  .message("The value for is " + templateValue.toString()
                                                      + " in the template yaml, but the linked template has it as "
                                                      + linkedTemplateInputValue.toString())
                                                  .identifierOfErrorSource(templateRef)
                                                  .build();
            uuidToErrorMessageMap.put(randomUuid, errorDTO);
          }
        } else {
          String error = validateStaticValues(templateValue, linkedTemplateInputValue);
          if (isNotEmpty(error)) {
            String randomUuid = UUID.randomUUID().toString();
            linkedTemplateInputsConfig.getFqnToValueMap().put(key, randomUuid);
            TemplateInputsErrorDTO errorDTO = TemplateInputsErrorDTO.builder()
                                                  .fieldName(randomUuid)
                                                  .message(error)
                                                  .identifierOfErrorSource(templateRef)
                                                  .build();
            uuidToErrorMessageMap.put(randomUuid, errorDTO);
          }
        }

        linkedTemplateInputsFQNs.remove(key);
      } else {
        Map<FQN, Object> subMap =
            YamlSubMapExtractor.getFQNToObjectSubMap(linkedTemplateInputsConfig.getFqnToValueMap(), key);
        subMap.keySet().forEach(linkedTemplateInputsFQNs::remove);
      }
    });
    return markAllRuntimeInputsInvalid(uuidToErrorMessageMap, templateRef, linkedTemplateInputsConfig,
        linkedTemplateInputsFQNs, "Field either not present in template or not a runtime input");
  }

  private String markAllRuntimeInputsInvalid(Map<String, TemplateInputsErrorDTO> uuidToErrorMessageMap,
      String templateRef, YamlConfig linkedTemplateInputsConfig, Set<FQN> linkedTemplateInputsFQNs,
      String errorMessage) {
    for (FQN fqn : linkedTemplateInputsFQNs) {
      String randomUuid = UUID.randomUUID().toString();
      TemplateInputsErrorDTO errorDTO = TemplateInputsErrorDTO.builder()
                                            .fieldName(randomUuid)
                                            .message(errorMessage)
                                            .identifierOfErrorSource(templateRef)
                                            .build();
      uuidToErrorMessageMap.put(randomUuid, errorDTO);
      linkedTemplateInputsConfig.getFqnToValueMap().put(fqn, randomUuid);
    }
    return new YamlConfig(linkedTemplateInputsConfig.getFqnToValueMap(), linkedTemplateInputsConfig.getYamlMap())
        .getYaml();
  }
}
