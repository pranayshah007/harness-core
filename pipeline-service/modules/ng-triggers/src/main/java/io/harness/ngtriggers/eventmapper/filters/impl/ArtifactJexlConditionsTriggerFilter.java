/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */
package io.harness.ngtriggers.eventmapper.filters.impl;
import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.exception.WingsException.USER;
import static io.harness.ngtriggers.beans.response.TriggerEventResponse.FinalStatus.NO_MATCHING_TRIGGER_FOR_JEXL_CONDITIONS;

import static org.apache.commons.lang3.StringUtils.isBlank;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.exception.TriggerException;
import io.harness.ngtriggers.beans.config.NGTriggerConfigV2;
import io.harness.ngtriggers.beans.dto.TriggerDetails;
import io.harness.ngtriggers.beans.dto.eventmapping.UnMatchedTriggerInfo;
import io.harness.ngtriggers.beans.dto.eventmapping.WebhookEventMappingResponse;
import io.harness.ngtriggers.beans.dto.eventmapping.WebhookEventMappingResponse.WebhookEventMappingResponseBuilder;
import io.harness.ngtriggers.beans.response.TriggerEventResponse;
import io.harness.ngtriggers.beans.source.NGTriggerSourceV2;
import io.harness.ngtriggers.beans.source.NGTriggerSpecV2;
import io.harness.ngtriggers.beans.source.artifact.ArtifactTriggerConfig;
import io.harness.ngtriggers.beans.source.artifact.ManifestTriggerConfig;
import io.harness.ngtriggers.beans.source.artifact.MultiRegionArtifactTriggerConfig;
import io.harness.ngtriggers.eventmapper.filters.TriggerFilter;
import io.harness.ngtriggers.eventmapper.filters.dto.FilterRequestData;
import io.harness.ngtriggers.expressions.TriggerExpressionEvaluator;
import io.harness.ngtriggers.helpers.TriggerEventResponseHelper;
import io.harness.ngtriggers.mapper.NGTriggerElementMapper;
import io.harness.pms.contracts.triggers.ArtifactData;
import io.harness.yaml.utils.JsonPipelineUtils;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_TRIGGERS})
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
@Singleton
@OwnedBy(PIPELINE)
public class ArtifactJexlConditionsTriggerFilter implements TriggerFilter {
  private NGTriggerElementMapper ngTriggerElementMapper;

  @Override
  public WebhookEventMappingResponse applyFilter(FilterRequestData filterRequestData) {
    WebhookEventMappingResponseBuilder mappingResponseBuilder = initWebhookEventMappingResponse(filterRequestData);
    List<TriggerDetails> matchedTriggers = new ArrayList<>();
    List<UnMatchedTriggerInfo> unMatchedTriggersInfoList = new ArrayList<>();

    for (TriggerDetails trigger : filterRequestData.getDetails()) {
      try {
        NGTriggerConfigV2 ngTriggerConfig = trigger.getNgTriggerConfigV2();
        if (ngTriggerConfig == null) {
          ngTriggerConfig = ngTriggerElementMapper.toTriggerConfigV2(trigger.getNgTriggerEntity().getYaml());
        }

        TriggerDetails triggerDetails = TriggerDetails.builder()
                                            .ngTriggerConfigV2(ngTriggerConfig)
                                            .ngTriggerEntity(trigger.getNgTriggerEntity())
                                            .build();
        if (checkTriggerEligibility(filterRequestData, triggerDetails)) {
          matchedTriggers.add(triggerDetails);
        } else {
          UnMatchedTriggerInfo unMatchedTriggerInfo =
              UnMatchedTriggerInfo.builder()
                  .unMatchedTriggers(triggerDetails)
                  .finalStatus(TriggerEventResponse.FinalStatus.TRIGGER_DID_NOT_MATCH_ARTIFACT_JEXL_CONDITION)
                  .message(triggerDetails.getNgTriggerEntity().getIdentifier()
                      + " didn't match condition for artifact jexl condition")
                  .build();
          unMatchedTriggersInfoList.add(unMatchedTriggerInfo);
        }
      } catch (Exception e) {
        log.error(getTriggerSkipMessage(trigger.getNgTriggerEntity()), e);
      }
    }

    mappingResponseBuilder.unMatchedTriggerInfoList(unMatchedTriggersInfoList);
    if (isEmpty(matchedTriggers)) {
      log.info("No trigger matched polling event after condition evaluation:");
      mappingResponseBuilder.failedToFindTrigger(true)
          .webhookEventResponse(TriggerEventResponseHelper.toResponse(NO_MATCHING_TRIGGER_FOR_JEXL_CONDITIONS,
              filterRequestData.getWebhookPayloadData().getOriginalEvent(), null, null,
              "No Trigger matched conditions for metadata for Account: " + filterRequestData.getAccountId(), null))
          .build();
    } else {
      addDetails(mappingResponseBuilder, filterRequestData, matchedTriggers);
    }
    return mappingResponseBuilder.build();
  }

  private boolean checkTriggerEligibility(FilterRequestData filterRequestData, TriggerDetails triggerDetails) {
    String triggerJexlCondition = "";

    NGTriggerConfigV2 ngTriggerConfigV2 = triggerDetails.getNgTriggerConfigV2();
    NGTriggerSourceV2 source = ngTriggerConfigV2.getSource();
    NGTriggerSpecV2 spec = source.getSpec();
    if (ManifestTriggerConfig.class.isAssignableFrom(spec.getClass())) {
      return true;
    } else if (ArtifactTriggerConfig.class.isAssignableFrom(spec.getClass())) {
      ArtifactTriggerConfig artifactTriggerConfig = (ArtifactTriggerConfig) spec;
      triggerJexlCondition = artifactTriggerConfig.getSpec().fetchJexlArtifactConditions();
    } else if (MultiRegionArtifactTriggerConfig.class.isAssignableFrom(spec.getClass())) {
      MultiRegionArtifactTriggerConfig multiRegionArtifactTriggerConfig = (MultiRegionArtifactTriggerConfig) spec;
      triggerJexlCondition = multiRegionArtifactTriggerConfig.getJexlCondition();
    }

    if (isEmpty(triggerJexlCondition)) {
      return true;
    }

    Map<String, String> metadata = new HashMap<>();
    if (filterRequestData.getPollingResponse().getBuildInfo().getMetadataCount() != 0) {
      metadata = filterRequestData.getPollingResponse().getBuildInfo().getMetadata(0).getMetadataMap();
    }
    String build = filterRequestData.getPollingResponse().getBuildInfo().getVersions(0);
    ArtifactData artifactData = ArtifactData.newBuilder().putAllMetadata(metadata).setBuild(build).build();
    String jsonMetadata = "";
    jsonMetadata = JsonPipelineUtils.getJsonString(metadata);
    return checkIfJexlConditionsMatch(artifactData, jsonMetadata, triggerJexlCondition);
  }

  public boolean checkIfJexlConditionsMatch(ArtifactData artifactData, String payload, String jexlExpression) {
    if (isBlank(jexlExpression)) {
      return true;
    }

    TriggerExpressionEvaluator triggerExpressionEvaluator =
        new TriggerExpressionEvaluator(null, artifactData, Collections.emptyList(), payload);
    Object result = triggerExpressionEvaluator.evaluateExpression(jexlExpression);
    if (result != null && Boolean.class.isAssignableFrom(result.getClass())) {
      return (Boolean) result;
    }

    StringBuilder errorMsg = new StringBuilder(128);
    if (result == null) {
      errorMsg.append("Expression ")
          .append(jexlExpression)
          .append(" was evaluated to null. Expected type is Boolean")
          .toString();
    } else {
      errorMsg.append("Expression ")
          .append(jexlExpression)
          .append(":  was evaluated to type: ")
          .append(result.getClass())
          .append(". Expected type is Boolean")
          .toString();
    }

    throw new TriggerException(errorMsg.toString(), USER);
  }
}
