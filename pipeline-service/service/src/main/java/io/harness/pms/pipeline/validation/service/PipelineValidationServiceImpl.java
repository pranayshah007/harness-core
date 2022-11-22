package io.harness.pms.pipeline.validation.service;

import io.harness.gitaware.helper.GitAwareContextHelper;
import io.harness.gitsync.sdk.EntityGitDetails;
import io.harness.pms.pipeline.service.PMSPipelineServiceHelper;
import io.harness.pms.pipeline.service.PMSYamlSchemaService;
import io.harness.pms.pipeline.service.PipelineCRUDErrorResponse;
import io.harness.pms.yaml.PipelineVersion;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlUtils;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@Singleton
@Slf4j
public class PipelineValidationServiceImpl implements PipelineValidationService {
  @Inject private final PMSYamlSchemaService pmsYamlSchemaService;

  @Override
  public boolean validateYaml(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      String yamlWithTemplatesResolved, String pipelineYaml, String harnessVersion) {
    if (harnessVersion.equals(PipelineVersion.V0)) {
      checkIfRootNodeIsPipeline(pipelineYaml);
    }
    pmsYamlSchemaService.validateYamlSchema(
        accountIdentifier, orgIdentifier, projectIdentifier, yamlWithTemplatesResolved);
    // validate unique fqn in resolveTemplateRefsInPipeline
    pmsYamlSchemaService.validateUniqueFqn(yamlWithTemplatesResolved);
    return true;
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
