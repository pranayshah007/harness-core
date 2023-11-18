/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.governance.services;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.idp.common.JacksonUtils.convert;
import static io.harness.remote.client.NGRestUtils.getGeneralResponse;
import static io.harness.remote.client.NGRestUtils.getResponse;

import static java.lang.String.format;

import io.harness.cdstage.remote.CDStageConfigClient;
import io.harness.clients.BackstageCatalogEntitiesByRefsRequest;
import io.harness.clients.BackstageResourceClient;
import io.harness.idp.backstagebeans.BackstageCatalogEntity;
import io.harness.idp.backstagebeans.BackstageCatalogEntityTypes;
import io.harness.idp.governance.beans.ScorecardExpandedValue;
import io.harness.idp.governance.beans.ServiceScorecards;
import io.harness.idp.governance.beans.ServiceScorecardsMapper;
import io.harness.idp.scorecard.scores.service.ScoreService;
import io.harness.ng.core.dto.CDStageMetaDataDTO;
import io.harness.ng.core.dto.CdDeployStageMetadataRequestDTO;
import io.harness.pms.contracts.governance.ExpansionPlacementStrategy;
import io.harness.pms.contracts.governance.ExpansionRequestMetadata;
import io.harness.pms.sdk.core.governance.ExpandedValue;
import io.harness.pms.sdk.core.governance.ExpansionResponse;
import io.harness.pms.sdk.core.governance.JsonExpansionHandler;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlNode;
import io.harness.spec.server.idp.v1.model.ScorecardSummaryInfo;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@Slf4j
public class ScorecardExpansionHandler implements JsonExpansionHandler {
  private static final String COMPONENT = "component";
  private static final String NAMESPACE = "default";
  private final ObjectMapper mapper =
      new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
  @Inject BackstageResourceClient backstageResourceClient;
  @Inject ScoreService scoreService;
  @Inject CDStageConfigClient cdStageConfigClient;

  @Override
  public ExpansionResponse expand(JsonNode fieldValue, ExpansionRequestMetadata metadata, String fqn) {
    String accountId = metadata.getAccountId();
    String orgId = metadata.getOrgId();
    String projectId = metadata.getProjectId();
    String stageIdentifier = fieldValue.get("identifier").asText();
    String pipeline = metadata.getYaml().toStringUtf8();
    log.info(format("Process started for IDP Scorecard expansion for pipeline: [%s], account: [%s], project:[%s]",
        pipeline, accountId, projectId));
    CDStageMetaDataDTO cdStageMetaDataDTO = getCDStageResponse(stageIdentifier, pipeline);
    if (cdStageMetaDataDTO == null || isInvalidResponse(cdStageMetaDataDTO)) {
      String errorMessage =
          format("Invalid Response for Service Ref and Environment Ref for pipeline: [%s], account: [%s], project:[%s]",
              pipeline, accountId, projectId);
      log.error(errorMessage);
      return ExpansionResponse.builder().success(false).errorMessage(errorMessage).build();
    }
    log.info("ServiceRef from CDStageMetaDataDTO response: " + cdStageMetaDataDTO.getServiceRef());

    if (isEmpty(cdStageMetaDataDTO.getServiceEnvRefList())) {
      cdStageMetaDataDTO = setServiceEnvRef(cdStageMetaDataDTO);
    }

    Map<String, ServiceScorecards> serviceScores = new HashMap<>();
    for (CDStageMetaDataDTO.ServiceEnvRef serviceEnvRef : cdStageMetaDataDTO.getServiceEnvRefList()) {
      String serviceRef = serviceEnvRef.getServiceRef();
      String serviceId = constructServiceId(truncateName(serviceRef));
      String serviceIdExtended = constructServiceId(truncateName(orgId + "-" + projectId + "-" + serviceRef));
      log.info("Before calling backstage API, Constructed names: " + serviceId + " & " + serviceIdExtended);
      Object entitiesResponse = getGeneralResponse(backstageResourceClient.getCatalogEntitiesByRefs(accountId,
          BackstageCatalogEntitiesByRefsRequest.builder().entityRefs(List.of(serviceId, serviceIdExtended)).build()));
      List<BackstageCatalogEntity> entities = convert(mapper, entitiesResponse, BackstageCatalogEntity.class);
      log.info("Response from backstage. Size: " + entities.size());
      String uuid = getUUId(entities, orgId, projectId);
      if (uuid == null) {
        continue;
      }
      log.info("UUID after filter: " + uuid);
      List<ScorecardSummaryInfo> scorecardSummaryInfos = scoreService.getScoresSummaryForAnEntity(accountId, uuid);
      for (ScorecardSummaryInfo scorecardSummaryInfo : scorecardSummaryInfos) {
        serviceScores.put(serviceRef, ServiceScorecardsMapper.toDTO(scorecardSummaryInfo));
      }
    }

    ExpandedValue value = ScorecardExpandedValue.builder().serviceScores(serviceScores).build();
    log.info("Construct json value: " + value.toJson());
    return ExpansionResponse.builder()
        .success(true)
        .key(value.getKey())
        .value(value)
        .fqn(fqn + YamlNode.PATH_SEP + YAMLFieldNameConstants.SPEC)
        .placement(ExpansionPlacementStrategy.APPEND)
        .build();
  }

  private CDStageMetaDataDTO getCDStageResponse(String stageIdentifier, String pipeline) {
    try {
      return getResponse(cdStageConfigClient.getCDStageMetaData(
          CdDeployStageMetadataRequestDTO.builder().stageIdentifier(stageIdentifier).pipelineYaml(pipeline).build()));
    } catch (Exception e) {
      String errorMessage =
          format("Exception occurred while fetching service and environment reference for pipeline: [%s]", pipeline);
      log.error(errorMessage, e);
      return null;
    }
  }

  private boolean isInvalidResponse(CDStageMetaDataDTO cdStageMetaDataDTO) {
    return isEmpty(cdStageMetaDataDTO.getServiceEnvRefList())
        && (Objects.isNull(cdStageMetaDataDTO.getServiceRef())
            || Objects.isNull(cdStageMetaDataDTO.getEnvironmentRef()));
  }

  private CDStageMetaDataDTO setServiceEnvRef(CDStageMetaDataDTO cdStageMetaDataDTO) {
    return CDStageMetaDataDTO.builder()
        .environmentRef(cdStageMetaDataDTO.getEnvironmentRef())
        .serviceRef(cdStageMetaDataDTO.getServiceRef())
        .serviceEnvRef(CDStageMetaDataDTO.ServiceEnvRef.builder()
                           .environmentRef(cdStageMetaDataDTO.getEnvironmentRef())
                           .serviceRef(cdStageMetaDataDTO.getServiceRef())
                           .build())
        .build();
  }

  private String constructServiceId(String serviceRef) {
    return COMPONENT + ":" + NAMESPACE + "/" + serviceRef;
  }

  private String truncateName(String harnessEntityName) {
    if (harnessEntityName.length() > 63) {
      return StringUtils.truncate(harnessEntityName, 60) + "---";
    }
    return harnessEntityName;
  }

  private String getUUId(List<BackstageCatalogEntity> entities, String orgId, String projectId) {
    BackstageCatalogEntity catalogEntity =
        entities.stream()
            .filter(entity
                -> entity != null
                    && Objects.equals(BackstageCatalogEntityTypes.getEntityDomain(entity), truncateName(orgId))
                    && Objects.equals(BackstageCatalogEntityTypes.getEntitySystem(entity), truncateName(projectId)))
            .findFirst()
            .orElse(null);
    if (catalogEntity != null) {
      return catalogEntity.getMetadata().getUid();
    }
    return null;
  }
}
