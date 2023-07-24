/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.pipeline.governance.service;
import static io.harness.pms.contracts.governance.ExpansionPlacementStrategy.APPEND;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.FeatureName;
import io.harness.data.structure.EmptyPredicate;
import io.harness.engine.GovernanceService;
import io.harness.engine.utils.OpaPolicyEvaluationHelper;
import io.harness.exception.InvalidRequestException;
import io.harness.gitsync.beans.StoreType;
import io.harness.gitsync.scm.beans.ScmGitMetaData;
import io.harness.governance.GovernanceMetadata;
import io.harness.governance.PolicySetMetadata;
import io.harness.opaclient.model.OpaConstants;
import io.harness.opaclient.model.PipelineGovernanceGitConfig;
import io.harness.pms.contracts.governance.ExpansionRequestMetadata;
import io.harness.pms.contracts.governance.ExpansionResponseBatch;
import io.harness.pms.contracts.governance.ExpansionResponseProto;
import io.harness.pms.gitsync.PmsGitSyncHelper;
import io.harness.pms.governance.ExpansionRequest;
import io.harness.pms.governance.ExpansionRequestsExtractor;
import io.harness.pms.governance.ExpansionsMerger;
import io.harness.pms.governance.JsonExpander;
import io.harness.pms.pipeline.PipelineEntity;
import io.harness.pms.utils.PipelineYamlHelper;
import io.harness.pms.yaml.PipelineVersion;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.serializer.JsonUtils;
import io.harness.utils.PmsFeatureFlagService;

import com.google.inject.Inject;
import com.google.protobuf.ByteString;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true,
    components = {HarnessModuleComponent.CDS_PIPELINE, HarnessModuleComponent.CDS_TEMPLATE_LIBRARY})
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@Slf4j
public class PipelineGovernanceServiceImpl implements PipelineGovernanceService {
  @Inject private final JsonExpander jsonExpander;
  @Inject private final ExpansionRequestsExtractor expansionRequestsExtractor;
  @Inject PmsFeatureFlagService pmsFeatureFlagService;
  @Inject private final PmsGitSyncHelper gitSyncHelper;

  @Inject private final GovernanceService governanceService;
  @Inject OpaPolicyEvaluationHelper opaPolicyEvaluationHelper;

  @Override
  public GovernanceMetadata validateGovernanceRules(String accountId, String orgIdentifier, String projectIdentifier,
      String branch, PipelineEntity pipelineEntity, String yamlWithResolvedTemplates) {
    String expandedPipelineJSON = fetchExpandedPipelineJSONFromYaml(
        pipelineEntity, yamlWithResolvedTemplates, branch, OpaConstants.OPA_EVALUATION_ACTION_SAVE);
    return governanceService.evaluateGovernancePolicies(expandedPipelineJSON, accountId, orgIdentifier,
        projectIdentifier, OpaConstants.OPA_EVALUATION_ACTION_SAVE, "", PipelineVersion.V0);
  }

  @Override
  public GovernanceMetadata validateGovernanceRulesAndThrowExceptionIfDenied(String accountId, String orgIdentifier,
      String projectIdentifier, String branch, PipelineEntity pipelineEntity, String yamlWithResolvedTemplates) {
    GovernanceMetadata governanceMetadata = validateGovernanceRules(
        accountId, orgIdentifier, projectIdentifier, branch, pipelineEntity, yamlWithResolvedTemplates);
    if (governanceMetadata.getDeny()) {
      List<String> denyingPolicySetIds = governanceMetadata.getDetailsList()
                                             .stream()
                                             .filter(PolicySetMetadata::getDeny)
                                             .map(PolicySetMetadata::getIdentifier)
                                             .collect(Collectors.toList());
      // todo: see if this can be changed to PolicyEvaluationFailureException, probably yes
      throw new InvalidRequestException(
          "Pipeline does not follow the Policies in these Policy Sets: " + denyingPolicySetIds);
    }
    return governanceMetadata;
  }

  @Override
  public String fetchExpandedPipelineJSONFromYaml(
      String accountId, String orgIdentifier, String projectIdentifier, String pipelineYaml, String action) {
    return getExpandedPipelineJSONFromYaml(
        accountId, orgIdentifier, projectIdentifier, pipelineYaml, null, null, action);
  }

  @Override
  public String fetchExpandedPipelineJSONFromYaml(
      PipelineEntity pipelineEntity, String pipelineYaml, String branch, String action) {
    return getExpandedPipelineJSONFromYaml(pipelineEntity.getAccountIdentifier(), pipelineEntity.getOrgIdentifier(),
        pipelineEntity.getProjectIdentifier(), pipelineYaml, branch, pipelineEntity, action);
  }

  private String getExpandedPipelineJSONFromYaml(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, String pipelineYaml, String branch, PipelineEntity pipelineEntity, String action) {
    if (!pmsFeatureFlagService.isEnabled(accountIdentifier, FeatureName.OPA_PIPELINE_GOVERNANCE)) {
      return null;
    }
    switch (PipelineYamlHelper.getVersion(pipelineYaml)) {
      case PipelineVersion.V1:
        return null;
      default:
        break;
    }
    if (!opaPolicyEvaluationHelper.shouldEvaluatePolicy(
            accountIdentifier, orgIdentifier, projectIdentifier, OpaConstants.OPA_EVALUATION_TYPE_PIPELINE, action)) {
      return null;
    }
    return getExpandedPipelineJSONFromYaml(
        accountIdentifier, orgIdentifier, projectIdentifier, pipelineYaml, branch, pipelineEntity);
  }

  @Override
  public String getExpandedPipelineJSONFromYaml(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, String pipelineYaml, String branch, PipelineEntity pipelineEntity) {
    long start = System.currentTimeMillis();
    ExpansionRequestMetadata expansionRequestMetadata =
        getRequestMetadata(accountIdentifier, orgIdentifier, projectIdentifier, pipelineYaml);

    Set<ExpansionRequest> expansionRequests = expansionRequestsExtractor.fetchExpansionRequests(pipelineYaml);
    Set<ExpansionResponseBatch> expansionResponseBatches =
        jsonExpander.fetchExpansionResponses(expansionRequests, expansionRequestMetadata);

    if (null != pipelineEntity) {
      addGitDetailsToExpandedYaml(expansionResponseBatches, pipelineEntity, branch);
    }
    String mergeExpansions = ExpansionsMerger.mergeExpansions(pipelineYaml, expansionResponseBatches);
    log.info("[PMS_GOVERNANCE] Pipeline Json Expansion took {}ms for projectId {}, orgId {}, accountId {}",
        System.currentTimeMillis() - start, projectIdentifier, orgIdentifier, accountIdentifier);
    return mergeExpansions;
  }

  ExpansionRequestMetadata getRequestMetadata(
      String accountId, String orgIdentifier, String projectIdentifier, String pipelineYaml) {
    ByteString gitSyncBranchContextBytes = gitSyncHelper.getGitSyncBranchContextBytesThreadLocal();
    ExpansionRequestMetadata.Builder expansionRequestMetadataBuilder =
        ExpansionRequestMetadata.newBuilder()
            .setAccountId(accountId)
            .setOrgId(orgIdentifier)
            .setProjectId(projectIdentifier)
            .setYaml(ByteString.copyFromUtf8(pipelineYaml));
    if (gitSyncBranchContextBytes != null) {
      expansionRequestMetadataBuilder.setGitSyncBranchContext(gitSyncBranchContextBytes);
    }
    return expansionRequestMetadataBuilder.build();
  }

  void addGitDetailsToExpandedYaml(
      Set<ExpansionResponseBatch> expansionResponseBatches, PipelineEntity pipelineEntity, String branch) {
    if (pipelineEntity.getStoreType() != null && StoreType.REMOTE.equals(pipelineEntity.getStoreType())) {
      // Adding GitConfig to expanded Yaml
      expansionResponseBatches.add(getGitDetailsAsExecutionResponse(pipelineEntity, branch));
    }
  }

  boolean checkIfRemotePipeline(ScmGitMetaData scmGitMetaData) {
    return !EmptyPredicate.isEmpty(scmGitMetaData.getBranchName());
  }

  ExpansionResponseBatch getGitDetailsAsExecutionResponse(ScmGitMetaData scmGitMetaData) {
    return getExpansionResponseBatch(getPipelineGovernanceGitConfigInfo(scmGitMetaData));
  }

  ExpansionResponseBatch getGitDetailsAsExecutionResponse(PipelineEntity pipelineEntity, String branch) {
    return getExpansionResponseBatch(
        getPipelineGovernanceGitConfigInfo(branch, pipelineEntity.getFilePath(), pipelineEntity.getRepo()));
  }

  ExpansionResponseBatch getExpansionResponseBatch(PipelineGovernanceGitConfig pipelineGovernanceGitConfig) {
    String gitDetailsJson = JsonUtils.asJson(pipelineGovernanceGitConfig);
    ExpansionResponseProto gitConfig = ExpansionResponseProto.newBuilder()
                                           .setFqn(YAMLFieldNameConstants.PIPELINE)
                                           .setKey(PipelineGovernanceGitConfig.GIT_CONFIG)
                                           .setValue(gitDetailsJson)
                                           .setSuccess(true)
                                           .setPlacement(APPEND)
                                           .build();

    return ExpansionResponseBatch.newBuilder()
        .addAllExpansionResponseProto(Collections.singletonList(gitConfig))
        .build();
  }

  PipelineGovernanceGitConfig getPipelineGovernanceGitConfigInfo(ScmGitMetaData scmGitMetaData) {
    return PipelineGovernanceGitConfig.builder()
        .branch(scmGitMetaData.getBranchName())
        .filePath(scmGitMetaData.getFilePath())
        .repoName(scmGitMetaData.getRepoName())
        .build();
  }
  PipelineGovernanceGitConfig getPipelineGovernanceGitConfigInfo(String branch, String filePath, String repo) {
    return PipelineGovernanceGitConfig.builder().branch(branch).filePath(filePath).repoName(repo).build();
  }
}
