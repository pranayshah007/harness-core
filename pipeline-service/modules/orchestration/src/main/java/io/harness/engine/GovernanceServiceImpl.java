/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.exception.InvalidRequestException;
import io.harness.governance.GovernanceMetadata;
import io.harness.network.SafeHttpCall;
import io.harness.opaclient.OpaServiceClient;
import io.harness.opaclient.model.OpaConstants;
import io.harness.opaclient.model.OpaEvaluationResponseHolder;
import io.harness.opaclient.model.PipelineOpaEvaluationContext;
import io.harness.pms.yaml.PipelineVersion;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlUtils;
import io.harness.utils.PmsFeatureFlagService;

import com.google.inject.Inject;
import java.io.IOException;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.PIPELINE)
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
public class GovernanceServiceImpl implements GovernanceService {
  private final PmsFeatureFlagService pmsFeatureFlagService;
  private final OpaServiceClient opaServiceClient;

  @Override
  public GovernanceMetadata evaluateGovernancePolicies(String expandedJson, String accountId, String orgIdentifier,
      String projectIdentifier, String action, String planExecutionId, String pipelineVersion) {
    pipelineVersion = isEmpty(pipelineVersion) ? PipelineVersion.V0 : pipelineVersion;
    switch (pipelineVersion) {
      case PipelineVersion.V1:
        return GovernanceMetadata.newBuilder().setDeny(false).build();
      default:
        break;
    }
    long startTs = System.currentTimeMillis();
    try {
      if (!pmsFeatureFlagService.isEnabled(accountId, FeatureName.OPA_PIPELINE_GOVERNANCE)) {
        return GovernanceMetadata.newBuilder()
            .setDeny(false)
            .setMessage(
                String.format("FF: [%s] is disabled for account: [%s]", FeatureName.OPA_PIPELINE_GOVERNANCE, accountId))
            .build();
      }
      if (isEmpty(expandedJson)) {
        return GovernanceMetadata.newBuilder().setDeny(false).build();
      }
      log.info("Initiating policy check for pipeline with expanded JSON:\n" + expandedJson);

      PipelineOpaEvaluationContext context;
      try {
        context = GovernanceServiceHelper.createEvaluationContext(expandedJson);
      } catch (IOException ex) {
        log.error("Could not create OPA evaluation context", ex);
        return GovernanceMetadata.newBuilder()
            .setDeny(true)
            .setMessage(String.format("Could not create OPA context: [%s]", ex.getMessage()))
            .build();
      }

      OpaEvaluationResponseHolder response;
      try {
        YamlField pipelineField = YamlUtils.readTree(expandedJson);
        String pipelineIdentifier =
            pipelineField.getNode().getField(YAMLFieldNameConstants.PIPELINE).getNode().getIdentifier();
        String pipelineName = pipelineField.getNode().getField(YAMLFieldNameConstants.PIPELINE).getNode().getName();
        String entityString =
            GovernanceServiceHelper.getEntityString(accountId, orgIdentifier, projectIdentifier, pipelineIdentifier);
        String entityMetadata =
            GovernanceServiceHelper.getEntityMetadataString(pipelineIdentifier, pipelineName, planExecutionId);
        String userIdentifier = GovernanceServiceHelper.getUserIdentifier();

        response = SafeHttpCall.executeWithExceptions(
            opaServiceClient.evaluateWithCredentials(OpaConstants.OPA_EVALUATION_TYPE_PIPELINE, accountId,
                orgIdentifier, projectIdentifier, action, entityString, entityMetadata, userIdentifier, context));
      } catch (Exception ex) {
        log.error("Exception while evaluating OPA rules", ex);
        throw new InvalidRequestException("Exception while evaluating OPA rules: " + ex.getMessage(), ex);
      }

      return GovernanceServiceHelper.mapResponseToMetadata(response);
    } finally {
      log.info("[PMS_Governance_Metadata] Time taken to evaluate governance policies: {}ms",
          System.currentTimeMillis() - startTs);
    }
  }
}
