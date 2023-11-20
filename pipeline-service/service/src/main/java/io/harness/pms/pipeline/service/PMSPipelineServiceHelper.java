/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.pipeline.service;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.telemetry.Destination.AMPLITUDE;

import static java.lang.String.format;
import static org.springframework.data.mongodb.core.query.Criteria.where;

import io.harness.ModuleType;
import io.harness.NGResourceFilterConstants;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.FeatureName;
import io.harness.beans.Scope;
import io.harness.data.structure.EmptyPredicate;
import io.harness.data.structure.HarnessStringUtils;
import io.harness.engine.governance.PolicyEvaluationFailureException;
import io.harness.eventsframework.schemas.entity.EntityDetailProtoDTO;
import io.harness.exception.DuplicateFileImportException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.NestedExceptionUtils;
import io.harness.exception.ngexception.InvalidFieldsDTO;
import io.harness.exception.ngexception.NGTemplateException;
import io.harness.exception.ngexception.beans.yamlschema.YamlSchemaErrorDTO;
import io.harness.exception.ngexception.beans.yamlschema.YamlSchemaErrorWrapperDTO;
import io.harness.filter.FilterType;
import io.harness.filter.dto.FilterDTO;
import io.harness.filter.service.FilterService;
import io.harness.gitaware.dto.GitContextRequestParams;
import io.harness.gitaware.helper.GitAwareContextHelper;
import io.harness.gitaware.helper.GitAwareEntityHelper;
import io.harness.gitsync.beans.StoreType;
import io.harness.gitsync.helpers.GitContextHelper;
import io.harness.gitsync.interceptor.GitEntityInfo;
import io.harness.gitsync.interceptor.GitSyncBranchContext;
import io.harness.governance.GovernanceMetadata;
import io.harness.governance.PolicySetMetadata;
import io.harness.ng.core.common.beans.NGTag;
import io.harness.ng.core.common.beans.NGTag.NGTagKeys;
import io.harness.ng.core.template.TemplateMergeResponseDTO;
import io.harness.pms.filter.creation.FilterCreatorMergeService;
import io.harness.pms.filter.creation.FilterCreatorMergeServiceResponse;
import io.harness.pms.filter.utils.ModuleInfoFilterUtils;
import io.harness.pms.gitsync.PmsGitSyncBranchContextGuard;
import io.harness.pms.instrumentaion.PipelineInstrumentationConstants;
import io.harness.pms.instrumentaion.PipelineInstrumentationUtils;
import io.harness.pms.pipeline.MoveConfigOperationDTO;
import io.harness.pms.pipeline.PipelineEntity;
import io.harness.pms.pipeline.PipelineEntity.PipelineEntityKeys;
import io.harness.pms.pipeline.PipelineEntityUtils;
import io.harness.pms.pipeline.PipelineFilterPropertiesDto;
import io.harness.pms.pipeline.PipelineImportRequestDTO;
import io.harness.pms.pipeline.PipelineMetadataV2.PipelineMetadataV2Keys;
import io.harness.pms.pipeline.governance.service.PipelineGovernanceService;
import io.harness.pms.pipeline.references.FilterCreationGitMetadata;
import io.harness.pms.pipeline.references.FilterCreationParams;
import io.harness.pms.pipeline.references.PipelineSetupUsageCreationHelper;
import io.harness.pms.pipeline.validation.PipelineValidationResponse;
import io.harness.pms.pipeline.validation.service.PipelineValidationService;
import io.harness.pms.plan.execution.beans.PipelineExecutionSummaryEntity.PlanExecutionSummaryKeys;
import io.harness.pms.yaml.HarnessYamlVersion;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YAMLMetadataFieldNameConstants;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlUtils;
import io.harness.pms.yaml.preprocess.YamlPreProcessor;
import io.harness.pms.yaml.preprocess.YamlPreProcessorFactory;
import io.harness.repositories.pipeline.PMSPipelineRepository;
import io.harness.serializer.JsonUtils;
import io.harness.telemetry.TelemetryReporter;
import io.harness.utils.PmsFeatureFlagService;
import io.harness.yaml.validator.InvalidYamlException;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;
import javax.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Update;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true,
    components = {HarnessModuleComponent.CDS_PIPELINE, HarnessModuleComponent.CDS_TEMPLATE_LIBRARY})
@Singleton
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@Slf4j
@OwnedBy(PIPELINE)
public class PMSPipelineServiceHelper {
  @Inject private final FilterService filterService;
  @Inject private final FilterCreatorMergeService filterCreatorMergeService;
  @Inject private final PipelineValidationService pipelineValidationService;
  @Inject private final PipelineGovernanceService pipelineGovernanceService;
  @Inject private final PMSPipelineTemplateHelper pipelineTemplateHelper;
  @Inject private final PmsFeatureFlagService pmsFeatureFlagService;
  @Inject private final TelemetryReporter telemetryReporter;
  @Inject private final GitAwareEntityHelper gitAwareEntityHelper;
  @Inject private final PMSPipelineRepository pmsPipelineRepository;
  @Inject private final PipelineSetupUsageCreationHelper pipelineSetupUsageCreationHelper;
  @Inject private final PMSPipelineService pmsPipelineService;
  @Inject @Named("PipelineExecutorService") ExecutorService executorService;
  @Inject private final YamlPreProcessorFactory yamlPreProcessorFactory;

  public static String PIPELINE_SAVE = "pipeline_save";
  public static String PIPELINE_SAVE_ACTION_TYPE = "action";
  public static String PIPELINE_NAME = "pipelineName";
  public static String ACCOUNT_ID = "accountId";
  public static String ORG_ID = "orgId";
  public static String PROJECT_ID = "projectId";
  public static String PIPELINE_ID = "pipelineId";
  public static String TEMPLATE_REF_PIPELINE = "template_ref_by_pipeline";
  public static String TEMPLATE_ID = "templateIdentifier";
  public static String MODULE_NAME = "moduleName";

  public static void validatePresenceOfRequiredFields(PipelineEntity pipelineEntity) {
    HashMap<String, String> requiredFieldMap = new HashMap<>();
    requiredFieldMap.put(ACCOUNT_ID, pipelineEntity.getAccountId());
    requiredFieldMap.put(ORG_ID, pipelineEntity.getOrgIdentifier());
    requiredFieldMap.put(PROJECT_ID, pipelineEntity.getProjectIdentifier());
    requiredFieldMap.put(PIPELINE_ID, pipelineEntity.getIdentifier());

    requiredFieldMap.forEach((requiredField, value) -> {
      if (EmptyPredicate.isEmpty(value)) {
        throw new InvalidRequestException(String.format("Required field [%s] is either null or empty.", requiredField));
      }
    });
  }

  public static Criteria getPipelineEqualityCriteria(String accountId, String orgIdentifier, String projectIdentifier,
      String pipelineIdentifier, boolean deleted, Long version) {
    Criteria criteria = Criteria.where(PipelineEntityKeys.accountId)
                            .is(accountId)
                            .and(PipelineEntityKeys.orgIdentifier)
                            .is(orgIdentifier)
                            .and(PipelineEntityKeys.projectIdentifier)
                            .is(projectIdentifier)
                            .and(PipelineEntityKeys.identifier)
                            .is(pipelineIdentifier)
                            .and(PipelineEntityKeys.deleted)
                            .is(deleted);

    if (version != null) {
      criteria.and(PipelineEntityKeys.version).is(version);
    }

    return criteria;
  }

  public static Criteria buildCriteriaForRepoListing(
      String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    Criteria criteria = new Criteria();

    criteria.and(PipelineEntityKeys.accountId).is(accountIdentifier);
    criteria.and(PipelineEntityKeys.orgIdentifier).is(orgIdentifier);
    criteria.and(PipelineEntityKeys.projectIdentifier).is(projectIdentifier);

    return criteria;
  }

  public PipelineEntity updatePipelineInfo(PipelineEntity pipelineEntity, String pipelineVersion) throws IOException {
    switch (pipelineVersion) {
      case HarnessYamlVersion.V1:
        return pipelineEntity;
      case HarnessYamlVersion.V0:
        return updatePipelineInfoInternal(pipelineEntity);
      default:
        throw new IllegalStateException("version not supported");
    }
  }

  public void populateFilterUsingIdentifier(List<Criteria> criteriaList, Criteria criteria, String accountIdentifier,
      String orgIdentifier, String projectIdentifier, @NotNull String filterIdentifier) {
    FilterDTO pipelineFilterDTO = filterService.get(
        accountIdentifier, orgIdentifier, projectIdentifier, filterIdentifier, FilterType.PIPELINESETUP);
    if (pipelineFilterDTO == null) {
      throw new InvalidRequestException("Could not find a pipeline filter with the identifier ");
    } else {
      populateFilter(criteriaList, criteria, (PipelineFilterPropertiesDto) pipelineFilterDTO.getFilterProperties());
    }
  }

  public static void populateFilter(
      List<Criteria> criteriaList, Criteria criteria, @NotNull PipelineFilterPropertiesDto pipelineFilter) {
    if (EmptyPredicate.isNotEmpty(pipelineFilter.getName())) {
      criteria.and(PipelineEntityKeys.name).is(pipelineFilter.getName());
    }
    if (EmptyPredicate.isNotEmpty(pipelineFilter.getDescription())) {
      criteria.and(PipelineEntityKeys.description).is(pipelineFilter.getDescription());
    }
    if (EmptyPredicate.isNotEmpty(pipelineFilter.getPipelineTags())) {
      addPipelineTagsCriteria(criteriaList, pipelineFilter.getPipelineTags());
    }
    if (EmptyPredicate.isNotEmpty(pipelineFilter.getPipelineIdentifiers())) {
      criteria.and(PipelineEntityKeys.identifier).in(pipelineFilter.getPipelineIdentifiers());
    }
    if (pipelineFilter.getModuleProperties() != null) {
      ModuleInfoFilterUtils.processNode(
          JsonUtils.readTree(pipelineFilter.getModuleProperties().toJson()), "filters", criteria);
    }
    if (EmptyPredicate.isNotEmpty(pipelineFilter.getRepoName())) {
      criteria.and(PipelineEntityKeys.repo).is(pipelineFilter.getRepoName());
    }
  }

  public static void addPipelineTagsCriteria(List<Criteria> criteriaList, List<NGTag> pipelineTags) {
    List<NGTag> ngTagsList = new ArrayList<>();
    List<String> tags = new ArrayList<>();
    pipelineTags.forEach(o -> {
      if (o.getKey() == null) {
        throw new InvalidRequestException("Key in Pipeline Tags filter cannot be null");
      } else if (o.getValue() == null) {
        tags.add(o.getKey());
      } else {
        ngTagsList.add(o);
      }
    });
    Criteria tagsCriteria = new Criteria();
    if (tags.size() > 0 && ngTagsList.size() > 0) {
      tagsCriteria.orOperator(where(PlanExecutionSummaryKeys.tagsKey).in(tags),
          where(PlanExecutionSummaryKeys.tagsValue).in(tags), where(PlanExecutionSummaryKeys.tags).in(ngTagsList));
    } else if (tags.size() > 0) {
      tagsCriteria.orOperator(
          where(PlanExecutionSummaryKeys.tagsKey).in(tags), where(PlanExecutionSummaryKeys.tagsValue).in(tags));
    } else if (ngTagsList.size() > 0) {
      tagsCriteria = where(PlanExecutionSummaryKeys.tags).in(ngTagsList);
    }
    criteriaList.add(tagsCriteria);
  }

  public void resolveTemplatesAndValidatePipelineEntity(PipelineEntity pipelineEntity, boolean loadFromCache) {
    long start = System.currentTimeMillis();
    GovernanceMetadata governanceMetadata = resolveTemplatesAndValidatePipeline(pipelineEntity, false, loadFromCache);
    log.info("[PMS_PipelineService] validating pipeline took {}ms for projectId {}, orgId {}, accountId {}",
        System.currentTimeMillis() - start, pipelineEntity.getProjectIdentifier(), pipelineEntity.getOrgIdentifier(),
        pipelineEntity.getAccountIdentifier());
    if (governanceMetadata.getDeny()) {
      List<String> denyingRuleSetIds = governanceMetadata.getDetailsList()
                                           .stream()
                                           .filter(PolicySetMetadata::getDeny)
                                           .map(PolicySetMetadata::getIdentifier)
                                           .collect(Collectors.toList());
      throw new PolicyEvaluationFailureException(
          "Pipeline does not follow the Policies in these Policy Sets: " + denyingRuleSetIds.toString(),
          governanceMetadata, pipelineEntity.getYaml());
    }
  }

  public PipelineEntity updatePipelineFilters(PipelineEntity pipelineToUpdate, String uuid, Integer yamlHash) {
    return pmsPipelineRepository.updatePipelineFilters(pipelineToUpdate, uuid, yamlHash);
  }

  @VisibleForTesting
  static void checkAndThrowMismatchInImportedPipelineMetadataInternal(String orgIdentifier, String projectIdentifier,
      String pipelineIdentifier, PipelineImportRequestDTO pipelineImportRequest, String importedPipeline) {
    YamlField pipelineYamlField;
    try {
      pipelineYamlField = YamlUtils.readTree(importedPipeline);
    } catch (IOException e) {
      String errorMessage = PipelineCRUDErrorResponse.errorMessageForNotAYAMLFile(
          GitAwareContextHelper.getBranchInRequest(), GitAwareContextHelper.getFilepathInRequest());
      throw PMSPipelineServiceHelper.buildInvalidYamlException(errorMessage, importedPipeline);
    }
    YamlField pipelineInnerField = pipelineYamlField.getNode().getField(YAMLFieldNameConstants.PIPELINE);
    if (pipelineInnerField == null) {
      String errorMessage = PipelineCRUDErrorResponse.errorMessageForNotAPipelineYAML(
          GitAwareContextHelper.getBranchInRequest(), GitAwareContextHelper.getFilepathInRequest());
      throw PMSPipelineServiceHelper.buildInvalidYamlException(errorMessage, importedPipeline);
    }

    Map<String, String> changedFields = new HashMap<>();

    String identifierFromGit = pipelineInnerField.getNode().getIdentifier();
    if (!pipelineIdentifier.equals(identifierFromGit)) {
      changedFields.put(YAMLMetadataFieldNameConstants.IDENTIFIER, identifierFromGit);
    }

    String nameFromGit = pipelineInnerField.getNode().getName();
    if (!pipelineImportRequest.getPipelineName().equals(nameFromGit)) {
      changedFields.put(YAMLMetadataFieldNameConstants.NAME, nameFromGit);
    }

    String orgIdentifierFromGit = pipelineInnerField.getNode().getStringValue(YAMLFieldNameConstants.ORG_IDENTIFIER);
    if (!orgIdentifier.equals(orgIdentifierFromGit)) {
      changedFields.put(YAMLMetadataFieldNameConstants.ORG_IDENTIFIER, orgIdentifierFromGit);
    }

    String projectIdentifierFromGit =
        pipelineInnerField.getNode().getStringValue(YAMLFieldNameConstants.PROJECT_IDENTIFIER);
    if (!projectIdentifier.equals(projectIdentifierFromGit)) {
      changedFields.put(YAMLMetadataFieldNameConstants.PROJECT_IDENTIFIER, projectIdentifierFromGit);
    }

    if (!changedFields.isEmpty()) {
      InvalidFieldsDTO invalidFields = InvalidFieldsDTO.builder().expectedValues(changedFields).build();
      throw new InvalidRequestException(
          "Requested metadata params do not match the values found in the YAML on Git for these fields: "
              + changedFields.keySet(),
          invalidFields);
    }
  }

  public GovernanceMetadata resolveTemplatesAndValidatePipeline(PipelineEntity pipelineEntity, boolean loadFromCache) {
    return resolveTemplatesAndValidatePipelineYaml(pipelineEntity, true, loadFromCache);
  }

  public GovernanceMetadata resolveTemplatesAndValidatePipeline(
      PipelineEntity pipelineEntity, boolean throwExceptionIfGovernanceRulesFails, boolean loadFromCache) {
    try {
      GitEntityInfo gitEntityInfo = GitContextHelper.getGitEntityInfo();
      if (HarnessYamlVersion.isV1(pipelineEntity.getHarnessVersion())) {
        String yaml = preProcessPipelineYaml(pipelineEntity.getYaml());
        pipelineEntity.setYaml(yaml);
      }
      if (gitEntityInfo != null && gitEntityInfo.isNewBranch()) {
        GitSyncBranchContext gitSyncBranchContext =
            GitSyncBranchContext.builder()
                .gitBranchInfo(GitEntityInfo.builder()
                                   .branch(gitEntityInfo.getBaseBranch())
                                   .connectorRef(gitEntityInfo.getConnectorRef())
                                   .repoName(gitEntityInfo.getRepoName())
                                   .yamlGitConfigId(gitEntityInfo.getYamlGitConfigId())
                                   .build())
                .build();
        try (PmsGitSyncBranchContextGuard ignored = new PmsGitSyncBranchContextGuard(gitSyncBranchContext, true)) {
          return resolveTemplatesAndValidatePipelineYaml(
              pipelineEntity, throwExceptionIfGovernanceRulesFails, loadFromCache);
        }
      } else {
        return resolveTemplatesAndValidatePipelineYaml(
            pipelineEntity, throwExceptionIfGovernanceRulesFails, loadFromCache);
      }
    } catch (io.harness.yaml.validator.InvalidYamlException ex) {
      ex.setYaml(pipelineEntity.getData());
      throw ex;
    } catch (NGTemplateException ex) {
      throw ex;
    } catch (Exception ex) {
      YamlSchemaErrorWrapperDTO errorWrapperDTO =
          YamlSchemaErrorWrapperDTO.builder()
              .schemaErrors(Collections.singletonList(
                  YamlSchemaErrorDTO.builder().message(ex.getMessage()).fqn("$.pipeline").build()))
              .build();
      throw new io.harness.yaml.validator.InvalidYamlException(
          HarnessStringUtils.emptyIfNull(ex.getMessage()), ex, errorWrapperDTO, pipelineEntity.getData());
    }
  }

  public GovernanceMetadata validatePipeline(PipelineEntity pipelineEntity,
      TemplateMergeResponseDTO templateMergeResponseDTO, boolean throwExceptionIfGovernanceRulesFails) {
    try {
      GitEntityInfo gitEntityInfo = GitContextHelper.getGitEntityInfo();
      if (gitEntityInfo != null && gitEntityInfo.isNewBranch()) {
        GitSyncBranchContext gitSyncBranchContext =
            GitSyncBranchContext.builder()
                .gitBranchInfo(GitEntityInfo.builder()
                                   .branch(gitEntityInfo.getBaseBranch())
                                   .yamlGitConfigId(gitEntityInfo.getYamlGitConfigId())
                                   .build())
                .build();
        try (PmsGitSyncBranchContextGuard ignored = new PmsGitSyncBranchContextGuard(gitSyncBranchContext, true)) {
          return validateYaml(pipelineEntity, templateMergeResponseDTO, throwExceptionIfGovernanceRulesFails)
              .getGovernanceMetadata();
        }
      } else {
        return validateYaml(pipelineEntity, templateMergeResponseDTO, throwExceptionIfGovernanceRulesFails)
            .getGovernanceMetadata();
      }
    } catch (io.harness.yaml.validator.InvalidYamlException ex) {
      ex.setYaml(pipelineEntity.getData());
      throw ex;
    } catch (Exception ex) {
      YamlSchemaErrorWrapperDTO errorWrapperDTO =
          YamlSchemaErrorWrapperDTO.builder()
              .schemaErrors(Collections.singletonList(
                  YamlSchemaErrorDTO.builder().message(ex.getMessage()).fqn("$.pipeline").build()))
              .build();
      throw new io.harness.yaml.validator.InvalidYamlException(
          HarnessStringUtils.emptyIfNull(ex.getMessage()), ex, errorWrapperDTO, pipelineEntity.getData());
    }
  }

  GovernanceMetadata resolveTemplatesAndValidatePipelineYaml(
      PipelineEntity pipelineEntity, boolean throwExceptionIfGovernanceRulesFails, boolean loadFromCache) {
    boolean getMergedTemplateWithTemplateReferences =
        pmsFeatureFlagService.isEnabled(pipelineEntity.getAccountId(), FeatureName.OPA_PIPELINE_GOVERNANCE);
    // Apply all the templateRefs(if any) then check for schema validation.
    TemplateMergeResponseDTO templateMergeResponseDTO = pipelineTemplateHelper.resolveTemplateRefsInPipeline(
        pipelineEntity, getMergedTemplateWithTemplateReferences, loadFromCache);
    // Add Template Module Info temporarily to Pipeline Entity
    pipelineEntity.setTemplateModules(pipelineTemplateHelper.getTemplatesModuleInfo(templateMergeResponseDTO));
    return validateYaml(pipelineEntity, templateMergeResponseDTO, throwExceptionIfGovernanceRulesFails)
        .getGovernanceMetadata();
  }

  PipelineValidationResponse validateYaml(PipelineEntity pipelineEntity,
      TemplateMergeResponseDTO templateMergeResponseDTO, boolean throwExceptionIfGovernanceRulesFails) {
    String accountId = pipelineEntity.getAccountId();
    String orgIdentifier = pipelineEntity.getOrgIdentifier();
    String projectIdentifier = pipelineEntity.getProjectIdentifier();
    String resolveTemplateRefsInPipeline = templateMergeResponseDTO.getMergedPipelineYaml();

    if (throwExceptionIfGovernanceRulesFails) {
      return pipelineValidationService.validateYamlAndGovernanceRules(accountId, orgIdentifier, projectIdentifier,
          resolveTemplateRefsInPipeline, templateMergeResponseDTO.getMergedPipelineYamlWithTemplateRef(),
          pipelineEntity);
    }
    return pipelineValidationService.validateYamlAndGetGovernanceMetadata(accountId, orgIdentifier, projectIdentifier,
        resolveTemplateRefsInPipeline, templateMergeResponseDTO.getMergedPipelineYamlWithTemplateRef(), pipelineEntity);
  }

  public Criteria formCriteria(String accountId, String orgId, String projectId, String filterIdentifier,
      PipelineFilterPropertiesDto filterProperties, boolean deleted, String module, String searchTerm) {
    Criteria criteria = new Criteria();
    if (isNotEmpty(accountId)) {
      criteria.and(PipelineEntityKeys.accountId).is(accountId);
    }
    if (isNotEmpty(orgId)) {
      criteria.and(PipelineEntityKeys.orgIdentifier).is(orgId);
    }
    if (isNotEmpty(projectId)) {
      criteria.and(PipelineEntityKeys.projectIdentifier).is(projectId);
    }

    criteria.and(PipelineEntityKeys.deleted).is(deleted);
    List<Criteria> criteriaList = new ArrayList<>();
    if (EmptyPredicate.isNotEmpty(filterIdentifier) && filterProperties != null) {
      throw new InvalidRequestException("Can not apply both filter properties and saved filter together");
    } else if (EmptyPredicate.isNotEmpty(filterIdentifier) && filterProperties == null) {
      populateFilterUsingIdentifier(criteriaList, criteria, accountId, orgId, projectId, filterIdentifier);
    } else if (EmptyPredicate.isEmpty(filterIdentifier) && filterProperties != null) {
      populateFilter(criteriaList, criteria, filterProperties);
    }

    Criteria moduleCriteria = new Criteria();
    if (EmptyPredicate.isNotEmpty(module)) {
      // Check if the provided module type is valid
      checkThatTheModuleExists(module);
      // Add approval stage criteria to check for the pipelines containing the given module and the approval stage.
      Criteria approvalStageCriteria =
          Criteria.where(format("%s.%s.stageTypes", PipelineEntityKeys.filters, ModuleType.PMS.name().toLowerCase()))
              .exists(true);
      for (ModuleType moduleType : ModuleType.values()) {
        if (moduleType.isInternal()) {
          continue;
        }
        // This query ensures that only pipelines containing approval stage are visible.
        approvalStageCriteria.and(format("%s.%s", PipelineEntityKeys.filters, moduleType.name().toLowerCase()))
            .exists(false);
      }
      // Check for pipeline with no filters also - empty pipeline or pipelines with only approval stage
      // criteria = { "$or": [ { "filters": {} } , { "filters.MODULE": { $exists: true } } ] }
      moduleCriteria.orOperator(where(PipelineEntityKeys.filters).is(new Document()),
          where(format("%s.%s", PipelineEntityKeys.filters, module)).exists(true), approvalStageCriteria);
    }

    Criteria searchCriteria = new Criteria();
    if (EmptyPredicate.isNotEmpty(searchTerm)) {
      searchCriteria.orOperator(where(PipelineEntityKeys.identifier)
                                    .regex(searchTerm, NGResourceFilterConstants.CASE_INSENSITIVE_MONGO_OPTIONS),
          where(PipelineEntityKeys.name).regex(searchTerm, NGResourceFilterConstants.CASE_INSENSITIVE_MONGO_OPTIONS),
          where(PipelineEntityKeys.tags + "." + NGTagKeys.key)
              .regex(searchTerm, NGResourceFilterConstants.CASE_INSENSITIVE_MONGO_OPTIONS),
          where(PipelineEntityKeys.tags + "." + NGTagKeys.value)
              .regex(searchTerm, NGResourceFilterConstants.CASE_INSENSITIVE_MONGO_OPTIONS));
    }

    criteriaList.add(moduleCriteria);
    criteriaList.add(searchCriteria);
    criteria.andOperator(criteriaList.toArray(new Criteria[criteriaList.size()]));

    return criteria;
  }

  public void sendPipelineSaveTelemetryEvent(PipelineEntity entity, String actionType) {
    executorService.submit(() -> {
      try {
        HashMap<String, Object> properties = new HashMap<>();
        properties.put(PIPELINE_NAME, entity.getName());
        properties.put(ORG_ID, entity.getOrgIdentifier());
        properties.put(PROJECT_ID, entity.getProjectIdentifier());
        properties.put(PIPELINE_SAVE_ACTION_TYPE, actionType);
        properties.put(PipelineInstrumentationConstants.MODULE_NAME,
            PipelineEntityUtils.getModuleNameFromPipelineEntity(entity, "cd"));
        properties.put(
            PipelineInstrumentationConstants.STAGE_TYPES, PipelineInstrumentationUtils.getStageTypes(entity));
        telemetryReporter.sendTrackEvent(PIPELINE_SAVE, null, entity.getAccountId(), properties,
            Collections.singletonMap(AMPLITUDE, true), io.harness.telemetry.Category.GLOBAL);
      } catch (Exception ex) {
        log.error(
            format(
                "Exception while sending telemetry event for pipeline save. accountId: %s, orgId: %s, projectId: %s, pipelineId: %s",
                entity.getAccountIdentifier(), entity.getOrgIdentifier(), entity.getProjectIdentifier(),
                entity.getIdentifier()),
            ex);
      }
    });
  }

  public void sendTemplatesUsedInPipelinesTelemetryEvent(PipelineEntity pipelineEntity, String actionType) {
    String accountId = pipelineEntity.getAccountId();
    String orgId = pipelineEntity.getOrgIdentifier();
    String projectId = pipelineEntity.getProjectIdentifier();
    executorService.submit(() -> {
      try {
        List<EntityDetailProtoDTO> templateReferences = pipelineTemplateHelper.getTemplateReferencesForGivenYaml(
            accountId, orgId, projectId, pipelineEntity.getYaml());
        for (EntityDetailProtoDTO reference : templateReferences) {
          if (reference.hasTemplateRef()) {
            HashMap<String, Object> properties = new HashMap<>();
            properties.put(TEMPLATE_ID, reference.getTemplateRef().getIdentifier().getValue());
            properties.put(PIPELINE_ID, accountId);
            properties.put(ORG_ID, orgId);
            properties.put(PROJECT_ID, projectId);
            properties.put(TEMPLATE_REF_PIPELINE, actionType);
            properties.put(MODULE_NAME, "cd");
            telemetryReporter.sendTrackEvent(TEMPLATE_REF_PIPELINE, null, accountId, properties,
                Collections.singletonMap(AMPLITUDE, true), io.harness.telemetry.Category.GLOBAL);
          }
        }
      } catch (Exception ex) {
        log.error(
            format(
                "Exception while sending telemetry event for template ref by pipeline. accountId: %s, orgId: %s, projectId: %s, pipelineId: %s",
                accountId, orgId, projectId, pipelineEntity.getIdentifier()),
            ex);
      }
    });
  }

  public static InvalidYamlException buildInvalidYamlException(String errorMessage, String pipelineYaml) {
    YamlSchemaErrorWrapperDTO errorWrapperDTO =
        YamlSchemaErrorWrapperDTO.builder()
            .schemaErrors(
                Collections.singletonList(YamlSchemaErrorDTO.builder().message(errorMessage).fqn("$.pipeline").build()))
            .build();
    return new InvalidYamlException(errorMessage, errorWrapperDTO, pipelineYaml);
  }

  public String importPipelineFromRemote(
      String accountId, String orgIdentifier, String projectIdentifier, boolean applyRepoAllowListFilter) {
    GitEntityInfo gitEntityInfo = GitAwareContextHelper.getGitRequestParamsInfo();
    Scope scope = Scope.of(accountId, orgIdentifier, projectIdentifier);
    GitContextRequestParams gitContextRequestParams = GitContextRequestParams.builder()
                                                          .branchName(gitEntityInfo.getBranch())
                                                          .connectorRef(gitEntityInfo.getConnectorRef())
                                                          .filePath(gitEntityInfo.getFilePath())
                                                          .repoName(gitEntityInfo.getRepoName())
                                                          .applyRepoAllowListFilter(applyRepoAllowListFilter)
                                                          .build();
    return gitAwareEntityHelper.fetchYAMLFromRemote(scope, gitContextRequestParams, Collections.emptyMap());
  }

  public static void checkAndThrowMismatchInImportedPipelineMetadata(String orgIdentifier, String projectIdentifier,
      String pipelineIdentifier, PipelineImportRequestDTO pipelineImportRequest, String importedPipeline,
      String pipelineVersion) {
    if (EmptyPredicate.isEmpty(importedPipeline)) {
      String errorMessage = PipelineCRUDErrorResponse.errorMessageForEmptyYamlOnGit(
          orgIdentifier, projectIdentifier, pipelineIdentifier, GitAwareContextHelper.getBranchInRequest());
      throw PMSPipelineServiceHelper.buildInvalidYamlException(errorMessage, importedPipeline);
    }
    // TODO (prashant) : Check with the team
    switch (pipelineVersion) {
      case HarnessYamlVersion.V1:
        return;
      default:
        checkAndThrowMismatchInImportedPipelineMetadataInternal(
            orgIdentifier, projectIdentifier, pipelineIdentifier, pipelineImportRequest, importedPipeline);
    }
  }

  public String getRepoUrlAndCheckForFileUniqueness(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, String pipelineIdentifier, Boolean isForceImport) {
    GitEntityInfo gitEntityInfo = GitAwareContextHelper.getGitRequestParamsInfo();
    String repoURL = gitAwareEntityHelper.getRepoUrl(accountIdentifier, orgIdentifier, projectIdentifier);

    if (Boolean.TRUE.equals(isForceImport)) {
      log.info("Importing YAML forcefully with Pipeline Id: {}, RepoURl: {}, FilePath: {}", pipelineIdentifier, repoURL,
          gitEntityInfo.getFilePath());
    } else if (isAlreadyImported(accountIdentifier, repoURL, gitEntityInfo.getFilePath())) {
      String error = "The Requested YAML with Pipeline Id: " + pipelineIdentifier + ", RepoURl: " + repoURL
          + ", FilePath: " + gitEntityInfo.getFilePath() + " has already been imported.";
      throw new DuplicateFileImportException(error);
    }
    return repoURL;
  }

  private boolean isAlreadyImported(String accountIdentifier, String repoURL, String filePath) {
    Long totalInstancesOfYAML = pmsPipelineRepository.countFileInstances(accountIdentifier, repoURL, filePath);
    return totalInstancesOfYAML > 0;
  }

  private PipelineEntity updatePipelineInfoInternal(PipelineEntity pipelineEntity) throws IOException {
    FilterCreatorMergeServiceResponse filtersAndStageCount = filterCreatorMergeService.getPipelineInfo(
        FilterCreationParams.builder().pipelineEntity(pipelineEntity).build());
    PipelineEntity newEntity = pipelineEntity.withStageCount(filtersAndStageCount.getStageCount())
                                   .withStageNames(filtersAndStageCount.getStageNames());
    newEntity.getFilters().clear();
    try {
      if (isNotEmpty(filtersAndStageCount.getFilters())) {
        filtersAndStageCount.getFilters().forEach(
            (key, value)
                -> newEntity.getFilters().put(key, isNotEmpty(value) ? Document.parse(value) : Document.parse("{}")));
      }

      if (isNotEmpty(pipelineEntity.getTemplateModules())) {
        for (String module : pipelineEntity.getTemplateModules()) {
          if (!newEntity.getFilters().containsKey(module)) {
            newEntity.getFilters().put(module, Document.parse("{}"));
          }
        }
      }
    } catch (Exception e) {
      log.error("Unable to parse the Filter value", e);
    }
    return newEntity;
  }

  public Criteria getPipelineMetadataV2Criteria(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String pipelineIdentifier) {
    return Criteria.where(PipelineMetadataV2Keys.accountIdentifier)
        .is(accountIdentifier)
        .and(PipelineMetadataV2Keys.orgIdentifier)
        .is(orgIdentifier)
        .and(PipelineMetadataV2Keys.projectIdentifier)
        .is(projectIdentifier)
        .and(PipelineMetadataV2Keys.identifier)
        .is(pipelineIdentifier);
  }
  public Update getPipelineUpdateForInlineToRemote(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, MoveConfigOperationDTO moveConfigDTO) {
    Update update = new Update();
    update.set(PipelineEntityKeys.repo, moveConfigDTO.getRepoName());
    update.set(PipelineEntityKeys.storeType, StoreType.REMOTE);
    update.set(PipelineEntityKeys.filePath, moveConfigDTO.getFilePath());
    update.set(PipelineEntityKeys.connectorRef, moveConfigDTO.getConnectorRef());
    update.set(PipelineEntityKeys.repoURL,
        gitAwareEntityHelper.getRepoUrl(accountIdentifier, orgIdentifier, projectIdentifier));
    return update;
  }
  public Update getPipelineUpdateForRemoteToInline() {
    Update update = new Update();
    update.unset(PipelineEntityKeys.repo);
    update.unset(PipelineEntityKeys.filePath);
    update.unset(PipelineEntityKeys.connectorRef);
    update.unset(PipelineEntityKeys.repoURL);
    update.set(PipelineEntityKeys.storeType, StoreType.INLINE);
    return update;
  }

  public void checkThatTheModuleExists(String module) {
    if (isNotEmpty(module)
        && isEmpty(ModuleType.getModules()
                       .stream()
                       .filter(moduleType -> moduleType.name().equalsIgnoreCase(module))
                       .collect(Collectors.toList()))) {
      throw NestedExceptionUtils.hintWithExplanationException(format("Invalid module type [%s]", module),
          format("Please select the correct module type %s", ModuleType.getModules()),
          new InvalidRequestException(format("Invalid module type [%s]", module)));
    }
  }

  public void computePipelineReferences(PipelineEntity pipelineEntity) {
    pipelineSetupUsageCreationHelper.submitTask(
        FilterCreationParams.builder()
            .pipelineEntity(pipelineEntity)
            .filterCreationGitMetadata(FilterCreationGitMetadata.builder()
                                           .branch(GitAwareContextHelper.getBranchFromGitContext())
                                           .repo(pipelineEntity.getRepo())
                                           .isGitDefaultBranch(true)
                                           .build())
            .build());
  }

  public void setPermittedPipelines(
      String accountId, String orgId, String projectId, Criteria criteria, String pipelineIdentifierKey) {
    /*
    If user is having all pipeline view permission, we do not need to check for individual pipeline view permission
     */
    if (!pmsPipelineService.validateViewPermission(accountId, orgId, projectId)) {
      List<String> allPipelineIdentifiers = pmsPipelineService.listAllIdentifiers(criteria);

      List<String> permittedPipelineIdentifiers =
          pmsPipelineService.getPermittedPipelineIdentifier(accountId, orgId, projectId, allPipelineIdentifiers);

      criteria.and(pipelineIdentifierKey).in(permittedPipelineIdentifiers);
    }
  }

  public String preProcessPipelineYaml(String yaml) {
    YamlPreProcessor preProcessor = yamlPreProcessorFactory.getProcessorInstance(HarnessYamlVersion.V1);
    if (preProcessor != null) {
      yaml = YamlUtils.writeYamlString(preProcessor.preProcess(yaml).getPreprocessedJsonNode());
    }
    return yaml;
  }
}
