/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine;

import static io.harness.security.dto.PrincipalType.USER;

import io.harness.data.structure.EmptyPredicate;
import io.harness.data.structure.HarnessStringUtils;
import io.harness.governance.GovernanceMetadata;
import io.harness.governance.PolicyMetadata;
import io.harness.governance.PolicySetMetadata;
import io.harness.opaclient.OpaUtils;
import io.harness.opaclient.model.OpaConstants;
import io.harness.opaclient.model.OpaEvaluationResponseHolder;
import io.harness.opaclient.model.OpaPolicyEvaluationResponse;
import io.harness.opaclient.model.OpaPolicySetEvaluationResponse;
import io.harness.opaclient.model.PipelineOpaEvaluationContext;
import io.harness.opaclient.model.TemplateOpaEvaluationContext;
import io.harness.security.SourcePrincipalContextBuilder;
import io.harness.security.dto.UserPrincipal;
import io.harness.serializer.JsonUtils;

import com.google.common.collect.ImmutableMap;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import lombok.experimental.UtilityClass;

@UtilityClass
public class GovernanceServiceHelper {
  public String getEntityString(String accountId, String orgIdentifier, String projectIdentifier,
      String pipelineIdentifier) throws UnsupportedEncodingException {
    String entityStringRaw =
        String.format("accountIdentifier:%s/orgIdentifier:%s/projectIdentifier:%s/pipelineIdentifier:%s", accountId,
            orgIdentifier, projectIdentifier, pipelineIdentifier);
    return URLEncoder.encode(entityStringRaw, StandardCharsets.UTF_8.toString());
  }

  public String getEntityMetadataString(String pipelineIdentifier, String pipelineName, String planExecutionId)
      throws UnsupportedEncodingException {
    Map<String, String> metadataMap = ImmutableMap.<String, String>builder()
                                          .put("pipelineIdentifier", pipelineIdentifier)
                                          .put("entityName", pipelineName)
                                          .put("executionIdentifier", planExecutionId)
                                          .build();
    return URLEncoder.encode(JsonUtils.asJson(metadataMap), StandardCharsets.UTF_8.toString());
  }

  public GovernanceMetadata mapResponseToMetadata(OpaEvaluationResponseHolder response) {
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

  List<PolicySetMetadata> mapPolicySetMetadata(List<OpaPolicySetEvaluationResponse> policySetResponse) {
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
              .setDescription(HarnessStringUtils.emptyIfNull(setEvaluationResponse.getDescription()))
              .build());
    }
    return policySetMetadataList;
  }

  List<PolicyMetadata> mapPolicyMetadata(List<OpaPolicyEvaluationResponse> policyEvaluationResponses) {
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

  public PipelineOpaEvaluationContext createEvaluationContext(String yaml) throws IOException {
    return PipelineOpaEvaluationContext.builder()
        .pipeline(OpaUtils.extractObjectFromYamlString(yaml, OpaConstants.OPA_EVALUATION_TYPE_PIPELINE))
        .date(new Date())
        .build();
  }

  public String getUserIdentifier() {
    if (SourcePrincipalContextBuilder.getSourcePrincipal() == null
        || !USER.equals(SourcePrincipalContextBuilder.getSourcePrincipal().getType())) {
      return "";
    }
    UserPrincipal userPrincipal = (UserPrincipal) SourcePrincipalContextBuilder.getSourcePrincipal();
    return userPrincipal.getName();
  }

  public String getEntityMetadata(String templateName) throws UnsupportedEncodingException {
    Map<String, String> metadataMap = ImmutableMap.<String, String>builder().put("entityName", templateName).build();
    return URLEncoder.encode(JsonUtils.asJson(metadataMap), StandardCharsets.UTF_8.toString());
  }

  public TemplateOpaEvaluationContext createEvaluationContextTemplate(String yaml) throws IOException {
    return TemplateOpaEvaluationContext.builder()
        .template(OpaUtils.extractObjectFromYamlString(yaml, OpaConstants.OPA_EVALUATION_TYPE_TEMPLATE))
        .date(new Date())
        .build();
  }
}
