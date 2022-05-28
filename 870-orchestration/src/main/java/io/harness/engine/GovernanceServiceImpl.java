/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine;

import static io.harness.security.dto.PrincipalType.USER;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.data.structure.EmptyPredicate;
import io.harness.data.structure.HarnessStringUtils;
import io.harness.exception.InvalidRequestException;
import io.harness.network.SafeHttpCall;
import io.harness.opaclient.OpaServiceClient;
import io.harness.opaclient.OpaUtils;
import io.harness.opaclient.model.OpaConstants;
import io.harness.opaclient.model.OpaEvaluationResponseHolder;
import io.harness.opaclient.model.OpaPolicyEvaluationResponse;
import io.harness.opaclient.model.OpaPolicySetEvaluationResponse;
import io.harness.opaclient.model.PipelineOpaEvaluationContext;
import io.harness.pms.PmsFeatureFlagService;
import io.harness.pms.contracts.governance.GovernanceMetadata;
import io.harness.pms.contracts.governance.PolicyMetadata;
import io.harness.pms.contracts.governance.PolicySetMetadata;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlUtils;
import io.harness.security.SourcePrincipalContextBuilder;
import io.harness.security.dto.UserPrincipal;
import io.harness.serializer.JsonUtils;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
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
      String projectIdentifier, String action, String planExecutionId) {
    long startTs = System.currentTimeMillis();
    try {
      if (!pmsFeatureFlagService.isEnabled(accountId, FeatureName.OPA_PIPELINE_GOVERNANCE)) {
        return GovernanceMetadata.newBuilder()
            .setDeny(false)
            .setMessage(
                String.format("FF: [%s] is disabled for account: [%s]", FeatureName.OPA_PIPELINE_GOVERNANCE, accountId))
            .build();
      }
      log.info("Initiating policy check for pipeline with expanded JSON:\n" + expandedJson);

      PipelineOpaEvaluationContext context;
      try {
        context = createEvaluationContext(expandedJson);
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
        String entityString = getEntityString(accountId, orgIdentifier, projectIdentifier, pipelineIdentifier);
        String entityMetadata = getEntityMetadataString(pipelineIdentifier, pipelineName, planExecutionId);
        String userIdentifier = getUserIdentifier();

        response = SafeHttpCall.executeWithExceptions(
            opaServiceClient.evaluateWithCredentials(OpaConstants.OPA_EVALUATION_TYPE_PIPELINE, accountId,
                orgIdentifier, projectIdentifier, action, entityString, entityMetadata, userIdentifier, context));
      } catch (Exception ex) {
        log.error("Exception while evaluating OPA rules", ex);
        throw new InvalidRequestException("Exception while evaluating OPA rules: " + ex.getMessage(), ex);
      }

      return mapResponseToMetadata(response);
    } finally {
      log.info("[PMS_Governance_Metadata] Time taken to evaluate governance policies: {}ms",
          System.currentTimeMillis() - startTs);
    }
  }

  private String getEntityString(String accountId, String orgIdentifier, String projectIdentifier,
      String pipelineIdentifier) throws UnsupportedEncodingException {
    String entityStringRaw =
        String.format("accountIdentifier:%s/orgIdentifier:%s/projectIdentifier:%s/pipelineIdentifier:%s", accountId,
            orgIdentifier, projectIdentifier, pipelineIdentifier);
    return URLEncoder.encode(entityStringRaw, StandardCharsets.UTF_8.toString());
  }

  private String getEntityMetadataString(String pipelineIdentifier, String pipelineName, String planExecutionId)
      throws UnsupportedEncodingException {
    Map<String, String> metadataMap = ImmutableMap.<String, String>builder()
                                          .put("pipelineIdentifier", pipelineIdentifier)
                                          .put("entityName", pipelineName)
                                          .put("executionIdentifier", planExecutionId)
                                          .build();
    return URLEncoder.encode(JsonUtils.asJson(metadataMap), StandardCharsets.UTF_8.toString());
  }

  private GovernanceMetadata mapResponseToMetadata(OpaEvaluationResponseHolder response) {
    return GovernanceMetadata.newBuilder()
        .setId(HarnessStringUtils.emptyIfNull(response.getId()))
        .setDeny(OpaConstants.OPA_STATUS_ERROR.equals(HarnessStringUtils.emptyIfNull(response.getStatus())))
        .setTimestamp(System.currentTimeMillis())
        .addAllDetails(mapPolicySetMetadata(response.getDetails()))
        .setStatus(HarnessStringUtils.emptyIfNull(response.getStatus()))
        .setAccountId(HarnessStringUtils.emptyIfNull(response.getAccount_id()))
        .setOrgId(HarnessStringUtils.emptyIfNull(response.getOrg_id()))
        .setProjectId(HarnessStringUtils.emptyIfNull(response.getProject_id()))
        .setEntity(HarnessStringUtils.emptyIfNull(response.getEntity()))
        .setType(HarnessStringUtils.emptyIfNull(response.getType()))
        .setAction(HarnessStringUtils.emptyIfNull(response.getAction()))
        .setCreated(response.getCreated())
        .build();
  }

  private List<PolicySetMetadata> mapPolicySetMetadata(List<OpaPolicySetEvaluationResponse> policySetResponse) {
    if (EmptyPredicate.isEmpty(policySetResponse)) {
      return Collections.emptyList();
    }
    List<PolicySetMetadata> policySetMetadataList = new ArrayList<>();
    for (OpaPolicySetEvaluationResponse setEvaluationResponse : policySetResponse) {
      policySetMetadataList.add(
          PolicySetMetadata.newBuilder()
              .setDeny(OpaConstants.OPA_STATUS_ERROR.equals(
                  HarnessStringUtils.emptyIfNull(setEvaluationResponse.getStatus())))
              .setStatus(HarnessStringUtils.emptyIfNull(setEvaluationResponse.getStatus()))
              .setPolicySetName(HarnessStringUtils.emptyIfNull(setEvaluationResponse.getName()))
              .addAllPolicyMetadata(mapPolicyMetadata(setEvaluationResponse.getDetails()))
              .setIdentifier(HarnessStringUtils.emptyIfNull(setEvaluationResponse.getIdentifier()))
              .setCreated(setEvaluationResponse.getCreated())
              .setAccountId(HarnessStringUtils.emptyIfNull(setEvaluationResponse.getAccount_id()))
              .setOrgId(HarnessStringUtils.emptyIfNull(setEvaluationResponse.getOrg_id()))
              .setProjectId(HarnessStringUtils.emptyIfNull(setEvaluationResponse.getProject_id()))
              .build());
    }
    return policySetMetadataList;
  }

  private List<PolicyMetadata> mapPolicyMetadata(List<OpaPolicyEvaluationResponse> policyEvaluationResponses) {
    if (EmptyPredicate.isEmpty(policyEvaluationResponses)) {
      return Collections.emptyList();
    }
    List<PolicyMetadata> policyMetadataList = new ArrayList<>();
    for (OpaPolicyEvaluationResponse policyEvaluationResponse : policyEvaluationResponses) {
      policyMetadataList.add(
          PolicyMetadata.newBuilder()
              .setPolicyName(HarnessStringUtils.emptyIfNull(policyEvaluationResponse.getPolicy().getName()))
              .setIdentifier(HarnessStringUtils.emptyIfNull(policyEvaluationResponse.getPolicy().getIdentifier()))
              .setAccountId(HarnessStringUtils.emptyIfNull(policyEvaluationResponse.getPolicy().getAccount_id()))
              .setOrgId(HarnessStringUtils.emptyIfNull(policyEvaluationResponse.getPolicy().getOrg_id()))
              .setProjectId(HarnessStringUtils.emptyIfNull(policyEvaluationResponse.getPolicy().getProject_id()))
              .setCreated(policyEvaluationResponse.getPolicy().getCreated())
              .setUpdated(policyEvaluationResponse.getPolicy().getUpdated())
              .setStatus(HarnessStringUtils.emptyIfNull(policyEvaluationResponse.getStatus()))
              .setError(HarnessStringUtils.emptyIfNull(policyEvaluationResponse.getError()))
              .setSeverity(policyEvaluationResponse.getStatus())
              .addAllDenyMessages(policyEvaluationResponse.getDeny_messages())
              .build());
    }
    return policyMetadataList;
  }

  private PipelineOpaEvaluationContext createEvaluationContext(String yaml) throws IOException {
    return PipelineOpaEvaluationContext.builder()
        .pipeline(OpaUtils.extractObjectFromYamlString(yaml, OpaConstants.OPA_EVALUATION_TYPE_PIPELINE))
        .date(new Date())
        .build();
  }

  private String getUserIdentifier() {
    if (SourcePrincipalContextBuilder.getSourcePrincipal() == null
        || !USER.equals(SourcePrincipalContextBuilder.getSourcePrincipal().getType())) {
      return "";
    }
    UserPrincipal userPrincipal = (UserPrincipal) SourcePrincipalContextBuilder.getSourcePrincipal();
    return userPrincipal.getName();
  }
}
