/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.template.helpers;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.pms.merger.helpers.MergeHelper.mergeInputSetFormatYamlToOriginYaml;
import static io.harness.pms.yaml.validation.RuntimeInputValuesValidator.validateStaticValues;
import static io.harness.template.beans.NGTemplateConstants.*;

import io.harness.account.AccountClient;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.beans.IdentifierRef;
import io.harness.common.EntityReferenceHelper;
import io.harness.common.NGExpressionUtils;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.ngexception.NGTemplateException;
import io.harness.exception.ngexception.beans.templateservice.TemplateInputsErrorDTO;
import io.harness.exception.ngexception.beans.templateservice.TemplateInputsErrorMetadataDTO;
import io.harness.expression.ExpressionMode;
import io.harness.pms.merger.YamlConfig;
import io.harness.pms.merger.fqn.FQN;
import io.harness.pms.merger.helpers.RuntimeInputFormHelper;
import io.harness.pms.merger.helpers.YamlSubMapExtractor;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlNode;
import io.harness.pms.yaml.YamlUtils;
import io.harness.serializer.JsonUtils;
import io.harness.template.beans.yaml.NGTemplateConfig;
import io.harness.template.entity.TemplateEntity;
import io.harness.template.evaluator.TemplateVariableEvaluator;
import io.harness.template.services.NGTemplateServiceHelper;
import io.harness.utils.IdentifierRefHelper;
import io.harness.utils.YamlPipelineUtils;
import io.harness.yaml.core.variables.NGVariable;
import io.harness.yaml.utils.NGVariablesUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.JsonNodeType;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.inject.Inject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
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

/**
 * Class containing common methods w.r.t NGTemplateService and TemplateMergeService
 */
public class TemplateMergeServiceHelper {
  private static final int MAX_DEPTH = 10;
  private NGTemplateServiceHelper templateServiceHelper;
  private AccountClient accountClient;

  // Gets the Template Entity linked to a YAML
  public TemplateEntity getLinkedTemplateEntity(
      String accountId, String orgId, String projectId, JsonNode yaml, Map<String, TemplateEntity> templateCacheMap) {
    String identifier = yaml.get(TEMPLATE_REF).asText();
    String versionLabel = "";
    String versionMarker = STABLE_VERSION;
    if (yaml.get(TEMPLATE_VERSION_LABEL) != null) {
      versionLabel = yaml.get(TEMPLATE_VERSION_LABEL).asText();
      versionMarker = versionLabel;
    }

    return getLinkedTemplateEntityHelper(
        accountId, orgId, projectId, identifier, versionLabel, templateCacheMap, versionMarker);
  }

  // Gets the Template Entity linked to a YAML
  public TemplateEntity getLinkedTemplateEntity(String accountId, String orgId, String projectId, String identifier,
      String versionLabel, Map<String, TemplateEntity> templateCacheMap) {
    String versionMarker = STABLE_VERSION;
    if (!EmptyPredicate.isEmpty(versionLabel)) {
      versionMarker = versionLabel;
    }

    return getLinkedTemplateEntityHelper(
        accountId, orgId, projectId, identifier, versionLabel, templateCacheMap, versionMarker);
  }
  public TemplateEntity getLinkedTemplateEntityHelper(String accountId, String orgId, String projectId,
      String identifier, String versionLabel, Map<String, TemplateEntity> templateCacheMap, String versionMarker) {
    IdentifierRef templateIdentifierRef = IdentifierRefHelper.getIdentifierRef(identifier, accountId, orgId, projectId);
    String templateUniqueIdentifier = generateUniqueTemplateIdentifier(templateIdentifierRef.getAccountIdentifier(),
        templateIdentifierRef.getOrgIdentifier(), templateIdentifierRef.getProjectIdentifier(),
        templateIdentifierRef.getIdentifier(), versionMarker);
    if (templateCacheMap.containsKey(templateUniqueIdentifier)) {
      return templateCacheMap.get(templateUniqueIdentifier);
    }

    Optional<TemplateEntity> templateEntity = templateServiceHelper.getTemplateOrThrowExceptionIfInvalid(
        templateIdentifierRef.getAccountIdentifier(), templateIdentifierRef.getOrgIdentifier(),
        templateIdentifierRef.getProjectIdentifier(), templateIdentifierRef.getIdentifier(), versionLabel, false);
    if (!templateEntity.isPresent()) {
      throw new NGTemplateException(String.format(
          "The template identifier %s and version label %s does not exist. Could not replace this template",
          templateIdentifierRef.getIdentifier(), versionLabel));
    }
    TemplateEntity template = templateEntity.get();
    templateCacheMap.put(templateUniqueIdentifier, template);
    return template;
  }

  // Checks if the current Json node is a Template node with fieldName as TEMPLATE and Non-null Value
  public boolean isTemplatePresent(String fieldName, JsonNode templateValue) {
    return TEMPLATE.equals(fieldName) && templateValue.isObject() && templateValue.get(TEMPLATE_REF) != null;
  }

  // Generates a unique Template Identifier
  private String generateUniqueTemplateIdentifier(
      String accountId, String orgId, String projectId, String templateIdentifier, String versionLabel) {
    List<String> fqnList = new LinkedList<>();
    fqnList.add(accountId);
    if (EmptyPredicate.isNotEmpty(orgId)) {
      fqnList.add(orgId);
    }
    if (EmptyPredicate.isNotEmpty(projectId)) {
      fqnList.add(projectId);
    }
    fqnList.add(templateIdentifier);
    fqnList.add(versionLabel);

    return EntityReferenceHelper.createFQN(fqnList);
  }

  /**
   * This method gets the template inputs from template.spec in template yaml.
   * For eg: Template Yaml:
   * template:
   *  identifier: httpTemplate
   *  versionLabel: 1
   *  name: template1
   *  type: Step
   *  spec:
   *    type: Http
   *    spec:
   *      url: <+input>
   *      method: GET
   *    timeout: <+input>
   * Output template inputs yaml:
   * type: Http
   * spec:
   *  url: <+input>
   * timeout: <+input>
   *
   * @param yaml      - template yaml
   * @param accountId
   * @return template inputs yaml
   */
  public String createTemplateInputsFromTemplate(String yaml, String accountId) {
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
      ObjectNode templateNodeCopy = templateNode.deepCopy();
      String templateSpec = templateNode.retain(SPEC).toString();
      String templateVariableSpec = new ObjectNode(
          JsonNodeFactory.instance, Collections.singletonMap(DUMMY_NODE, templateNodeCopy.retain("variables")))
                                        .toString();
      if (isEmpty(templateSpec)) {
        log.error("Template yaml provided does not have spec in it.");
        throw new NGTemplateException("Template yaml provided does not have spec in it.");
      }
      String templateInputsYamlWithSpec = RuntimeInputFormHelper.createTemplateFromYaml(templateSpec);
      JsonNode templateInputsYaml = templateInputsYamlWithSpec == null
          ? null
          : YamlUtils.readTree(templateInputsYamlWithSpec).getNode().getCurrJsonNode().get(SPEC);
      if (!TemplateYamlSchemaMergeHelper.isFeatureFlagEnabled(
              FeatureName.NG_TEMPLATE_VARIABLES, accountId, accountClient)) {
        if (isEmpty(templateInputsYamlWithSpec)) {
          return templateInputsYamlWithSpec;
        }
        return YamlPipelineUtils.writeYamlString(templateInputsYaml);
      }
      String templateVariablesYamlWithDummy = RuntimeInputFormHelper.createTemplateFromYaml(templateVariableSpec);
      if (isEmpty(templateInputsYamlWithSpec) && isEmpty(templateVariablesYamlWithDummy)) {
        return templateInputsYamlWithSpec;
      }
      JsonNode templateVariablesJson = templateVariablesYamlWithDummy == null
          ? null
          : YamlUtils.readTree(templateVariablesYamlWithDummy)
                .getNode()
                .getCurrJsonNode()
                .get(DUMMY_NODE)
                .get(YAMLFieldNameConstants.VARIABLES);
      ObjectMapper mapper = new ObjectMapper();
      ObjectNode finalTemplateJson = mapper.createObjectNode();
      ;

      if (templateVariablesJson != null) {
        finalTemplateJson.set(TEMPLATE_VARIABLES, templateVariablesJson);
      }
      if (templateInputsYaml != null) {
        finalTemplateJson.set(TEMPLATE_INPUTS, templateInputsYaml);
      }
      return YamlPipelineUtils.writeYamlString(finalTemplateJson);
    } catch (IOException e) {
      log.error("Error occurred while creating template inputs " + e);
      throw new NGTemplateException("Error occurred while creating template inputs ", e);
    }
  }

  /**
   * This method iterates recursively on pipeline yaml. Whenever we find a key with "template" we call
   * replaceTemplateOccurrenceWithTemplateSpecYaml() to get the actual template.spec in template yaml.
   */
  public Map<String, Object> mergeTemplateInputsInObject(String accountId, String orgId, String projectId,
      YamlNode yamlNode, Map<String, TemplateEntity> templateCacheMap, int depth) {
    Map<String, Object> resMap = new LinkedHashMap<>();
    for (YamlField childYamlField : yamlNode.fields()) {
      String fieldName = childYamlField.getName();
      JsonNode value = childYamlField.getNode().getCurrJsonNode();
      boolean isTemplatePresent = isTemplatePresent(fieldName, value);
      if (isTemplatePresent) {
        value = replaceTemplateOccurrenceWithTemplateSpecYaml(accountId, orgId, projectId, value, templateCacheMap);
      }
      if (value.isValueNode() || YamlUtils.checkIfNodeIsArrayWithPrimitiveTypes(value)) {
        resMap.put(fieldName, value);
      } else if (value.isArray()) {
        resMap.put(fieldName,
            mergeTemplateInputsInArray(accountId, orgId, projectId, childYamlField.getNode(), templateCacheMap, depth));
      } else {
        // If it was template key in yaml, we have replace it with the fields in template.spec in template yaml.
        // Hence, we directly put all the keys returned in map, after iterating over them.
        if (isTemplatePresent) {
          depth++;
          if (depth >= MAX_DEPTH) {
            throw new InvalidRequestException("Exponentially growing template nesting. Aborting");
          }
          Map<String, Object> temp = mergeTemplateInputsInObject(accountId, orgId, projectId,
              new YamlNode(fieldName, value, childYamlField.getNode().getParentNode()), templateCacheMap, depth);
          resMap.putAll(temp);
          depth--;
        } else {
          resMap.put(fieldName,
              mergeTemplateInputsInObject(
                  accountId, orgId, projectId, childYamlField.getNode(), templateCacheMap, depth));
        }
      }
    }
    return resMap;
  }

  private List<Object> mergeTemplateInputsInArray(String accountId, String orgId, String projectId, YamlNode yamlNode,
      Map<String, TemplateEntity> templateCacheMap, int depth) {
    List<Object> arrayList = new ArrayList<>();
    for (YamlNode arrayElement : yamlNode.asArray()) {
      if (yamlNode.getCurrJsonNode().isValueNode()) {
        arrayList.add(arrayElement);
      } else if (arrayElement.isArray()) {
        arrayList.add(mergeTemplateInputsInArray(accountId, orgId, projectId, arrayElement, templateCacheMap, depth));
      } else {
        arrayList.add(mergeTemplateInputsInObject(accountId, orgId, projectId, arrayElement, templateCacheMap, depth));
      }
    }
    return arrayList;
  }

  /**
   * This method Provides all the information from mergeTemplateInputsInObject method along with template references.
   */
  public MergeTemplateInputsInObject mergeTemplateInputsInObjectAlongWithOpaPolicy(String accountId, String orgId,
      String projectId, YamlNode yamlNode, Map<String, TemplateEntity> templateCacheMap, int depth) {
    Map<String, Object> resMap = new LinkedHashMap<>();
    Map<String, Object> resMapWithTemplateRef = new LinkedHashMap<>();
    for (YamlField childYamlField : yamlNode.fields()) {
      String fieldName = childYamlField.getName();
      JsonNode value = childYamlField.getNode().getCurrJsonNode();
      boolean isTemplatePresent = isTemplatePresent(fieldName, value);
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
            accountId, orgId, projectId, childYamlField.getNode(), templateCacheMap, depth);
        resMap.put(fieldName, arrayLists.getArrayList());
        resMapWithTemplateRef.put(fieldName, arrayLists.getArrayListWithTemplateRef());
      } else {
        // If it was template key in yaml, we have replace it with the fields in template.spec in template yaml.
        // Hence, we directly put all the keys returned in map, after iterating over them.
        if (isTemplatePresent) {
          depth++;
          if (depth >= MAX_DEPTH) {
            throw new InvalidRequestException("Exponentially growing template nesting. Aborting");
          }
          MergeTemplateInputsInObject temp = mergeTemplateInputsInObjectAlongWithOpaPolicy(accountId, orgId, projectId,
              new YamlNode(fieldName, value, childYamlField.getNode().getParentNode()), templateCacheMap, depth);
          resMap.putAll(temp.getResMap());
          resMapWithTemplateRef.putAll(temp.getResMapWithOpaResponse());
          depth--;
        } else {
          MergeTemplateInputsInObject temp = mergeTemplateInputsInObjectAlongWithOpaPolicy(
              accountId, orgId, projectId, childYamlField.getNode(), templateCacheMap, depth);
          resMap.put(fieldName, temp.getResMap());
          resMapWithTemplateRef.put(fieldName, temp.getResMapWithOpaResponse());
        }
      }
    }
    return MergeTemplateInputsInObject.builder().resMap(resMap).resMapWithOpaResponse(resMapWithTemplateRef).build();
  }

  private ArrayListForMergedTemplateRef mergeTemplateInputsInArrayWithOpaPolicy(String accountId, String orgId,
      String projectId, YamlNode yamlNode, Map<String, TemplateEntity> templateCacheMap, int depth) {
    List<Object> arrayList = new ArrayList<>();
    List<Object> arrayListWithTemplateRef = new ArrayList<>();
    for (YamlNode arrayElement : yamlNode.asArray()) {
      if (yamlNode.getCurrJsonNode().isValueNode()) {
        arrayList.add(arrayElement);
        arrayListWithTemplateRef.add(arrayElement);
      } else if (arrayElement.isArray()) {
        ArrayListForMergedTemplateRef arrayListForMergedTemplateRef =
            mergeTemplateInputsInArrayWithOpaPolicy(accountId, orgId, projectId, arrayElement, templateCacheMap, depth);
        arrayList.add(arrayListForMergedTemplateRef.getArrayList());
        arrayListWithTemplateRef.add(arrayListForMergedTemplateRef.getArrayListWithTemplateRef());
      } else {
        MergeTemplateInputsInObject temp = mergeTemplateInputsInObjectAlongWithOpaPolicy(
            accountId, orgId, projectId, arrayElement, templateCacheMap, depth);
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
    JsonNode templateVariablesFromPipeline = template.get(TEMPLATE_VARIABLES);

    TemplateEntity templateEntity = getLinkedTemplateEntity(accountId, orgId, projectId, template, templateCacheMap);
    String templateYaml = templateEntity.getYaml();

    JsonNode templateSpec;
    List<NGVariable> variablesFromTemplate;
    try {
      NGTemplateConfig templateConfig = YamlPipelineUtils.read(templateYaml, NGTemplateConfig.class);
      templateSpec = templateConfig.getTemplateInfoConfig().getSpec();
      variablesFromTemplate = templateConfig.getTemplateInfoConfig().getVariables();
    } catch (IOException e) {
      log.error("Could not read template yaml", e);
      throw new NGTemplateException("Could not read template yaml: " + e.getMessage());
    }

    return mergeTemplateInputsToTemplateSpecInTemplateYaml(
        templateInputs, templateVariablesFromPipeline, templateSpec, variablesFromTemplate);
  }

  /**
   * This method merges template inputs provided in pipeline yaml to template spec in template yaml.
   *
   * @param templateInputs        - template runtime info provided in pipeline yaml
   * @param templateVariables     - template variable info provided in pipeline yaml
   * @param templateSpec          - template spec present in template yaml
   * @param variablesFromTemplate
   * @return jsonNode of merged yaml
   */
  private JsonNode mergeTemplateInputsToTemplateSpecInTemplateYaml(JsonNode templateInputs, JsonNode templateVariables,
      JsonNode templateSpec, List<NGVariable> variablesFromTemplate) {
    Map<String, JsonNode> dummyTemplateSpecMap = new LinkedHashMap<>();
    dummyTemplateSpecMap.put(DUMMY_NODE, templateSpec);
    String dummyTemplateSpecYaml = YamlPipelineUtils.writeYamlString(dummyTemplateSpecMap);

    String mergedYaml = dummyTemplateSpecYaml;
    String dummyTemplateInputsYaml = "";
    if (templateInputs != null) {
      Map<String, JsonNode> dummyTemplateInputsMap = new LinkedHashMap<>();
      dummyTemplateInputsMap.put(DUMMY_NODE, templateInputs);
      dummyTemplateInputsYaml = YamlPipelineUtils.writeYamlString(dummyTemplateInputsMap);

      mergedYaml = mergeInputSetFormatYamlToOriginYaml(dummyTemplateSpecYaml, dummyTemplateInputsYaml);
    }

    String mergedYamlWithResolvedVariables = applyTemplateVariablesInYaml(templateVariables, variablesFromTemplate, mergedYaml);

    try {
      String finalMergedYaml = removeOmittedRuntimeInputsFromMergedYaml(mergedYamlWithResolvedVariables, dummyTemplateInputsYaml);
      return YamlUtils.readTree(finalMergedYaml).getNode().getCurrJsonNode().get(DUMMY_NODE);
    } catch (IOException e) {
      log.error("Could not convert merged yaml to JsonNode. Yaml:\n" + mergedYaml, e);
      throw new NGTemplateException("Could not convert merged yaml to JsonNode: " + e.getMessage());
    }
  }

  private String applyTemplateVariablesInYaml(
      JsonNode templateVariablesFromPipeline, List<NGVariable> templateVariablesFromTemplate, String mergedYaml) {
    if (templateVariablesFromPipeline == null && templateVariablesFromTemplate == null) {
      return mergedYaml;
    }
    Map<String, String> variableValues = new HashMap<>();
    if (isNotEmpty(templateVariablesFromTemplate)) {
      Map<String, String> templateVariablesMap =
          NGVariablesUtils.getStringMapVariables(templateVariablesFromTemplate, 0L);
      variableValues.putAll(templateVariablesMap);
    }
    if (templateVariablesFromPipeline != null && templateVariablesFromPipeline.getNodeType() == JsonNodeType.ARRAY) {
      ArrayNode templateVariablesArray = (ArrayNode) templateVariablesFromPipeline;
      for (JsonNode templateVariable : templateVariablesArray) {
        variableValues.put(templateVariable.get(YAMLFieldNameConstants.NAME).asText(),
            templateVariable.get(YAMLFieldNameConstants.VALUE).asText());
      }
    }
    TemplateVariableEvaluator variableEvaluator = new TemplateVariableEvaluator(variableValues);
    String result =
        variableEvaluator.renderExpression(mergedYaml, ExpressionMode.RETURN_ORIGINAL_EXPRESSION_IF_UNRESOLVED);
    return isEmpty(result) ? mergedYaml : result;
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
    String errorYaml = YamlPipelineUtils.writeYamlString(errorYamlMap);
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
      if (isTemplatePresent(fieldName, value)) {
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
    TemplateEntity templateEntity =
        getLinkedTemplateEntity(accountId, orgId, projectId, linkedTemplate, templateCacheMap);
    JsonNode linkedTemplateInputs = linkedTemplate.get(TEMPLATE_INPUTS);
    if (linkedTemplateInputs == null) {
      return linkedTemplate;
    }

    String templateYaml = templateEntity.getYaml();
    String templateSpecInputSetFormatYaml = createTemplateInputsFromTemplate(templateYaml, accountId);

    try {
      Map<String, JsonNode> dummyLinkedTemplateInputsMap = new LinkedHashMap<>();
      dummyLinkedTemplateInputsMap.put(DUMMY_NODE, linkedTemplateInputs);
      String dummyLinkedTemplateInputsYaml = YamlPipelineUtils.writeYamlString(dummyLinkedTemplateInputsMap);

      Map<String, TemplateInputsErrorDTO> uuidToErrorMessageMap = new LinkedHashMap<>();
      String invalidLinkedTemplateInputsYaml;
      if (isNotEmpty(templateSpecInputSetFormatYaml)) {
        JsonNode templateSpecInputSetFormatNode =
            YamlUtils.readTree(templateSpecInputSetFormatYaml).getNode().getCurrJsonNode();
        Map<String, JsonNode> dummyTemplateSpecMap = new LinkedHashMap<>();
        dummyTemplateSpecMap.put(DUMMY_NODE, templateSpecInputSetFormatNode);
        invalidLinkedTemplateInputsYaml =
            getInvalidInputValuesYaml(YamlPipelineUtils.writeYamlString(dummyTemplateSpecMap),
                dummyLinkedTemplateInputsYaml, uuidToErrorMessageMap, identifier);
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
      return YamlUtils.readTree(YamlPipelineUtils.writeYamlString(originalTemplateMap)).getNode().getCurrJsonNode();
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

  public String applyVariablesToCopiedTemplate(List<NGVariable> variables, String templateYaml){
    return applyTemplateVariablesInYaml(null, variables, templateYaml);
  }
}
