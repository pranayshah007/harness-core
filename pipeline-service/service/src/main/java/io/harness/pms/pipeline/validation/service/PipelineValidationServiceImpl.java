/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.pipeline.validation.service;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.gitaware.helper.GitAwareContextHelper;
import io.harness.gitsync.sdk.EntityGitDetails;
import io.harness.governance.GovernanceMetadata;
import io.harness.pms.pipeline.PipelineEntity;
import io.harness.pms.pipeline.governance.service.PipelineGovernanceServiceImpl;
import io.harness.pms.pipeline.service.PMSPipelineServiceHelper;
import io.harness.pms.pipeline.service.PMSYamlSchemaService;
import io.harness.pms.pipeline.service.PipelineCRUDErrorResponse;
import io.harness.pms.pipeline.validation.PipelineValidationResponse;
import io.harness.pms.yaml.PipelineVersion;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlUtils;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@Singleton
@Slf4j
public class PipelineValidationServiceImpl implements PipelineValidationService {
  @Inject private final PMSYamlSchemaService pmsYamlSchemaService;
  @Inject private final PipelineGovernanceServiceImpl pipelineGovernanceService;

  @Override
  public boolean validateYaml(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      String yamlWithTemplatesResolved, String pipelineYaml, String harnessVersion) {
    if (harnessVersion.equals(PipelineVersion.V0)) {
      checkIfRootNodeIsPipeline(pipelineYaml);
    }
    pmsYamlSchemaService.validateYamlSchema(
        accountIdentifier, orgIdentifier, projectIdentifier, YamlUtils.readAsJsonNode(yamlWithTemplatesResolved));
    // validate unique fqn in resolveTemplateRefsInPipeline
    pmsYamlSchemaService.validateUniqueFqn(yamlWithTemplatesResolved);
    return true;
  }

  @Override
  public void validateYamlWithUnresolvedTemplates(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, String pipelineYaml, String harnessVersion) {
    if (Objects.equals(harnessVersion, PipelineVersion.V0)) {
      checkIfRootNodeIsPipeline(pipelineYaml);
    }
    pmsYamlSchemaService.validateYamlSchema(
        accountIdentifier, orgIdentifier, projectIdentifier, YamlUtils.readAsJsonNode(pipelineYaml));
    pmsYamlSchemaService.validateUniqueFqn(pipelineYaml);
  }

  @Override
  public PipelineValidationResponse validateYamlAndGovernanceRules(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, String yamlWithTemplatesResolved, String resolvedYamlWithTemplateRefs,
      PipelineEntity pipelineEntity) {
    validateYaml(accountIdentifier, orgIdentifier, projectIdentifier, yamlWithTemplatesResolved,
        pipelineEntity.getYaml(), pipelineEntity.getHarnessVersion());

    String branch = GitAwareContextHelper.getBranchInRequest();
    GovernanceMetadata governanceMetadata = pipelineGovernanceService.validateGovernanceRulesAndThrowExceptionIfDenied(
        accountIdentifier, orgIdentifier, projectIdentifier, branch, pipelineEntity, resolvedYamlWithTemplateRefs);
    return PipelineValidationResponse.builder().governanceMetadata(governanceMetadata).build();
  }

  @Override
  public PipelineValidationResponse validateYamlAndGetGovernanceMetadata(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, String yamlWithTemplatesResolved, String resolvedYamlWithTemplateRefs,
      PipelineEntity pipelineEntity) {
    validateYaml(accountIdentifier, orgIdentifier, projectIdentifier, yamlWithTemplatesResolved,
        pipelineEntity.getYaml(), pipelineEntity.getHarnessVersion());

    String branch = GitAwareContextHelper.getBranchInRequest();
    GovernanceMetadata governanceMetadata = pipelineGovernanceService.validateGovernanceRules(
        accountIdentifier, orgIdentifier, projectIdentifier, branch, pipelineEntity, resolvedYamlWithTemplateRefs);
    return PipelineValidationResponse.builder().governanceMetadata(governanceMetadata).build();
  }

  @VisibleForTesting
  void checkIfRootNodeIsPipeline(String pipelineYaml) {
    EntityGitDetails gitDetails = GitAwareContextHelper.getEntityGitDetailsFromScmGitMetadata();
    String branch = gitDetails.getBranch();
    String filePath = gitDetails.getFilePath();
    YamlField pipelineYamlField;

    try {
      pipelineYamlField = YamlUtils.readTree(pipelineYaml);
    } catch (IOException e) {
      String errorMessage = PipelineCRUDErrorResponse.errorMessageForNotAYAMLFile(branch, filePath);
      throw PMSPipelineServiceHelper.buildInvalidYamlException(errorMessage, pipelineYaml);
    }
    if (pipelineYamlField.getNode().getField(YAMLFieldNameConstants.PIPELINE) == null) {
      String errorMessage = PipelineCRUDErrorResponse.errorMessageForNotAPipelineYAML(branch, filePath);
      throw PMSPipelineServiceHelper.buildInvalidYamlException(errorMessage, pipelineYaml);
    }
  }
}
