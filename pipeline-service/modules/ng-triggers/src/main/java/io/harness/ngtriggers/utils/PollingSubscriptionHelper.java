/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ngtriggers.utils;

import static io.harness.exception.WingsException.USER_SRE;
import static io.harness.ngtriggers.beans.source.NGTriggerType.ARTIFACT;
import static io.harness.ngtriggers.beans.source.NGTriggerType.MANIFEST;

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
import io.harness.polling.contracts.PollingItem;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Optional;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Singleton
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
public class PollingSubscriptionHelper {
  private final BuildTriggerHelper buildTriggerHelper;
  private final NGTriggerElementMapper ngTriggerElementMapper;
  private final GeneratorFactory generatorFactory;

  public PollingItem generatePollingItem(NGTriggerEntity ngTriggerEntity) {
    NGTriggerType triggerType = ngTriggerEntity.getType();
    if (triggerType != ARTIFACT && triggerType != MANIFEST) {
      throw new InvalidRequestException("");
    }

    try {
      Optional<String> pipelineYml = buildTriggerHelper.fetchResolvedTemplatesPipelineForTrigger(ngTriggerEntity);
      if (!pipelineYml.isPresent()) {
        throw new InvalidRequestException("Failed to retrieve pipeline");
      }

      TriggerDetails triggerDetails = ngTriggerElementMapper.toTriggerDetails(ngTriggerEntity);

      BuildTriggerOpsData buildTriggerOpsData = null;
      PollingItemGenerator pollingItemGenerator = null;
      if (triggerType == ARTIFACT) {
        buildTriggerOpsData =
            buildTriggerHelper.generateBuildTriggerOpsDataForArtifact(triggerDetails, pipelineYml.get());
      } else {
        buildTriggerOpsData =
            buildTriggerHelper.generateBuildTriggerOpsDataForManifest(triggerDetails, pipelineYml.get());
      }

      pollingItemGenerator = generatorFactory.retrievePollingItemGenerator(buildTriggerOpsData);
      if (pollingItemGenerator != null) {
        return pollingItemGenerator.generatePollingItem(buildTriggerOpsData);
      }
    } catch (Exception e) {
      String msg = String.format(
          "Failed while generating Polling Item for Trigger : %s", TriggerHelper.getTriggerRef(ngTriggerEntity));
      log.error(msg, e);
      throw new TriggerException(msg, e, USER_SRE);
    }
    return null;
  }
}
