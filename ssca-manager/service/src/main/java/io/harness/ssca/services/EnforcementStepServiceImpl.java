/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ssca.services;

import io.harness.repositories.SBOMComponentRepo;
import io.harness.serializer.JsonUtils;
import io.harness.spec.server.ssca.v1.model.Artifact;
import io.harness.spec.server.ssca.v1.model.EnforceSbomRequestBody;
import io.harness.spec.server.ssca.v1.model.EnforceSbomResponseBody;
import io.harness.spec.server.ssca.v1.model.EnforcementSummaryResponse;
import io.harness.spec.server.ssca.v1.model.PolicyViolation;
import io.harness.ssca.enforcement.ExecutorRegistry;
import io.harness.ssca.entities.ArtifactEntity;
import io.harness.ssca.entities.EnforcementResultEntity;
import io.harness.ssca.entities.EnforcementSummaryEntity;
import io.harness.ssca.entities.NormalizedSBOMComponentEntity;

import com.google.inject.Inject;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.ws.rs.NotFoundException;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

public class EnforcementStepServiceImpl implements EnforcementStepService {
  @Inject ArtifactService artifactService;
  @Inject ExecutorRegistry executorRegistry;
  @Inject RuleEngineService ruleEngineService;
  @Inject EnforcementSummaryService enforcementSummaryService;
  @Inject EnforcementResultService enforcementResultService;
  @Inject SBOMComponentRepo sbomComponentRepo;

  @Override
  public EnforceSbomResponseBody enforceSbom(
      String accountId, String orgIdentifier, String projectIdentifier, EnforceSbomRequestBody body, String authToken) {
    String artifactId =
        artifactService.generateArtifactId(body.getArtifact().getRegistryUrl(), body.getArtifact().getName());
    ArtifactEntity artifactEntity =
        artifactService
            .getArtifact(accountId, orgIdentifier, projectIdentifier, artifactId,
                Sort.by(ArtifactEntity.ArtifactEntityKeys.createdOn).descending())
            .orElseThrow(()
                             -> new NotFoundException(
                                 String.format("Artifact with image name [%s] and registry Url [%s] is not found",
                                     body.getArtifact().getName(), body.getArtifact().getRegistryUrl())));

    String regoPolicy =
        ruleEngineService.getPolicy(accountId, orgIdentifier, projectIdentifier, body.getPolicyFileId());
    Page<NormalizedSBOMComponentEntity> entities =
        sbomComponentRepo.findByAccountIdAndOrgIdentifierAndProjectIdentifierAndOrchestrationId(accountId,
            orgIdentifier, projectIdentifier, artifactEntity.getOrchestrationId(),
            PageRequest.of(0, Integer.MAX_VALUE));
    Map<String, Object> requestMap = new HashMap<>();
    requestMap.put("rego", regoPolicy);
    requestMap.put("input", entities.get().collect(Collectors.toList()));
    String requestBody = JsonUtils.asJson(requestMap);
    String response = getHttpResponse(requestBody, authToken);
    Map<String, Object> responseMap1 = JsonUtils.asMap(response);
    List<Map<String, Object>> outputMapList = (List<Map<String, Object>>) responseMap1.get("output");
    List<Map<String, Object>> expressionsMapList =
        (List<Map<String, Object>>) ((Map<String, Object>) outputMapList.get(0)).get("expressions");
    Map<String, Object> expressionsMap = (Map<String, Object>) (expressionsMapList.get(0));
    Map<String, Object> valueMap = (Map<String, Object>) expressionsMap.get("value");
    Map<String, Object> sbomMap = (Map<String, Object>) valueMap.get("sbom");
    List<List<Violation>> allowListViolationsList = (List<List<Violation>>) sbomMap.get("allow_list_violations");
    List<List<Violation>> denyListViolationsList = (List<List<Violation>>) sbomMap.get("deny_list_violations");
    List<EnforcementResultEntity> denyListResult = new ArrayList<>();
    List<EnforcementResultEntity> allowListResult = new ArrayList<>();
    for (List violations : allowListViolationsList) {
      Violation current = getViolation((Map<String, Object>) violations.get(0));
      current.violations.forEach(currentArtifactId -> {
        EnforcementResultEntity currentResult = EnforcementResultEntity.builder()
                                                    .artifactId(currentArtifactId)
                                                    .enforcementID(body.getEnforcementId())
                                                    .accountId(accountId)
                                                    .violationType(current.type)
                                                    .violationDetails(current.rule.toString())
                                                    .build();
        allowListResult.add(currentResult);
      });
    }
    for (List<Violation> violations : denyListViolationsList) {
      Violation current = getViolation((Map<String, Object>) violations.get(0));
      current.violations.forEach(currentArtifactId -> {
        EnforcementResultEntity currentResult = EnforcementResultEntity.builder()
                                                    .artifactId(currentArtifactId)
                                                    .enforcementID(body.getEnforcementId())
                                                    .accountId(accountId)
                                                    .violationType(current.type)
                                                    .violationDetails(current.rule.toString())
                                                    .build();
        denyListResult.add(currentResult);
      });
    }

    //    HttpClient.
    //    List<EnforcementResultEntity> denyListResult = engine.executeRules();
    //
    //    engine.setRules(ruleDTO.getAllowList());
    //    List<EnforcementResultEntity> allowListResult = engine.executeRules();
    //
    String status = enforcementSummaryService.persistEnforcementSummary(
        body.getEnforcementId(), denyListResult, allowListResult, artifactEntity, body.getPipelineExecutionId());

    EnforceSbomResponseBody responseBody = new EnforceSbomResponseBody();
    responseBody.setEnforcementId(body.getEnforcementId());
    responseBody.setStatus(status);

    return responseBody;
  }

  @Override
  public EnforcementSummaryResponse getEnforcementSummary(
      String accountId, String orgIdentifier, String projectIdentifier, String enforcementId) {
    EnforcementSummaryEntity enforcementSummary =
        enforcementSummaryService.getEnforcementSummary(accountId, orgIdentifier, projectIdentifier, enforcementId)
            .orElseThrow(()
                             -> new NotFoundException(String.format(
                                 "Enforcement with enforcementIdentifier [%s] is not found", enforcementId)));

    return new EnforcementSummaryResponse()
        .enforcementId(enforcementSummary.getEnforcementId())
        .artifact(new Artifact()
                      .id(enforcementSummary.getArtifact().getArtifactId())
                      .name(enforcementSummary.getArtifact().getName())
                      .type(enforcementSummary.getArtifact().getType())
                      .registryUrl(enforcementSummary.getArtifact().getUrl())
                      .tag(enforcementSummary.getArtifact().getTag())

                )
        .allowListViolationCount(enforcementSummary.getAllowListViolationCount())
        .denyListViolationCount(enforcementSummary.getDenyListViolationCount())
        .status(enforcementSummary.getStatus());
  }

  @Override
  public Page<PolicyViolation> getPolicyViolations(String accountId, String orgIdentifier, String projectIdentifier,
      String enforcementId, String searchText, Pageable pageable) {
    return enforcementResultService
        .getPolicyViolations(accountId, orgIdentifier, projectIdentifier, enforcementId, searchText, pageable)
        .map(enforcementResultEntity
            -> new PolicyViolation()
                   .enforcementId(enforcementResultEntity.getEnforcementID())
                   .account(enforcementResultEntity.getAccountId())
                   .org(enforcementResultEntity.getOrgIdentifier())
                   .project(enforcementResultEntity.getProjectIdentifier())
                   .artifactId(enforcementResultEntity.getArtifactId())
                   .imageName(enforcementResultEntity.getImageName())
                   .purl(enforcementResultEntity.getPurl())
                   .orchestrationId(enforcementResultEntity.getOrchestrationID())
                   .license(enforcementResultEntity.getLicense())
                   .tag(enforcementResultEntity.getTag())
                   .supplier(enforcementResultEntity.getSupplier())
                   .supplierType(enforcementResultEntity.getSupplierType())
                   .name(enforcementResultEntity.getName())
                   .version(enforcementResultEntity.getVersion())
                   .packageManager(enforcementResultEntity.getPackageManager())
                   .violationType(enforcementResultEntity.getViolationType())
                   .violationDetails(enforcementResultEntity.getViolationDetails()));
  }

  private String getHttpResponse(String requestPayload, String authToken) {
    try {
      HttpRequest request2 =
          HttpRequest.newBuilder()
              .uri(new URI(
                  "https://qa.harness.io/gateway/pm/api/v1/evaluate?accountIdentifier=-k53qRQAQ1O7DBLb9ACnjQ&orgIdentifier=default&projectIdentifier=CloudWatch"))
              .header("content-type", "application/json")
              .header("accept", "application/json")
              .header("Authorization", authToken)
              .POST(HttpRequest.BodyPublishers.ofString(requestPayload))
              .build();
      HttpResponse<String> response =
          HttpClient.newBuilder().build().send(request2, HttpResponse.BodyHandlers.ofString());
      return response.body();
    } catch (Exception ex) {
    }
    return null;
  }

  @NoArgsConstructor
  @AllArgsConstructor
  @Builder(toBuilder = true)
  public static class Violation {
    List<String> violations;
    Object rule;
    String type;
  }

  private static Violation getViolation(Map<String, Object> map) {
    return Violation.builder()
        .violations((List<String>) map.get("violations"))
        .type((String) map.get("type"))
        .rule(map.get("rule"))
        .build();
  }
}
