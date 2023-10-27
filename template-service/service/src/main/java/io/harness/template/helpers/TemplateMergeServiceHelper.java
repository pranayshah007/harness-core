/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.template.helpers;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.logging.AutoLogContext.OverrideBehavior.OVERRIDE_NESTS;
import static io.harness.pms.yaml.validation.RuntimeInputValuesValidator.validateStaticValues;
import static io.harness.template.resources.beans.NGTemplateConstants.DUMMY_NODE;
import static io.harness.template.resources.beans.NGTemplateConstants.GIT_BRANCH;
import static io.harness.template.resources.beans.NGTemplateConstants.SPEC;
import static io.harness.template.resources.beans.NGTemplateConstants.STABLE_VERSION;
import static io.harness.template.resources.beans.NGTemplateConstants.TEMPLATE;
import static io.harness.template.resources.beans.NGTemplateConstants.TEMPLATE_INPUTS;
import static io.harness.template.resources.beans.NGTemplateConstants.TEMPLATE_REF;
import static io.harness.template.resources.beans.NGTemplateConstants.TEMPLATE_VERSION_LABEL;
import static io.harness.template.utils.TemplateUtils.validateAndGetYamlNode;

import io.harness.EntityType;
import io.harness.NgAutoLogContextForMethod;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.IdentifierRef;
import io.harness.beans.Scope;
import io.harness.common.EntityReferenceHelper;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.ngexception.NGTemplateException;
import io.harness.exception.ngexception.beans.templateservice.TemplateInputsErrorDTO;
import io.harness.exception.ngexception.beans.templateservice.TemplateInputsErrorMetadataDTO;
import io.harness.gitaware.dto.FetchRemoteEntityRequest;
import io.harness.gitaware.dto.GetFileGitContextRequestParams;
import io.harness.gitaware.helper.GitAwareEntityHelper;
import io.harness.gitsync.beans.StoreType;
import io.harness.gitx.GitXTransientBranchGuard;
import io.harness.logging.AutoLogContext;
import io.harness.ng.core.template.TemplateEntityType;
import io.harness.pms.merger.YamlConfig;
import io.harness.pms.merger.fqn.FQN;
import io.harness.pms.merger.helpers.MergeHelper;
import io.harness.pms.merger.helpers.RuntimeInputFormHelper;
import io.harness.pms.merger.helpers.YamlSubMapExtractor;
import io.harness.pms.yaml.HarnessYamlVersion;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlNode;
import io.harness.pms.yaml.YamlUtils;
import io.harness.serializer.JsonUtils;
import io.harness.template.entity.TemplateEntity;
import io.harness.template.entity.TemplateEntityGetResponse;
import io.harness.template.mappers.NGTemplateDtoMapper;
import io.harness.template.resources.beans.GetTemplateEntityRequest;
import io.harness.template.resources.beans.TemplateUniqueIdentifier;
import io.harness.template.resources.beans.yaml.NGTemplateConfig;
import io.harness.template.services.NGTemplateServiceHelper;
import io.harness.template.services.TemplateGitXService;
import io.harness.template.utils.TemplateUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.annotations.VisibleForTesting;
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
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import javax.ws.rs.InternalServerErrorException;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true,
    components = {HarnessModuleComponent.CDS_TEMPLATE_LIBRARY, HarnessModuleComponent.CDS_PIPELINE,
        HarnessModuleComponent.CDS_GITX})
@OwnedBy(HarnessTeam.CDC)
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j

/**
 * Class containing common methods w.r.t NGTemplateService and TemplateMergeService
 */
public class TemplateMergeServiceHelper {
  private static final int MAX_DEPTH = 10;
  private NGTemplateServiceHelper templateServiceHelper;
  private GitAwareEntityHelper gitAwareEntityHelper;
  private TemplateGitXService templateGitXService;
  private TemplatePreProcessorHelper templatePreprocessorHelper;

  // Gets the Template Entity linked to a YAML
  public TemplateEntityGetResponse getLinkedTemplateEntity(String accountId, String orgId, String projectId,
      JsonNode yaml, Map<String, TemplateEntity> templateCacheMap, boolean loadFromCache, String yamlVersion) {
    TemplateUniqueIdentifier templateUniqueIdentifier;
    if (HarnessYamlVersion.isV1(yamlVersion)) {
      templateUniqueIdentifier = parseYamlAndGetTemplateIdentifierAndVersionV1(yaml.get(YAMLFieldNameConstants.SPEC));
    } else {
      templateUniqueIdentifier = parseYamlAndGetTemplateIdentifierAndVersion(yaml);
    }
    long start = System.currentTimeMillis();
    try (AutoLogContext ignore1 =
             new NgAutoLogContextForMethod(projectId, orgId, accountId, "getLinkedTemplateEntity", OVERRIDE_NESTS)) {
      log.info("[TemplateService] Fetching Template {} from project {}, org {}, account {}",
          templateUniqueIdentifier.getTemplateIdentifier(), projectId, orgId, accountId);
      TemplateEntity template =
          getLinkedTemplateEntityHelper(accountId, orgId, projectId, templateUniqueIdentifier.getTemplateIdentifier(),
              templateUniqueIdentifier.getVersionLabel(), templateCacheMap, templateUniqueIdentifier.getVersionMaker(),
              loadFromCache, templateUniqueIdentifier.getGitBranch());
      return new TemplateEntityGetResponse(template, NGTemplateDtoMapper.getEntityGitDetails(template));
    } finally {
      log.debug("[TemplateService] Finished fetching Template {} from project {}, org {}, account {} took {}ms ",
          templateUniqueIdentifier.getTemplateIdentifier(), projectId, orgId, accountId,
          System.currentTimeMillis() - start);
    }
  }

  // Gets the Template Entity linked to a YAML
  public TemplateEntity getLinkedTemplateEntity(String accountId, String orgId, String projectId, String identifier,
      String versionLabel, Map<String, TemplateEntity> templateCacheMap, String branch) {
    String versionMarker = STABLE_VERSION;
    if (!EmptyPredicate.isEmpty(versionLabel)) {
      versionMarker = versionLabel;
    }

    return getLinkedTemplateEntityHelper(
        accountId, orgId, projectId, identifier, versionLabel, templateCacheMap, versionMarker, false, branch);
  }

  public TemplateEntity getLinkedTemplateEntityHelper(String accountId, String orgId, String projectId,
      String identifier, String versionLabel, Map<String, TemplateEntity> templateCacheMap, String versionMarker,
      boolean loadFromCache, String branch) {
    IdentifierRef templateIdentifierRef =
        TemplateUtils.getIdentifierRef(accountId, orgId, projectId, identifier, branch);
    String templateUniqueIdentifier = generateUniqueTemplateIdentifier(templateIdentifierRef.getAccountIdentifier(),
        templateIdentifierRef.getOrgIdentifier(), templateIdentifierRef.getProjectIdentifier(),
        templateIdentifierRef.getIdentifier(), versionMarker, branch);
    if (templateCacheMap.containsKey(templateUniqueIdentifier)) {
      return templateCacheMap.get(templateUniqueIdentifier);
    }

    Optional<TemplateEntity> templateEntity;
    try (GitXTransientBranchGuard ignore = new GitXTransientBranchGuard(branch)) {
      templateEntity =
          templateServiceHelper.getTemplateOrThrowExceptionIfInvalid(templateIdentifierRef.getAccountIdentifier(),
              templateIdentifierRef.getOrgIdentifier(), templateIdentifierRef.getProjectIdentifier(),
              templateIdentifierRef.getIdentifier(), versionLabel, false, loadFromCache);
    }
    if (!templateEntity.isPresent()) {
      throwTemplateNotFoundException(templateIdentifierRef.getIdentifier(), versionLabel);
    }
    TemplateEntity template = templateEntity.get();
    templateCacheMap.put(templateUniqueIdentifier, template);
    return template;
  }

  private void throwTemplateNotFoundException(String templateIdentifier, String versionLabel) {
    throw new NGTemplateException(String.format(
        "The template identifier [%s] and version label [%s] does not exist. Please check identifier and version is correct and exists",
        templateIdentifier, versionLabel));
  }

  // Checks if v0 or v1 template is present
  public boolean isTemplatePresent(String fieldName, JsonNode jsonNode) {
    JsonNode templateValue = jsonNode.get(fieldName);
    return isV0TemplatePresent(fieldName, templateValue)
        || (isV1TemplatePresent(jsonNode) && YAMLFieldNameConstants.SPEC.equals(fieldName));
  }

  // Checks if the current Json node is a Template node with fieldName as TEMPLATE and Non-null Value
  public boolean isV0TemplatePresent(String fieldName, JsonNode templateValue) {
    return TEMPLATE.equals(fieldName) && templateValue.isObject() && templateValue.get(TEMPLATE_REF) != null;
  }

  // checks if current json node is template with type set as template or spec and ref fields (pipeline template)
  public boolean isV1TemplatePresent(JsonNode jsonNode) {
    return jsonNode.has(YAMLFieldNameConstants.TYPE)
        && YAMLFieldNameConstants.TEMPLATE.equals(jsonNode.get(YAMLFieldNameConstants.TYPE).asText());
  }
  // Generates a unique Template Identifier
  private String generateUniqueTemplateIdentifier(
      String accountId, String orgId, String projectId, String templateIdentifier, String versionLabel, String branch) {
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
    if (isNotEmpty(branch)) {
      fqnList.add(branch);
    }

    return EntityReferenceHelper.createFQN(fqnList);
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
  public String createTemplateInputsFromTemplate(String yaml) {
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
      String templateInputsYamlWithSpec = RuntimeInputFormHelper.createRuntimeInputFormWithDefaultValues(templateSpec);
      if (isEmpty(templateInputsYamlWithSpec)) {
        return templateInputsYamlWithSpec;
      }
      JsonNode templateInputsYaml =
          YamlUtils.readTree(templateInputsYamlWithSpec).getNode().getCurrJsonNode().get(SPEC);
      return YamlUtils.writeYamlString(templateInputsYaml);
    } catch (IOException e) {
      log.error("Error occurred while creating template inputs " + e);
      throw new NGTemplateException("Error occurred while creating template inputs ", e);
    }
  }

  public Map<String, Object> mergeTemplateInputsInObject(String accountId, String orgId, String projectId,
      YamlNode yamlNode, Map<String, TemplateEntity> templateCacheMap, int depth, boolean loadFromCache,
      boolean appendInputSetValidator, String yamlVersion) {
    return mergeTemplateInputsInObjectWithVersion(accountId, orgId, projectId, yamlNode, templateCacheMap, depth,
        loadFromCache, appendInputSetValidator, yamlVersion, null, null)
        .getResMap();
  }

  /**
   * This method iterates recursively on pipeline yaml. Whenever we find a key with "template" we call
   * replaceTemplateOccurrenceWithTemplateSpecYaml() to get the actual template.spec in template yaml.
   */
  public MergeTemplateInputsInObject mergeTemplateInputsInObjectWithVersion(String accountId, String orgId,
      String projectId, YamlNode currentYamlNode, Map<String, TemplateEntity> templateCacheMap, int depth,
      boolean loadFromCache, boolean appendInputSetValidator, String currentYamlVersion, Set<String> idsValuesSet,
      Map<String, Integer> idsSuffixMap) {
    Map<String, Object> resMap = new LinkedHashMap<>();
    TemplateEntity templateEntity = null;
    for (YamlField childYamlField : currentYamlNode.fields()) {
      String fieldName = childYamlField.getName();
      JsonNode value = childYamlField.getNode().getCurrJsonNode();
      boolean isTemplatePresent = isTemplatePresent(fieldName, currentYamlNode.getCurrJsonNode());
      if (isTemplatePresent) {
        Pair<TemplateEntity, JsonNode> entry = replaceTemplateOccurrenceWithTemplateSpecYaml(accountId, orgId,
            projectId, getTemplateJsonNode(currentYamlVersion, value, currentYamlNode), templateCacheMap, loadFromCache,
            appendInputSetValidator, currentYamlVersion);
        value = entry.getValue();
        templateEntity = entry.getKey();
        templatePreprocessorHelper.collectIdsFromTemplateYaml(templateEntity, idsValuesSet);
      }
      if (value.isValueNode() || YamlUtils.checkIfNodeIsArrayWithPrimitiveTypes(value)) {
        resMap.put(fieldName, value);
      } else if (value.isArray()) {
        resMap.put(fieldName,
            mergeTemplateInputsInArray(accountId, orgId, projectId, childYamlField.getNode(), templateCacheMap, depth,
                loadFromCache, appendInputSetValidator, currentYamlVersion, idsValuesSet, idsSuffixMap));
      } else {
        // If it was template key in yaml, we have replace it with the fields in template.spec in template yaml.
        // Hence, we directly put all the keys returned in map, after iterating over them.
        if (isTemplatePresent) {
          depth++;
          if (depth >= MAX_DEPTH) {
            throw new InvalidRequestException("Exponentially growing template nesting. Aborting");
          }
          Map<String, Object> temp = mergeTemplateInputsInObjectWithVersion(accountId, orgId, projectId,
              new YamlNode(fieldName, value, childYamlField.getNode().getParentNode()), templateCacheMap, depth,
              loadFromCache, appendInputSetValidator, templateEntity.getHarnessVersion(), idsValuesSet, idsSuffixMap)
                                         .getResMap();
          resMap.putAll(temp);
          depth--;
        } else {
          resMap.put(fieldName,
              mergeTemplateInputsInObjectWithVersion(accountId, orgId, projectId, childYamlField.getNode(),
                  templateCacheMap, depth, loadFromCache, appendInputSetValidator, currentYamlVersion, idsValuesSet,
                  idsSuffixMap)
                  .getResMap());
        }
      }
    }
    return getMergeTemplateInputsInObject(currentYamlVersion, templateEntity, resMap, null, idsValuesSet, idsSuffixMap);
  }

  private MergeTemplateInputsInObject getMergeTemplateInputsInObject(String currentYamlVersion,
      TemplateEntity templateEntity, Map<String, Object> resMap, Map<String, Object> resMapWithTemplateRef,
      Set<String> idsValuesSet, Map<String, Integer> idsSuffixMap) {
    String processedYamlVersion = currentYamlVersion;
    // If current yaml version is v0 then we can directly return the resMap
    if (templateEntity == null || !HarnessYamlVersion.isV1(currentYamlVersion)) {
      return MergeTemplateInputsInObject.builder()
          .resMap(resMap)
          .processedYamlVersion(processedYamlVersion)
          .resMapWithOpaResponse(resMapWithTemplateRef)
          .build();
    }
    // Preprocess the yaml to add ids in the step and stage nodes
    resMap = templatePreprocessorHelper.preProcessResMap(templateEntity, resMap, idsValuesSet, idsSuffixMap);
    // if current yaml version is v1 and template is of type pipeline, we need to merge yamls differently for v0 and v1
    // templates
    if (templateEntity.getTemplateEntityType() == TemplateEntityType.PIPELINE_TEMPLATE) {
      if (HarnessYamlVersion.isV1(templateEntity.getHarnessVersion())) {
        return MergeTemplateInputsInObject.builder()
            .resMap(getMergedPipelineYamlV1(resMap))
            .resMapWithOpaResponse(resMapWithTemplateRef)
            .processedYamlVersion(HarnessYamlVersion.V1)
            .build();
      } else {
        return MergeTemplateInputsInObject.builder()
            .resMap(getMergedPipelineYaml(resMap, templateEntity.getTemplateEntityType()))
            .resMapWithOpaResponse(resMapWithTemplateRef)
            .processedYamlVersion(HarnessYamlVersion.V0)
            .build();
      }
    } else {
      // if template is of any other type then in case of v1, resMap can be returned directly but for v0 templates,
      // template entity root name needs to be appended
      if (HarnessYamlVersion.isV1(templateEntity.getHarnessVersion())) {
        return MergeTemplateInputsInObject.builder()
            .resMap(resMap)
            .resMapWithOpaResponse(resMapWithTemplateRef)
            .processedYamlVersion(processedYamlVersion)
            .build();
      } else {
        return MergeTemplateInputsInObject.builder()
            .resMap(Map.of(templateEntity.getTemplateEntityType().getRootYamlName(), resMap))
            .resMapWithOpaResponse(resMapWithTemplateRef)
            .processedYamlVersion(HarnessYamlVersion.V0)
            .build();
      }
    }
  }

  private Map<String, Object> getMergedPipelineYaml(Map<String, Object> resMap, TemplateEntityType type) {
    resMap.remove(YAMLFieldNameConstants.VERSION);
    resMap.remove(YAMLFieldNameConstants.TYPE);
    resMap.remove(YAMLFieldNameConstants.KIND);
    return Map.of(type.getRootYamlName(), resMap);
  }

  private Map<String, Object> getMergedPipelineYamlV1(Map<String, Object> resMap) {
    resMap.remove(YAMLFieldNameConstants.TYPE);
    for (Map.Entry<String, Object> entry : resMap.entrySet()) {
      if (entry.getKey().equals(YAMLFieldNameConstants.STAGES)) {
        resMap.put(YAMLFieldNameConstants.SPEC, entry);
        resMap.remove(YAMLFieldNameConstants.STAGES);
      }
    }
    return resMap;
  }

  public Map<String, TemplateEntity> getAllTemplatesFromYaml(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, YamlNode yamlNode, boolean loadFromCache) {
    Map<String, YamlNode> templatesToGet = new HashMap<>();
    Map<String, TemplateEntity> templateCacheMap = new HashMap<>();
    Queue<YamlField> yamlNodeQueue = new LinkedList<>(yamlNode.fields());
    while (!yamlNodeQueue.isEmpty()) {
      int size = yamlNodeQueue.size();

      for (int i = 0; i < size; i++) {
        YamlField childYamlField = yamlNodeQueue.remove();
        String fieldName = childYamlField.getName();
        JsonNode value = childYamlField.getNode().getCurrJsonNode();
        boolean isTemplatePresent = isV0TemplatePresent(fieldName, value);
        if (isTemplatePresent) {
          String templateUniqueIdentifier =
              getTemplateUniqueIdentifier(accountIdentifier, orgIdentifier, projectIdentifier, value);
          if (!templateCacheMap.containsKey(templateUniqueIdentifier)) {
            templatesToGet.put(
                templateUniqueIdentifier, new YamlNode(fieldName, value, childYamlField.getNode().getParentNode()));
          }
        }
        if (value.isObject()) {
          yamlNodeQueue.addAll(childYamlField.getNode().fields());
        } else if (value.isArray()) {
          getAllTemplatesFromYamlInArray(childYamlField.getNode(), yamlNodeQueue);
        }
      }

      if (!templatesToGet.isEmpty()) {
        templateCacheMap.putAll(processAndGetTemplates(
            accountIdentifier, orgIdentifier, projectIdentifier, templatesToGet, yamlNodeQueue, loadFromCache));

        templatesToGet.clear();
      }
    }
    return templateCacheMap;
  }

  private Map<String, TemplateEntity> processAndGetTemplates(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, Map<String, YamlNode> templatesToGet, Queue<YamlField> yamlNodeQueue,
      boolean loadFromCache) {
    Map<String, GetTemplateEntityRequest> getBatchRequest = prepareBatchGetTemplatesRequest(
        accountIdentifier, orgIdentifier, projectIdentifier, templatesToGet, loadFromCache);

    return getBatchTemplatesAndProcessTemplates(accountIdentifier, getBatchRequest, yamlNodeQueue);
  }

  private void validateAndAddToQueue(Map<String, TemplateEntity> remoteTemplates, Queue<YamlField> yamlNodeQueue) {
    remoteTemplates.forEach((templateIdentifier, templateEntity) -> {
      YamlNode yamlNode = validateAndGetYamlNode(remoteTemplates.get(templateIdentifier).getYaml(), templateIdentifier);
      yamlNodeQueue.addAll(yamlNode.fields());
    });
  }

  @VisibleForTesting
  String getTemplateUniqueIdentifier(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, JsonNode value) {
    TemplateUniqueIdentifier templateUniqueIdentification = parseYamlAndGetTemplateIdentifierAndVersion(value);

    IdentifierRef templateIdentifierRef =
        TemplateUtils.getIdentifierRef(accountIdentifier, orgIdentifier, projectIdentifier,
            templateUniqueIdentification.getTemplateIdentifier(), templateUniqueIdentification.getGitBranch());
    String templateUniqueIdentifier = generateUniqueTemplateIdentifier(templateIdentifierRef.getAccountIdentifier(),
        templateIdentifierRef.getOrgIdentifier(), templateIdentifierRef.getProjectIdentifier(),
        templateIdentifierRef.getIdentifier(), templateUniqueIdentification.getVersionMaker(),
        templateIdentifierRef.getBranch());
    log.info("Unique template identifier: {}", templateUniqueIdentifier);
    return templateUniqueIdentifier;
  }

  @VisibleForTesting
  Map<String, GetTemplateEntityRequest> prepareBatchGetTemplatesRequest(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, Map<String, YamlNode> templatesToGet, boolean loadFromCache) {
    Map<String, GetTemplateEntityRequest> getBatchRequest = new HashMap<>();
    for (Map.Entry<String, YamlNode> entry : templatesToGet.entrySet()) {
      JsonNode yaml = entry.getValue().getCurrJsonNode();
      TemplateUniqueIdentifier templateUniqueIdentifier = parseYamlAndGetTemplateIdentifierAndVersion(yaml);
      IdentifierRef templateIdentifierRef = TemplateUtils.getIdentifierRef(accountIdentifier, orgIdentifier,
          projectIdentifier, templateUniqueIdentifier.getTemplateIdentifier(), templateUniqueIdentifier.getGitBranch());

      Scope templateScope = Scope.builder()
                                .projectIdentifier(templateIdentifierRef.getProjectIdentifier())
                                .accountIdentifier(templateIdentifierRef.getAccountIdentifier())
                                .orgIdentifier(templateIdentifierRef.getOrgIdentifier())
                                .build();
      GetTemplateEntityRequest request = GetTemplateEntityRequest.builder()
                                             .scope(templateScope)
                                             .templateIdentifier(templateIdentifierRef.getIdentifier())
                                             .version(templateUniqueIdentifier.getVersionLabel())
                                             .loadFromCache(loadFromCache)
                                             .branch(templateIdentifierRef.getBranch())
                                             .build();
      getBatchRequest.put(entry.getKey(), request);
    }
    return getBatchRequest;
  }

  @VisibleForTesting
  Map<String, TemplateEntity> performBatchGetTemplateAndValidate(
      String accountIdentifier, Map<String, FetchRemoteEntityRequest> getBatchRemoteTemplatesRequestList) {
    Map<String, TemplateEntity> templateCacheMap = new HashMap<>();
    Map<String, TemplateEntity> getBatchTemplateResponseMap;
    try {
      getBatchTemplateResponseMap =
          templateServiceHelper.getBatchRemoteTemplates(accountIdentifier, getBatchRemoteTemplatesRequestList);
    } catch (NGTemplateException e) {
      throw new NGTemplateException(e.getMessage(), e);
    } catch (Exception e) {
      log.error("Error while getting batch templates ", e);
      throw new InvalidRequestException(String.format("Error while getting templates: %s", e.getMessage()));
    }

    for (String requestIdentifier : getBatchRemoteTemplatesRequestList.keySet()) {
      if (getBatchTemplateResponseMap.containsKey(requestIdentifier)) {
        templateCacheMap.put(requestIdentifier, getBatchTemplateResponseMap.get(requestIdentifier));
      } else {
        log.error(String.format("Template key %s not found in the getBatchTemplate", requestIdentifier));
        throw new InternalServerErrorException("Error while retrieving templates");
      }
    }
    return templateCacheMap;
  }

  @VisibleForTesting
  Map<String, TemplateEntity> getBatchTemplatesAndProcessTemplates(
      String accountIdentifier, Map<String, GetTemplateEntityRequest> getBatchRequest, Queue<YamlField> yamlNodeQueue) {
    Map<String, FetchRemoteEntityRequest> remoteTemplatesRequestList = new HashMap<>();
    Map<String, TemplateEntity> templateCacheMap = new HashMap<>();
    Map<String, TemplateEntity> inlineTemplateResponseList = new HashMap<>();

    for (Map.Entry<String, GetTemplateEntityRequest> getTemplateEntityRequestEntry : getBatchRequest.entrySet()) {
      Scope scope = getTemplateEntityRequestEntry.getValue().getScope();
      boolean loadFromCache = getTemplateEntityRequestEntry.getValue().isLoadFromCache();
      String branch = getTemplateEntityRequestEntry.getValue().getBranch();
      Optional<TemplateEntity> templateEntity = templateServiceHelper.getMetadataOrThrowExceptionIfInvalid(
          scope.getAccountIdentifier(), scope.getOrgIdentifier(), scope.getProjectIdentifier(),
          getTemplateEntityRequestEntry.getValue().getTemplateIdentifier(),
          getTemplateEntityRequestEntry.getValue().getVersion(), false);
      if (templateEntity.isEmpty()) {
        throw new NGTemplateException(String.format("Template with template ID %s and version %s not found.",
            getTemplateEntityRequestEntry.getValue().getTemplateIdentifier(),
            getTemplateEntityRequestEntry.getValue().getVersion()));
      }
      TemplateEntity savedEntity = templateEntity.get();

      if (StoreType.REMOTE == savedEntity.getStoreType()) {
        remoteTemplatesRequestList.put(getTemplateEntityRequestEntry.getKey(),
            buildFetchRemoteEntityRequest(scope, savedEntity, loadFromCache, branch));
      } else {
        templateCacheMap.put(getTemplateEntityRequestEntry.getKey(), templateEntity.get());
        inlineTemplateResponseList.put(getTemplateEntityRequestEntry.getKey(), savedEntity);
      }
    }
    //    process inline templates
    if (!inlineTemplateResponseList.isEmpty()) {
      validateAndAddToQueue(inlineTemplateResponseList, yamlNodeQueue);
    }

    //    process remote templates
    if (!remoteTemplatesRequestList.isEmpty()) {
      Map<String, TemplateEntity> remoteTemplateResponseList =
          new HashMap<>(performBatchGetTemplateAndValidate(accountIdentifier, remoteTemplatesRequestList));
      templateCacheMap.putAll(remoteTemplateResponseList);
      validateAndAddToQueue(remoteTemplateResponseList, yamlNodeQueue);
    }
    return templateCacheMap;
  }

  private FetchRemoteEntityRequest buildFetchRemoteEntityRequest(
      Scope scope, TemplateEntity savedEntity, boolean loadFromCache, String branchName) {
    if (isEmpty(branchName)) {
      branchName = gitAwareEntityHelper.getWorkingBranch(savedEntity.getRepo());
    }

    GetFileGitContextRequestParams getFileGitContextRequestParams =
        buildGitContextRequestParams(savedEntity, branchName, loadFromCache);

    return FetchRemoteEntityRequest.builder()
        .entity(savedEntity)
        .getFileGitContextRequestParams(getFileGitContextRequestParams)
        .scope(scope)
        .contextMap(Collections.emptyMap())
        .build();
  }

  private List<Object> getAllTemplatesFromYamlInArray(YamlNode yamlNode, Queue<YamlField> yamlNodeQueue) {
    List<Object> arrayList = new ArrayList<>();
    for (YamlNode arrayElement : yamlNode.asArray()) {
      if (arrayElement.isArray()) {
        arrayList.add(getAllTemplatesFromYamlInArray(arrayElement, yamlNodeQueue));
      } else {
        yamlNodeQueue.addAll(arrayElement.fields());
      }
    }
    return arrayList;
  }

  public GetFileGitContextRequestParams buildGitContextRequestParams(
      TemplateEntity savedEntity, String branchName, boolean loadFromCache) {
    return GetFileGitContextRequestParams.builder()
        .branchName(branchName)
        .connectorRef(savedEntity.getConnectorRef())
        .filePath(savedEntity.getFilePath())
        .repoName(savedEntity.getRepo())
        .entityType(EntityType.TEMPLATE)
        .loadFromCache(loadFromCache)
        .getOnlyFileContent(TemplateUtils.isExecutionFlow())
        .build();
  }

  private List<Object> mergeTemplateInputsInArray(String accountId, String orgId, String projectId, YamlNode yamlNode,
      Map<String, TemplateEntity> templateCacheMap, int depth, boolean loadFromCache, boolean appendInputSetValidator,
      String yamlVersion, Set<String> idsValuesSet, Map<String, Integer> idsSuffixMap) {
    List<Object> arrayList = new ArrayList<>();
    for (YamlNode arrayElement : yamlNode.asArray()) {
      if (yamlNode.getCurrJsonNode().isValueNode()) {
        arrayList.add(arrayElement);
      } else if (arrayElement.isArray()) {
        arrayList.add(mergeTemplateInputsInArray(accountId, orgId, projectId, arrayElement, templateCacheMap, depth,
            loadFromCache, appendInputSetValidator, yamlVersion, idsValuesSet, idsSuffixMap));
      } else {
        arrayList.add(mergeTemplateInputsInObjectWithVersion(accountId, orgId, projectId, arrayElement,
            templateCacheMap, depth, loadFromCache, appendInputSetValidator, yamlVersion, idsValuesSet, idsSuffixMap)
                          .getResMap());
      }
    }
    return arrayList;
  }

  /**
   * This method Provides all the information from mergeTemplateInputsInObject method along with template references.
   */
  public MergeTemplateInputsInObject mergeTemplateInputsInObjectAlongWithOpaPolicy(String accountId, String orgId,
      String projectId, YamlNode currentYamlNode, Map<String, TemplateEntity> templateCacheMap, int depth,
      boolean loadFromCache, boolean appendInputSetValidator, String currentYamlVersion, Set<String> idsValuesSet,
      Map<String, Integer> idsSuffixMap) {
    Map<String, Object> resMap = new LinkedHashMap<>();
    Map<String, Object> resMapWithTemplateRef = new LinkedHashMap<>();
    TemplateEntity templateEntity = null;
    for (YamlField childYamlField : currentYamlNode.fields()) {
      String fieldName = childYamlField.getName();
      JsonNode value = childYamlField.getNode().getCurrJsonNode();
      boolean isTemplatePresent = isTemplatePresent(fieldName, currentYamlNode.getCurrJsonNode());
      if (isTemplatePresent) {
        resMapWithTemplateRef.put(fieldName, JsonUtils.jsonNodeToMap(value));
        Pair<TemplateEntity, JsonNode> entry = replaceTemplateOccurrenceWithTemplateSpecYaml(accountId, orgId,
            projectId, getTemplateJsonNode(currentYamlVersion, value, currentYamlNode), templateCacheMap, loadFromCache,
            appendInputSetValidator, currentYamlVersion);
        value = entry.getValue();
        templateEntity = entry.getKey();
        templatePreprocessorHelper.collectIdsFromTemplateYaml(templateEntity, idsValuesSet);
      }
      if (value.isValueNode() || YamlUtils.checkIfNodeIsArrayWithPrimitiveTypes(value)) {
        resMap.put(fieldName, value);
        resMapWithTemplateRef.put(fieldName, value);
      } else if (value.isArray()) {
        ArrayListForMergedTemplateRef arrayLists = mergeTemplateInputsInArrayWithOpaPolicy(accountId, orgId, projectId,
            childYamlField.getNode(), templateCacheMap, depth, loadFromCache, appendInputSetValidator,
            currentYamlVersion, idsValuesSet, idsSuffixMap);
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
              new YamlNode(fieldName, value, childYamlField.getNode().getParentNode()), templateCacheMap, depth,
              loadFromCache, appendInputSetValidator, templateEntity.getHarnessVersion(), idsValuesSet, idsSuffixMap);
          resMap.putAll(temp.getResMap());
          resMapWithTemplateRef.putAll(temp.getResMapWithOpaResponse());
          depth--;
        } else {
          MergeTemplateInputsInObject temp = mergeTemplateInputsInObjectAlongWithOpaPolicy(accountId, orgId, projectId,
              childYamlField.getNode(), templateCacheMap, depth, loadFromCache, appendInputSetValidator,
              currentYamlVersion, idsValuesSet, idsSuffixMap);
          resMap.put(fieldName, temp.getResMap());
          resMapWithTemplateRef.put(fieldName, temp.getResMapWithOpaResponse());
        }
      }
    }

    return getMergeTemplateInputsInObject(
        currentYamlVersion, templateEntity, resMap, resMapWithTemplateRef, idsValuesSet, idsSuffixMap);
  }

  private JsonNode getTemplateJsonNode(
      String currentYamlVersion, JsonNode currentFieldValue, YamlNode currentYamlNode) {
    JsonNode templateJsonNode = currentFieldValue;
    if (HarnessYamlVersion.isV1(currentYamlVersion)) {
      templateJsonNode = currentYamlNode.getCurrJsonNode();
    }
    return templateJsonNode;
  }

  private ArrayListForMergedTemplateRef mergeTemplateInputsInArrayWithOpaPolicy(String accountId, String orgId,
      String projectId, YamlNode yamlNode, Map<String, TemplateEntity> templateCacheMap, int depth,
      boolean loadFromCache, boolean appendInputSetValidator, String yamlVersion, Set<String> idsValuesSet,
      Map<String, Integer> idsSuffixMap) {
    List<Object> arrayList = new ArrayList<>();
    List<Object> arrayListWithTemplateRef = new ArrayList<>();
    for (YamlNode arrayElement : yamlNode.asArray()) {
      if (yamlNode.getCurrJsonNode().isValueNode()) {
        arrayList.add(arrayElement);
        arrayListWithTemplateRef.add(arrayElement);
      } else if (arrayElement.isArray()) {
        ArrayListForMergedTemplateRef arrayListForMergedTemplateRef =
            mergeTemplateInputsInArrayWithOpaPolicy(accountId, orgId, projectId, arrayElement, templateCacheMap, depth,
                loadFromCache, appendInputSetValidator, yamlVersion, idsValuesSet, idsSuffixMap);
        arrayList.add(arrayListForMergedTemplateRef.getArrayList());
        arrayListWithTemplateRef.add(arrayListForMergedTemplateRef.getArrayListWithTemplateRef());
      } else {
        MergeTemplateInputsInObject temp =
            mergeTemplateInputsInObjectAlongWithOpaPolicy(accountId, orgId, projectId, arrayElement, templateCacheMap,
                depth, loadFromCache, appendInputSetValidator, yamlVersion, idsValuesSet, idsSuffixMap);
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
   * This method gets the TemplateEntity from database. Further it gets template yaml and merge template inputs
   * present in pipeline to template.spec in template yaml
   *
   * @param template         - template json node present in pipeline yaml
   * @param templateCacheMap
   * @param loadFromCache
   * @param appendInputSetValidator
   * @return jsonNode of merged yaml
   */
  private Pair<TemplateEntity, JsonNode> replaceTemplateOccurrenceWithTemplateSpecYaml(String accountId, String orgId,
      String projectId, JsonNode template, Map<String, TemplateEntity> templateCacheMap, boolean loadFromCache,
      boolean appendInputSetValidator, String yamlVersion) {
    JsonNode templateInputs = template.get(TEMPLATE_INPUTS);

    TemplateEntityGetResponse templateEntityGetResponse =
        getLinkedTemplateEntity(accountId, orgId, projectId, template, templateCacheMap, loadFromCache, yamlVersion);
    TemplateEntity templateEntity = templateEntityGetResponse.getTemplateEntity();
    JsonNode templateSpec = getSpecFromTemplateEntity(templateEntity);
    return Pair.of(templateEntity,
        mergeTemplateInputsToTemplateSpecInTemplateYaml(templateInputs, templateSpec, appendInputSetValidator));
  }

  /**
   * This method merges template inputs provided in pipeline yaml to template spec in template yaml.
   * @param templateInputs - template runtime info provided in pipeline yaml
   * @param templateSpec - template spec present in template yaml
   * @param appendInputSetValidator
   * @return jsonNode of merged yaml
   */
  private JsonNode mergeTemplateInputsToTemplateSpecInTemplateYaml(
      JsonNode templateInputs, JsonNode templateSpec, boolean appendInputSetValidator) {
    Map<String, JsonNode> dummyTemplateSpecMap = new LinkedHashMap<>();
    dummyTemplateSpecMap.put(DUMMY_NODE, templateSpec);
    String dummyTemplateSpecYaml = YamlUtils.writeYamlString(dummyTemplateSpecMap);

    String mergedYaml = dummyTemplateSpecYaml;
    String dummyTemplateInputsYaml = "";
    if (templateInputs != null) {
      Map<String, JsonNode> dummyTemplateInputsMap = new LinkedHashMap<>();
      dummyTemplateInputsMap.put(DUMMY_NODE, templateInputs);
      dummyTemplateInputsYaml = YamlUtils.writeYamlString(dummyTemplateInputsMap);

      mergedYaml = MergeHelper.mergeRuntimeInputValuesAndCheckForRuntimeInOriginalYaml(
          dummyTemplateSpecYaml, dummyTemplateInputsYaml, appendInputSetValidator, true);
    }

    try {
      return YamlUtils.readTree(mergedYaml).getNode().getCurrJsonNode().get(DUMMY_NODE);
    } catch (IOException e) {
      log.error("Could not convert merged yaml to JsonNode. Yaml:\n" + mergedYaml, e);
      throw new NGTemplateException("Could not convert merged yaml to JsonNode: " + e.getMessage());
    }
  }

  /**
   * This method validates the template inputs in linked templates in yaml
   *
   * @param accountId
   * @param orgId
   * @param projectId
   * @param yamlNode         - YamlNode on which we need to validate template inputs in linked template.
   * @param templateCacheMap
   * @param loadFromCache
   * @return
   */
  public TemplateInputsErrorMetadataDTO validateLinkedTemplateInputsInYaml(String accountId, String orgId,
      String projectId, YamlNode yamlNode, Map<String, TemplateEntity> templateCacheMap, boolean loadFromCache) {
    Map<String, TemplateInputsErrorDTO> templateInputsErrorMap = new LinkedHashMap<>();
    Map<String, Object> errorYamlMap = validateTemplateInputsInObject(
        accountId, orgId, projectId, yamlNode, templateInputsErrorMap, templateCacheMap, loadFromCache);
    if (isEmpty(templateInputsErrorMap)) {
      return null;
    }
    String errorYaml = YamlUtils.writeYamlString(errorYamlMap);
    String errorTemplateYaml = convertUuidErrorMapToFqnErrorMap(errorYaml, templateInputsErrorMap);
    return new TemplateInputsErrorMetadataDTO(errorTemplateYaml, templateInputsErrorMap);
  }

  private Map<String, Object> validateTemplateInputsInObject(String accountId, String orgId, String projectId,
      YamlNode yamlNode, Map<String, TemplateInputsErrorDTO> templateInputsErrorMap,
      Map<String, TemplateEntity> templateCacheMap, boolean loadFromCache) {
    Map<String, Object> resMap = new LinkedHashMap<>();
    for (YamlField childYamlField : yamlNode.fields()) {
      String fieldName = childYamlField.getName();
      JsonNode value = childYamlField.getNode().getCurrJsonNode();
      if (isV0TemplatePresent(fieldName, value)) {
        resMap.put(fieldName,
            validateTemplateInputs(
                accountId, orgId, projectId, value, templateInputsErrorMap, templateCacheMap, loadFromCache));
        continue;
      }
      if (value.isValueNode() || YamlUtils.checkIfNodeIsArrayWithPrimitiveTypes(value)) {
        resMap.put(fieldName, value);
      } else if (value.isArray()) {
        resMap.put(fieldName,
            validateTemplateInputsInArray(accountId, orgId, projectId, childYamlField.getNode(), templateInputsErrorMap,
                templateCacheMap, loadFromCache));
      } else {
        resMap.put(fieldName,
            validateTemplateInputsInObject(accountId, orgId, projectId, childYamlField.getNode(),
                templateInputsErrorMap, templateCacheMap, loadFromCache));
      }
    }
    return resMap;
  }

  private Object validateTemplateInputsInArray(String accountId, String orgId, String projectId, YamlNode yamlNode,
      Map<String, TemplateInputsErrorDTO> templateInputsErrorMap, Map<String, TemplateEntity> templateCacheMap,
      boolean loadFromCache) {
    List<Object> arrayList = new ArrayList<>();
    for (YamlNode arrayElement : yamlNode.asArray()) {
      if (yamlNode.getCurrJsonNode().isValueNode()) {
        arrayList.add(arrayElement);
      } else if (arrayElement.isObject()) {
        arrayList.add(validateTemplateInputsInObject(
            accountId, orgId, projectId, arrayElement, templateInputsErrorMap, templateCacheMap, loadFromCache));
      } else {
        arrayList.add(validateTemplateInputsInArray(
            accountId, orgId, projectId, arrayElement, templateInputsErrorMap, templateCacheMap, loadFromCache));
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
      Map<String, TemplateInputsErrorDTO> errorMap, Map<String, TemplateEntity> templateCacheMap,
      boolean loadFromCache) {
    String identifier = linkedTemplate.get(TEMPLATE_REF).asText();
    TemplateEntityGetResponse templateEntityGetResponse = getLinkedTemplateEntity(
        accountId, orgId, projectId, linkedTemplate, templateCacheMap, loadFromCache, HarnessYamlVersion.V0);
    TemplateEntity templateEntity = templateEntityGetResponse.getTemplateEntity();
    JsonNode linkedTemplateInputs = linkedTemplate.get(TEMPLATE_INPUTS);
    if (linkedTemplateInputs == null) {
      return linkedTemplate;
    }

    String templateYaml = templateEntity.getYaml();
    String templateSpecInputSetFormatYaml = createTemplateInputsFromTemplate(templateYaml);

    try {
      Map<String, JsonNode> dummyLinkedTemplateInputsMap = new LinkedHashMap<>();
      dummyLinkedTemplateInputsMap.put(DUMMY_NODE, linkedTemplateInputs);
      String dummyLinkedTemplateInputsYaml = YamlUtils.writeYamlString(dummyLinkedTemplateInputsMap);

      Map<String, TemplateInputsErrorDTO> uuidToErrorMessageMap = new LinkedHashMap<>();
      String invalidLinkedTemplateInputsYaml;
      if (isNotEmpty(templateSpecInputSetFormatYaml)) {
        JsonNode templateSpecInputSetFormatNode =
            YamlUtils.readTree(templateSpecInputSetFormatYaml).getNode().getCurrJsonNode();
        Map<String, JsonNode> dummyTemplateSpecMap = new LinkedHashMap<>();
        dummyTemplateSpecMap.put(DUMMY_NODE, templateSpecInputSetFormatNode);
        invalidLinkedTemplateInputsYaml = getInvalidInputValuesYaml(YamlUtils.writeYamlString(dummyTemplateSpecMap),
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
      return YamlUtils.readTree(YamlUtils.writeYamlString(originalTemplateMap)).getNode().getCurrJsonNode();
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
          String error = validateStaticValues(templateValue, linkedTemplateInputValue, key.getExpressionFqn());
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

  private TemplateUniqueIdentifier parseYamlAndGetTemplateIdentifierAndVersion(JsonNode yaml) {
    String templateIdentifier = yaml.get(TEMPLATE_REF).asText();
    String versionLabel = "";
    String versionMarker = STABLE_VERSION;
    String gitBranch = null;
    if (yaml.get(GIT_BRANCH) != null) {
      gitBranch = yaml.get(GIT_BRANCH).asText();
    }
    if (yaml.get(TEMPLATE_VERSION_LABEL) != null) {
      versionLabel = yaml.get(TEMPLATE_VERSION_LABEL).asText();
      versionMarker = versionLabel;
    }
    return TemplateUniqueIdentifier.builder()
        .templateIdentifier(templateIdentifier)
        .versionLabel(versionLabel)
        .versionMaker(versionMarker)
        .gitBranch(gitBranch)
        .build();
  }

  private TemplateUniqueIdentifier parseYamlAndGetTemplateIdentifierAndVersionV1(JsonNode yaml) {
    String[] templateIdAndLabel = yaml.get(YAMLFieldNameConstants.REF).asText().split("@");
    String templateIdentifier = templateIdAndLabel[0];
    String versionLabel = "";
    String versionMarker = STABLE_VERSION;
    if (templateIdAndLabel.length > 1) {
      versionLabel = templateIdAndLabel[1];
      versionMarker = versionLabel;
    }
    String gitBranch = null;
    if (yaml.get(GIT_BRANCH) != null) {
      gitBranch = yaml.get(GIT_BRANCH).asText();
    }
    return TemplateUniqueIdentifier.builder()
        .templateIdentifier(templateIdentifier)
        .versionLabel(versionLabel)
        .versionMaker(versionMarker)
        .gitBranch(gitBranch)
        .build();
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

  private JsonNode getSpecFromTemplateEntity(TemplateEntity templateEntity) {
    String templateYaml = templateEntity.getYaml();
    if (HarnessYamlVersion.isV1(templateEntity.getHarnessVersion())) {
      // First spec is the root for the template content it will name/id/type field. Inner spec will have the content
      // of the template.
      JsonNode templateNode = YamlUtils.readAsJsonNode(templateYaml).get(YAMLFieldNameConstants.SPEC);
      if (templateNode == null || !templateNode.has(YAMLFieldNameConstants.SPEC)) {
        throw new NGTemplateException(
            String.format("Could not extract spec from the template YAML for the template with id: %s",
                templateEntity.getIdentifier()));
      }
      return templateNode.get(YAMLFieldNameConstants.SPEC);
    }
    try {
      NGTemplateConfig templateConfig = YamlUtils.read(templateYaml, NGTemplateConfig.class);
      return templateConfig.getTemplateInfoConfig().getSpec();
    } catch (IOException e) {
      log.error("Could not read template yaml", e);
      throw new NGTemplateException("Could not read template yaml: " + e.getMessage());
    }
  }
}
