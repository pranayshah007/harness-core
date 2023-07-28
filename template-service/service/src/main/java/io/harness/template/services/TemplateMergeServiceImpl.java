/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.template.services;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.template.resources.beans.NGTemplateConstants.GIT_BRANCH;
import static io.harness.template.resources.beans.NGTemplateConstants.TEMPLATE;
import static io.harness.template.resources.beans.NGTemplateConstants.TEMPLATE_REF;
import static io.harness.template.resources.beans.NGTemplateConstants.TEMPLATE_VERSION_LABEL;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.FeatureName;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.ngexception.NGTemplateException;
import io.harness.ng.core.template.TemplateMergeResponseDTO;
import io.harness.ng.core.template.TemplateReferenceSummary;
import io.harness.ng.core.template.TemplateRetainVariablesResponse;
import io.harness.pms.merger.fqn.FQN;
import io.harness.pms.merger.fqn.FQNNode;
import io.harness.pms.merger.helpers.FQNMapGenerator;
import io.harness.pms.merger.helpers.YamlRefreshHelper;
import io.harness.pms.yaml.YamlNode;
import io.harness.pms.yaml.YamlUtils;
import io.harness.template.entity.TemplateEntity;
import io.harness.template.helpers.MergeTemplateInputsInObject;
import io.harness.template.helpers.TemplateInputsValidator;
import io.harness.template.helpers.TemplateMergeServiceHelper;
import io.harness.template.mappers.NGTemplateDtoMapper;
import io.harness.template.utils.NGTemplateFeatureFlagHelperService;
import io.harness.template.utils.TemplateUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.inject.Inject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true,
    components = {HarnessModuleComponent.CDS_PIPELINE, HarnessModuleComponent.CDS_GITX,
        HarnessModuleComponent.CDS_TEMPLATE_LIBRARY})
@OwnedBy(HarnessTeam.CDC)
@Slf4j
public class TemplateMergeServiceImpl implements TemplateMergeService {
  @Inject private NGTemplateServiceHelper templateServiceHelper;
  @Inject private TemplateInputsValidator templateInputsValidator;
  @Inject private TemplateMergeServiceHelper templateMergeServiceHelper;

  @Inject private NGTemplateFeatureFlagHelperService ngTemplateFeatureFlagHelperService;
  @Override
  public String getTemplateInputs(String accountId, String orgIdentifier, String projectIdentifier,
      String templateIdentifier, String versionLabel, boolean loadFromCache) {
    Optional<TemplateEntity> optionalTemplateEntity = templateServiceHelper.getTemplateOrThrowExceptionIfInvalid(
        accountId, orgIdentifier, projectIdentifier, templateIdentifier, versionLabel, false, loadFromCache);
    if (optionalTemplateEntity.isEmpty()) {
      throw new NGTemplateException("Template to fetch template inputs does not exist.");
    }
    return templateMergeServiceHelper.createTemplateInputsFromTemplate(optionalTemplateEntity.get().getYaml());
  }

  @Override
  /**
   * Same as applyTemplatesToYamlV2 since we removed all the validations
   * will deprecate this soon
   */
  public TemplateMergeResponseDTO applyTemplatesToYaml(String accountId, String orgId, String projectId, String yaml,
      boolean getMergedYamlWithTemplateField, boolean loadFromCache, boolean appendInputSetValidator) {
    YamlNode yamlNode = TemplateUtils.validateAndGetYamlNode(yaml);
    TemplateUtils.setupGitParentEntityDetails(accountId, orgId, projectId, null, null);
    Map<String, TemplateEntity> templateCacheMap = new HashMap<>();
    return getTemplateMergeResponseDTO(accountId, orgId, projectId, getMergedYamlWithTemplateField, yamlNode,
        templateCacheMap, loadFromCache, appendInputSetValidator);
  }

  @Override
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
  public TemplateMergeResponseDTO applyTemplatesToYamlV2(String accountId, String orgId, String projectId,
      JsonNode entityJsonNode, boolean getMergedYamlWithTemplateField, boolean loadFromCache,
      boolean appendInputSetValidator) {
    YamlNode entityYamlNode = TemplateUtils.validateAndGetYamlNode(entityJsonNode);
    TemplateUtils.setupGitParentEntityDetails(accountId, orgId, projectId, null, null);
    Map<String, TemplateEntity> templateCacheMap = new HashMap<>();
    return getTemplateMergeResponseDTO(accountId, orgId, projectId, getMergedYamlWithTemplateField, entityYamlNode,
        templateCacheMap, loadFromCache, appendInputSetValidator);
  }

  @Override
  public TemplateRetainVariablesResponse mergeTemplateInputs(String newTemplateInputs, String originalTemplateInputs) {
    JsonNode templateInputSetJsonNode;
    JsonNode originalTemplateInputSetJsonNode;
    if (EmptyPredicate.isEmpty(originalTemplateInputs)) {
      return TemplateRetainVariablesResponse.builder().mergedTemplateInputs(newTemplateInputs).build();
    }
    if (EmptyPredicate.isEmpty(newTemplateInputs)) {
      return TemplateRetainVariablesResponse.builder().mergedTemplateInputs("").build();
    }
    try {
      templateInputSetJsonNode = YamlUtils.readTree(newTemplateInputs).getNode().getCurrJsonNode();
      originalTemplateInputSetJsonNode = YamlUtils.readTree(originalTemplateInputs).getNode().getCurrJsonNode();
    } catch (IOException e) {
      throw new InvalidRequestException("Couldn't convert yaml to JsonNode");
    }
    JsonNode updatedJsonNode =
        YamlRefreshHelper.refreshNodeFromSourceNode(originalTemplateInputSetJsonNode, templateInputSetJsonNode);
    return TemplateRetainVariablesResponse.builder()
        .mergedTemplateInputs(YamlUtils.writeYamlString(updatedJsonNode))
        .build();
  }

  private TemplateMergeResponseDTO getTemplateMergeResponseDTO(String accountId, String orgId, String projectId,
      boolean getMergedYamlWithTemplateField, YamlNode entityYamlNode, Map<String, TemplateEntity> templateCacheMap,
      boolean loadFromCache, boolean appendInputSetValidator) {
    Map<String, Object> resMap;
    if (ngTemplateFeatureFlagHelperService.isFeatureFlagEnabled(accountId, FeatureName.PIE_NG_BATCH_GET_TEMPLATES)) {
      templateCacheMap.putAll(templateMergeServiceHelper.getAllTemplatesFromYaml(
          accountId, orgId, projectId, entityYamlNode, loadFromCache));
    }
    MergeTemplateInputsInObject mergeTemplateInputsInObject = null;
    if (!getMergedYamlWithTemplateField) {
      resMap = templateMergeServiceHelper.mergeTemplateInputsInObject(
          accountId, orgId, projectId, entityYamlNode, templateCacheMap, 0, loadFromCache, appendInputSetValidator);
    } else {
      mergeTemplateInputsInObject = templateMergeServiceHelper.mergeTemplateInputsInObjectAlongWithOpaPolicy(
          accountId, orgId, projectId, entityYamlNode, templateCacheMap, 0, loadFromCache, appendInputSetValidator);
      resMap = mergeTemplateInputsInObject.getResMap();
    }

    List<TemplateReferenceSummary> templateReferenceSummaries =
        getTemplateReferenceSummaries(accountId, orgId, projectId, entityYamlNode.getCurrJsonNode(), templateCacheMap);
    return TemplateMergeResponseDTO.builder()
        .mergedPipelineYaml(YamlUtils.writeYamlString(resMap))
        .templateReferenceSummaries(templateReferenceSummaries)
        .mergedPipelineYamlWithTemplateRef(mergeTemplateInputsInObject == null
                ? null
                : YamlUtils.writeYamlString(mergeTemplateInputsInObject.getResMapWithOpaResponse()))
        .cacheResponseMetadata(NGTemplateDtoMapper.getCacheResponse())
        .build();
  }

  private List<TemplateReferenceSummary> getTemplateReferenceSummaries(String accountId, String orgId, String projectId,
      JsonNode entityJsonNode, Map<String, TemplateEntity> templateCacheMap) {
    Map<FQN, Object> fqnToValueMap = FQNMapGenerator.generateFQNMap(entityJsonNode);
    Set<FQN> fqnSet = new LinkedHashSet<>(fqnToValueMap.keySet());
    List<TemplateReferenceSummary> templateReferenceSummaries = new ArrayList<>();
    fqnSet.forEach(key -> {
      if (key.getFqnList().size() >= 2) {
        List<FQNNode> fqnList = new ArrayList<>(key.getFqnList());
        FQNNode lastNode = fqnList.get(fqnList.size() - 1);
        FQNNode secondLastNode = fqnList.get(fqnList.size() - 2);

        if (TEMPLATE_REF.equals(lastNode.getKey()) && TEMPLATE.equals(secondLastNode.getKey())) {
          String identifier = ((JsonNode) fqnToValueMap.get(key)).asText();

          // remove templateRef from FQN and add versionLabel to FQN to fetch corresponding template version.
          fqnList.remove(fqnList.size() - 1);
          fqnList.add(FQNNode.builder().nodeType(FQNNode.NodeType.KEY).key(TEMPLATE_VERSION_LABEL).build());
          JsonNode versionLabelNode = (JsonNode) fqnToValueMap.get(FQN.builder().fqnList(fqnList).build());
          String versionLabel = versionLabelNode == null ? null : versionLabelNode.asText();
          fqnList.remove(fqnList.size() - 1);
          fqnList.add(FQNNode.builder().nodeType(FQNNode.NodeType.KEY).key(GIT_BRANCH).build());
          JsonNode gitBranchNode = (JsonNode) fqnToValueMap.get(FQN.builder().fqnList(fqnList).build());
          String gitBranch = gitBranchNode == null ? null : gitBranchNode.asText();
          TemplateEntity templateEntity = templateMergeServiceHelper.getLinkedTemplateEntity(
              accountId, orgId, projectId, identifier, versionLabel, templateCacheMap, gitBranch);
          Set<String> moduleInfo = new HashSet<>();
          moduleInfo = isNotEmpty(templateEntity.getModules()) ? templateEntity.getModules() : moduleInfo;

          // remove template and versionLabel from FQN to construct base FQN.
          fqnList.remove(fqnList.size() - 1);
          fqnList.remove(fqnList.size() - 1);
          TemplateReferenceSummary templateReferenceSummary =
              TemplateReferenceSummary.builder()
                  .fqn(FQN.builder().fqnList(fqnList).build().getExpressionFqn())
                  .templateIdentifier(templateEntity.getIdentifier())
                  .versionLabel(versionLabelNode == null ? templateEntity.getVersionLabel() : versionLabel)
                  .scope(templateEntity.getTemplateScope())
                  .moduleInfo(moduleInfo)
                  .stableTemplate(versionLabelNode == null)
                  .build();
          templateReferenceSummaries.add(templateReferenceSummary);
        }
      }
    });
    return templateReferenceSummaries;
  }
}
