/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ngtriggers.utils;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.exception.WingsException.USER_SRE;
import static io.harness.ngtriggers.beans.source.NGTriggerType.ARTIFACT;
import static io.harness.ngtriggers.beans.source.NGTriggerType.MANIFEST;
import static io.harness.ngtriggers.beans.source.NGTriggerType.MULTI_REGION_ARTIFACT;
import static io.harness.ngtriggers.beans.source.NGTriggerType.WEBHOOK;

import io.harness.exception.InvalidRequestException;
import io.harness.exception.TriggerException;
import io.harness.ngtriggers.beans.dto.TriggerDetails;
import io.harness.ngtriggers.beans.entity.NGTriggerEntity;
import io.harness.ngtriggers.beans.source.NGTriggerType;
import io.harness.ngtriggers.buildtriggers.helpers.BuildTriggerHelper;
import io.harness.ngtriggers.buildtriggers.helpers.dtos.BuildTriggerOpsData;
import io.harness.ngtriggers.buildtriggers.helpers.generator.GeneratorFactory;
import io.harness.ngtriggers.buildtriggers.helpers.generator.PollingItemGenerator;
import io.harness.ngtriggers.helpers.TriggerHelper;
import io.harness.ngtriggers.mapper.NGTriggerElementMapper;
import io.harness.polling.contracts.Category;
import io.harness.polling.contracts.PollingItem;
import io.harness.polling.contracts.Qualifier;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Singleton
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
public class PollingSubscriptionHelper {
  private final BuildTriggerHelper buildTriggerHelper;
  private final NGTriggerElementMapper ngTriggerElementMapper;
  private final GeneratorFactory generatorFactory;

  public List<PollingItem> generatePollingItems(NGTriggerEntity ngTriggerEntity) {
    NGTriggerType triggerType = ngTriggerEntity.getType();
    if (triggerType != ARTIFACT && triggerType != MANIFEST && triggerType != WEBHOOK
        && triggerType != MULTI_REGION_ARTIFACT) {
      throw new InvalidRequestException(
          "Polling items generation is not support for trigger type " + triggerType.toString());
    }

    try {
      TriggerDetails triggerDetails = ngTriggerElementMapper.toTriggerDetails(ngTriggerEntity);
      Optional<String> pipelineYml = buildTriggerHelper.fetchResolvedTemplatesPipelineForTrigger(triggerDetails);
      if (!pipelineYml.isPresent()) {
        throw new InvalidRequestException("Failed to retrieve pipeline");
      }

      List<BuildTriggerOpsData> buildTriggerOpsData = new ArrayList<>();
      if (triggerType == ARTIFACT) {
        buildTriggerOpsData.add(
            buildTriggerHelper.generateBuildTriggerOpsDataForArtifact(triggerDetails, pipelineYml.get()));
      } else if (triggerType == MANIFEST) {
        buildTriggerOpsData.add(
            buildTriggerHelper.generateBuildTriggerOpsDataForManifest(triggerDetails, pipelineYml.get()));
      } else if (triggerType == WEBHOOK) {
        buildTriggerOpsData.add(buildTriggerHelper.generateBuildTriggerOpsDataForGitPolling(triggerDetails));
      } else if (triggerType == MULTI_REGION_ARTIFACT) {
        buildTriggerOpsData.addAll(buildTriggerHelper.generateBuildTriggerOpsDataForMultiArtifact(triggerDetails));
      }

      List<PollingItem> pollingItems = new ArrayList<>();
      for (BuildTriggerOpsData buildTriggerOpsDataEntry : buildTriggerOpsData) {
        PollingItemGenerator pollingItemGenerator = null;
        pollingItemGenerator = generatorFactory.retrievePollingItemGenerator(buildTriggerOpsDataEntry);
        if (pollingItemGenerator != null) {
          pollingItems.add(pollingItemGenerator.generatePollingItem(buildTriggerOpsDataEntry));
        } else {
          throw new InvalidRequestException("No polling item generator found for Trigger "
              + TriggerHelper.getTriggerRef(ngTriggerEntity) + " with specMap "
              + buildTriggerOpsDataEntry.getTriggerSpecMap());
        }
      }
      return pollingItems;
    } catch (Exception e) {
      String msg = String.format(
          "Failed while generating Polling Item for Trigger : %s", TriggerHelper.getTriggerRef(ngTriggerEntity));
      log.error(msg, e);
      throw new TriggerException(msg, e, USER_SRE);
    }
  }

  public List<PollingItem> generateMultiArtifactPollingItemsToUnsubscribe(NGTriggerEntity ngTriggerEntity) {
    List<String> signatures = ngTriggerEntity.getMetadata().getSignatures();
    if (isEmpty(signatures)) {
      return Collections.emptyList();
    }
    return signatures.stream()
        .map(signature -> {
          PollingItem.Builder pollingItem = PollingItem.newBuilder();
          pollingItem.setCategory(Category.ARTIFACT)
              .setQualifier(Qualifier.newBuilder()
                                .setAccountId(ngTriggerEntity.getAccountId())
                                .setOrganizationId(ngTriggerEntity.getOrgIdentifier())
                                .setProjectId(ngTriggerEntity.getProjectIdentifier())
                                .build())
              .setSignature(signature);
          return pollingItem.build();
        })
        .collect(Collectors.toList());
  }
}
