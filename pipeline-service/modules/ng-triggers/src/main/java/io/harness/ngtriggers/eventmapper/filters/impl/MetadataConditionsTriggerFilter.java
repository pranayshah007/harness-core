/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */
package io.harness.ngtriggers.eventmapper.filters.impl;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.ngtriggers.beans.response.TriggerEventResponse.FinalStatus.NO_MATCHING_TRIGGER_FOR_METADATA_CONDITIONS;

import static org.apache.commons.lang3.StringUtils.EMPTY;

import io.harness.annotations.dev.OwnedBy;
import io.harness.expression.common.ExpressionMode;
import io.harness.ngtriggers.beans.config.NGTriggerConfigV2;
import io.harness.ngtriggers.beans.dto.TriggerDetails;
import io.harness.ngtriggers.beans.dto.eventmapping.WebhookEventMappingResponse;
import io.harness.ngtriggers.beans.dto.eventmapping.WebhookEventMappingResponse.WebhookEventMappingResponseBuilder;
import io.harness.ngtriggers.beans.source.NGTriggerSourceV2;
import io.harness.ngtriggers.beans.source.NGTriggerSpecV2;
import io.harness.ngtriggers.beans.source.artifact.ArtifactTriggerConfig;
import io.harness.ngtriggers.beans.source.artifact.ManifestTriggerConfig;
import io.harness.ngtriggers.beans.source.webhook.v2.TriggerEventDataCondition;
import io.harness.ngtriggers.conditionchecker.ConditionEvaluator;
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

@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
@Singleton
@OwnedBy(PIPELINE)
public class MetadataConditionsTriggerFilter implements TriggerFilter {
  private NGTriggerElementMapper ngTriggerElementMapper;

  @Override
  public WebhookEventMappingResponse applyFilter(FilterRequestData filterRequestData) {
    WebhookEventMappingResponseBuilder mappingResponseBuilder = initWebhookEventMappingResponse(filterRequestData);
    List<TriggerDetails> matchedTriggers = new ArrayList<>();

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
        }
      } catch (Exception e) {
        log.error(getTriggerSkipMessage(trigger.getNgTriggerEntity()), e);
      }
    }

    if (isEmpty(matchedTriggers)) {
      log.info("No trigger matched polling event after condition evaluation:");
      mappingResponseBuilder.failedToFindTrigger(true)
          .webhookEventResponse(TriggerEventResponseHelper.toResponse(NO_MATCHING_TRIGGER_FOR_METADATA_CONDITIONS,
              filterRequestData.getWebhookPayloadData().getOriginalEvent(), null, null,
              "No Trigger matched conditions for metadata for Account: " + filterRequestData.getAccountId(), null))
          .build();
    } else {
      addDetails(mappingResponseBuilder, filterRequestData, matchedTriggers);
    }
    return mappingResponseBuilder.build();
  }

  private boolean checkTriggerEligibility(FilterRequestData filterRequestData, TriggerDetails triggerDetails) {
    List<TriggerEventDataCondition> triggerMetadataConditions = null;

    NGTriggerConfigV2 ngTriggerConfigV2 = triggerDetails.getNgTriggerConfigV2();
    NGTriggerSourceV2 source = ngTriggerConfigV2.getSource();
    NGTriggerSpecV2 spec = source.getSpec();
    if (ManifestTriggerConfig.class.isAssignableFrom(spec.getClass())) {
      return true;
    } else if (ArtifactTriggerConfig.class.isAssignableFrom(spec.getClass())) {
      ArtifactTriggerConfig artifactTriggerConfig = (ArtifactTriggerConfig) spec;
      triggerMetadataConditions = artifactTriggerConfig.getSpec().fetchMetaDataConditions();
    }

    if (isEmpty(triggerMetadataConditions)) {
      return true;
    }

    Map<String, String> metadata = new HashMap<>();
    if (filterRequestData.getPollingResponse().getBuildInfo().getMetadataCount() != 0) {
      metadata = filterRequestData.getPollingResponse().getBuildInfo().getMetadata(0).getMetadataMap();
    }
    String build = filterRequestData.getPollingResponse().getBuildInfo().getVersions(0);

    boolean allConditionsMatched = true;
    String input;
    String standard;
    String operator;
    ArtifactData artifactData = ArtifactData.newBuilder().putAllMetadata(metadata).setBuild(build).build();
    String jsonMetadata = "";
    jsonMetadata = JsonPipelineUtils.getJsonString(metadata);
    TriggerExpressionEvaluator expressionEvaluator =
        new TriggerExpressionEvaluator(null, artifactData, Collections.emptyList(), jsonMetadata);
    for (TriggerEventDataCondition condition : triggerMetadataConditions) {
      input = condition.getKey();
      standard = condition.getValue();
      operator = condition.getOperator() != null ? condition.getOperator().getValue() : EMPTY;
      input = expressionEvaluator.renderExpression(input, ExpressionMode.RETURN_ORIGINAL_EXPRESSION_IF_UNRESOLVED);
      allConditionsMatched = ConditionEvaluator.evaluate(input, standard, operator);
      if (!allConditionsMatched) {
        break;
      }
    }
    return allConditionsMatched;
  }
}
