/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ssca.services;

import io.harness.entities.Instance;
import io.harness.exception.InvalidArgumentsException;
import io.harness.pipeline.remote.PipelineServiceClient;
import io.harness.remote.client.NGRestUtils;
import io.harness.repositories.CdInstanceSummaryRepo;
import io.harness.repositories.EnforcementSummaryRepo;
import io.harness.serializer.JsonUtils;
import io.harness.spec.server.ssca.v1.model.ArtifactDeploymentViewRequestBody;
import io.harness.ssca.beans.EnvType;
import io.harness.ssca.beans.SLSAVerificationSummary;
import io.harness.ssca.beans.SLSAVerificationSummary.SLSAVerificationSummaryBuilder;
import io.harness.ssca.entities.ArtifactEntity;
import io.harness.ssca.entities.CdInstanceSummary;
import io.harness.ssca.entities.CdInstanceSummary.CdInstanceSummaryKeys;
import io.harness.ssca.entities.EnforcementSummaryEntity.EnforcementSummaryEntityKeys;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import java.time.Instant;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.query.Criteria;

@Slf4j
public class CdInstanceSummaryServiceImpl implements CdInstanceSummaryService {
  @Inject CdInstanceSummaryRepo cdInstanceSummaryRepo;
  @Inject ArtifactService artifactService;
  @Inject EnforcementSummaryRepo enforcementSummaryRepo;
  @Inject PipelineServiceClient pipelineServiceClient;

  private static String IDENTIFIER = "identifier";
  private static String STEP_TYPE = "stepType";

  private static String EXECUTION_GRAPH = "executionGraph";
  private static String SLSA_VERIFICATION_STEP_ID = "SlsaVerification";
  private static String NODE_MAP = "nodeMap";
  private static String OUTCOMES = "outcomes";

  private static String PIPELINE_EXECUTION_SUMMARY = "pipelineExecutionSummary";
  private static String PIPELINE_NAME = "name";
  private static String RUN_SEQUENCE = "runSequence";
  private static String EXECUTION_TRIGGER_INFO = "executionTriggerInfo";
  private static String TRIGGER_TYPE = "triggerType";

  private static String STEP_ARTIFACTS = "stepArtifacts";
  private static String PROVENANCE_ARTIFACTS = "provenanceArtifacts";
  private static String POLICY_OUTPUT = "policyOutput";
  private static String STATUS = "status";

  private static String PUBLISHED_IMAGES = "publishedImageArtifacts";
  private static String IMAGE_NAME = "imageName";
  private static String TAG = "tag";

  @Override
  public boolean upsertInstance(Instance instance) {
    if (Objects.isNull(instance.getPrimaryArtifact())
        || Objects.isNull(instance.getPrimaryArtifact().getArtifactIdentity())
        || Objects.isNull(instance.getPrimaryArtifact().getArtifactIdentity().getImage())) {
      log.info(
          String.format("Instance skipped because of missing artifact identity, {InstanceId: %s}", instance.getId()));
      return true;
    }

    ArtifactEntity artifact =
        artifactService.getArtifactByCorrelationId(instance.getAccountIdentifier(), instance.getOrgIdentifier(),
            instance.getProjectIdentifier(), instance.getPrimaryArtifact().getArtifactIdentity().getImage());
    if (Objects.isNull(artifact)) {
      log.info(String.format(
          "Instance skipped because of missing correlated artifactEntity, {InstanceId: %s}", instance.getId()));
      return true;
    }

    CdInstanceSummary cdInstanceSummary = getCdInstanceSummary(instance.getAccountIdentifier(),
        instance.getOrgIdentifier(), instance.getProjectIdentifier(),
        instance.getPrimaryArtifact().getArtifactIdentity().getImage(), instance.getEnvIdentifier());

    if (Objects.nonNull(cdInstanceSummary)) {
      JsonNode rootNode = getPmsExecutionSummary(instance);
      cdInstanceSummary.getInstanceIds().add(instance.getId());
      cdInstanceSummary.setSlsaVerificationSummary(getSlsaVerificationSummary(rootNode, instance, artifact));
      cdInstanceSummary = setPipelineDetails(cdInstanceSummary, rootNode, instance);
      cdInstanceSummaryRepo.save(cdInstanceSummary);
    } else {
      CdInstanceSummary newCdInstanceSummary = createInstanceSummary(instance, artifact);
      artifactService.updateArtifactEnvCount(artifact, newCdInstanceSummary.getEnvType(), 1);
      cdInstanceSummaryRepo.save(newCdInstanceSummary);
    }
    return true;
  }

  @Override
  public boolean removeInstance(Instance instance) {
    if (Objects.isNull(instance.getPrimaryArtifact())
        || Objects.isNull(instance.getPrimaryArtifact().getArtifactIdentity())
        || Objects.isNull(instance.getPrimaryArtifact().getArtifactIdentity().getImage())) {
      log.info(
          String.format("Instance skipped because of missing artifact identity, {InstanceId: %s}", instance.getId()));
      return true;
    }
    CdInstanceSummary cdInstanceSummary = getCdInstanceSummary(instance.getAccountIdentifier(),
        instance.getOrgIdentifier(), instance.getProjectIdentifier(),
        instance.getPrimaryArtifact().getArtifactIdentity().getImage(), instance.getEnvIdentifier());

    if (Objects.nonNull(cdInstanceSummary)) {
      cdInstanceSummary.getInstanceIds().remove(instance.getId());
      if (cdInstanceSummary.getInstanceIds().isEmpty()) {
        ArtifactEntity artifact =
            artifactService.getArtifactByCorrelationId(instance.getAccountIdentifier(), instance.getOrgIdentifier(),
                instance.getProjectIdentifier(), instance.getPrimaryArtifact().getArtifactIdentity().getImage());
        artifactService.updateArtifactEnvCount(artifact, cdInstanceSummary.getEnvType(), -1);
        cdInstanceSummaryRepo.delete(cdInstanceSummary);
      } else {
        cdInstanceSummaryRepo.save(cdInstanceSummary);
      }
    }
    return true;
  }

  @Override
  public Page<CdInstanceSummary> getCdInstanceSummaries(String accountId, String orgIdentifier,
      String projectIdentifier, ArtifactEntity artifact, ArtifactDeploymentViewRequestBody filterBody,
      Pageable pageable) {
    Criteria criteria = Criteria.where(CdInstanceSummaryKeys.artifactCorrelationId)
                            .is(artifact.getArtifactCorrelationId())
                            .and(CdInstanceSummaryKeys.accountIdentifier)
                            .is(accountId)
                            .and(CdInstanceSummaryKeys.orgIdentifier)
                            .is(orgIdentifier)
                            .and(CdInstanceSummaryKeys.projectIdentifier)
                            .is(projectIdentifier);

    if (Objects.nonNull(filterBody)) {
      if (Objects.nonNull(filterBody.getEnvironment())) {
        Pattern pattern = Pattern.compile("[.]*" + filterBody.getEnvironment() + "[.]*");
        criteria.and(CdInstanceSummaryKeys.envName).regex(pattern);
      }
      if (Objects.nonNull(filterBody.getEnvironmentType())) {
        switch (filterBody.getEnvironmentType()) {
          case PROD:
            criteria.and(CdInstanceSummaryKeys.envType).is(EnvType.Production);
            break;
          case NONPROD:
            criteria.and(CdInstanceSummaryKeys.envType).is(EnvType.PreProduction);
            break;
          default:
            throw new InvalidArgumentsException(
                String.format("Unknown environment type filter: %s", filterBody.getEnvironmentType()));
        }
      }
      if (Objects.nonNull(filterBody.getPolicyViolation())) {
        Criteria enforcementSummaryCriteria = Criteria.where(EnforcementSummaryEntityKeys.accountId)
                                                  .is(accountId)
                                                  .and(EnforcementSummaryEntityKeys.orgIdentifier)
                                                  .is(orgIdentifier)
                                                  .and(EnforcementSummaryEntityKeys.projectIdentifier)
                                                  .is(projectIdentifier);

        switch (filterBody.getPolicyViolation()) {
          case ALLOW:
            enforcementSummaryCriteria.and(EnforcementSummaryEntityKeys.allowListViolationCount).gt(0);
            break;
          case DENY:
            enforcementSummaryCriteria.and(EnforcementSummaryEntityKeys.denyListViolationCount).gt(0);
            break;
          default:
            throw new InvalidArgumentsException(
                String.format("Unknown policy type filter: %s", filterBody.getPolicyViolation()));
        }

        List<String> pipelineExecutionIds = enforcementSummaryRepo.findAll(enforcementSummaryCriteria)
                                                .stream()
                                                .map(entity -> entity.getPipelineExecutionId())
                                                .collect(Collectors.toList());
        criteria.and(CdInstanceSummaryKeys.lastPipelineExecutionId).in(pipelineExecutionIds);
      }
    }

    return cdInstanceSummaryRepo.findAll(criteria, pageable);
  }

  @Override
  public CdInstanceSummary getCdInstanceSummary(String accountId, String orgIdentifier, String projectIdentifier,
      String artifactCorrelationId, String envIdentifier) {
    Criteria criteria = Criteria.where(CdInstanceSummaryKeys.artifactCorrelationId)
                            .is(artifactCorrelationId)
                            .and(CdInstanceSummaryKeys.accountIdentifier)
                            .is(accountId)
                            .and(CdInstanceSummaryKeys.orgIdentifier)
                            .is(orgIdentifier)
                            .and(CdInstanceSummaryKeys.projectIdentifier)
                            .is(projectIdentifier)
                            .and(CdInstanceSummaryKeys.envIdentifier)
                            .is(envIdentifier);

    return cdInstanceSummaryRepo.findOne(criteria);
  }

  @VisibleForTesting
  public CdInstanceSummary createInstanceSummary(Instance instance, ArtifactEntity artifact) {
    JsonNode rootNode = getPmsExecutionSummary(instance);

    CdInstanceSummary cdInstanceSummary =
        CdInstanceSummary.builder()
            .artifactCorrelationId(instance.getPrimaryArtifact().getArtifactIdentity().getImage())
            .accountIdentifier(instance.getAccountIdentifier())
            .orgIdentifier(instance.getOrgIdentifier())
            .projectIdentifier(instance.getProjectIdentifier())
            .slsaVerificationSummary(getSlsaVerificationSummary(rootNode, instance, artifact))
            .envIdentifier(instance.getEnvIdentifier())
            .envName(instance.getEnvName())
            .envType(EnvType.valueOf(instance.getEnvType().name()))
            .createdAt(Instant.now().toEpochMilli())
            .instanceIds(Collections.singleton(instance.getId()))
            .build();

    return setPipelineDetails(cdInstanceSummary, rootNode, instance);
  }

  private CdInstanceSummary setPipelineDetails(
      CdInstanceSummary cdInstanceSummary, JsonNode rootNode, Instance instance) {
    cdInstanceSummary.setLastPipelineName(
        getNodeValue(parseField(rootNode, PIPELINE_EXECUTION_SUMMARY, PIPELINE_NAME)));
    cdInstanceSummary.setLastPipelineExecutionName(instance.getLastPipelineExecutionName());
    cdInstanceSummary.setLastPipelineExecutionId(instance.getLastPipelineExecutionId());
    cdInstanceSummary.setSequenceId(getNodeValue(parseField(rootNode, PIPELINE_EXECUTION_SUMMARY, RUN_SEQUENCE)));
    cdInstanceSummary.setLastDeployedAt(instance.getLastDeployedAt());
    cdInstanceSummary.setLastDeployedById(instance.getLastDeployedById());
    cdInstanceSummary.setLastDeployedByName(instance.getLastDeployedByName());
    cdInstanceSummary.setTriggerType(
        getNodeValue(parseField(rootNode, PIPELINE_EXECUTION_SUMMARY, EXECUTION_TRIGGER_INFO, TRIGGER_TYPE)));
    return cdInstanceSummary;
  }

  private SLSAVerificationSummary getSlsaVerificationSummary(
      JsonNode rootNode, Instance instance, ArtifactEntity artifact) {
    SLSAVerificationSummaryBuilder slsaVerificationSummaryBuilder = SLSAVerificationSummary.builder();
    if (rootNode == null) {
      return slsaVerificationSummaryBuilder.build();
    }
    try {
      JsonNode executionNodeList = parseField(rootNode, EXECUTION_GRAPH, NODE_MAP);
      if (Objects.isNull(executionNodeList)) {
        return slsaVerificationSummaryBuilder.build();
      }
      for (JsonNode node : executionNodeList) {
        if (SLSA_VERIFICATION_STEP_ID.equals(getNodeValue(node.get(STEP_TYPE)))
            && getNodeValue(node.get(IDENTIFIER)) != null) {
          String fqnSlsaStepIdentifier = getFqnSlsaStepIdentifier(node);
          if (fqnSlsaStepIdentifier != null && isCorrelated(fqnSlsaStepIdentifier, node, artifact)) {
            JsonNode provenanceArtifactList =
                parseField(node, OUTCOMES, fqnSlsaStepIdentifier, STEP_ARTIFACTS, PROVENANCE_ARTIFACTS);
            JsonNode provenanceArtifact =
                Objects.nonNull(provenanceArtifactList) ? provenanceArtifactList.get(0) : null;
            slsaVerificationSummaryBuilder.hasSlsaVerification(true)
                .slsaPolicyOutcomeStatus(getNodeValue(parseField(node, OUTCOMES, POLICY_OUTPUT, STATUS)))
                .provenanceArtifact(provenanceArtifact);
            break;
          }
        }
      }
    } catch (Exception e) {
      log.error(String.format("Failed to extract SLSA Verification Data. Exception: %s", e));
    }
    return slsaVerificationSummaryBuilder.build();
  }

  private String getFqnSlsaStepIdentifier(JsonNode node) {
    JsonNode outcomes = parseField(node, OUTCOMES);
    if (outcomes == null) {
      return null;
    }
    Iterator<String> fields = outcomes.fieldNames();
    String identifier = getNodeValue(parseField(node, IDENTIFIER));
    while (fields.hasNext()) {
      String field = fields.next();
      if (field.startsWith("artifact") && field.endsWith(identifier)) {
        return field;
      }
    }
    return null;
  }

  private boolean isCorrelated(String fqnIdentifier, JsonNode node, ArtifactEntity artifact) {
    JsonNode publishedImageArtifacts = parseField(node, OUTCOMES, fqnIdentifier, STEP_ARTIFACTS, PUBLISHED_IMAGES);
    if (publishedImageArtifacts == null) {
      return false;
    }
    for (JsonNode artifactNode : publishedImageArtifacts) {
      if (artifact.getName().equals(getNodeValue(parseField(artifactNode, IMAGE_NAME)))
          && artifact.getTag().equals(getNodeValue(parseField(artifactNode, TAG)))) {
        return true;
      }
    }
    return false;
  }

  private JsonNode getPmsExecutionSummary(Instance instance) {
    JsonNode rootNode = null;
    try {
      Object pmsExecutionSummary = NGRestUtils.getResponse(pipelineServiceClient.getExecutionDetailV2(
          instance.getLastPipelineExecutionId(), instance.getAccountIdentifier(), instance.getOrgIdentifier(),
          instance.getProjectIdentifier(), instance.getStageSetupId()));
      rootNode = JsonUtils.asTree(pmsExecutionSummary);
    } catch (Exception e) {
      log.error(String.format("PMS Request Failed. Exception: %s", e));
    }
    return rootNode;
  }

  private JsonNode parseField(JsonNode rootNode, String... path) {
    for (String field : path) {
      if (rootNode == null) {
        return null;
      } else {
        rootNode = rootNode.get(field);
      }
    }
    return rootNode;
  }

  private String getNodeValue(JsonNode node) {
    if (Objects.isNull(node)) {
      return null;
    }
    return node.asText();
  }
}
