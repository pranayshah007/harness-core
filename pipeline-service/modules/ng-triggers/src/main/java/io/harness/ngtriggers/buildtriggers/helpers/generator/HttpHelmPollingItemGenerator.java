/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ngtriggers.buildtriggers.helpers.generator;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.ngtriggers.beans.entity.NGTriggerEntity;
import io.harness.ngtriggers.buildtriggers.helpers.BuildTriggerHelper;
import io.harness.ngtriggers.buildtriggers.helpers.dtos.BuildTriggerOpsData;
import io.harness.polling.contracts.HelmVersion;
import io.harness.polling.contracts.HttpHelmPayload;
import io.harness.polling.contracts.PollingItem;
import io.harness.polling.contracts.PollingPayloadData;
import io.harness.polling.contracts.Type;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.AllArgsConstructor;

@Singleton
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@OwnedBy(PIPELINE)
public class HttpHelmPollingItemGenerator implements PollingItemGenerator {
  @Inject BuildTriggerHelper buildTriggerHelper;

  @Override
  public PollingItem generatePollingItem(BuildTriggerOpsData buildTriggerOpsData) {
    NGTriggerEntity ngTriggerEntity = buildTriggerOpsData.getTriggerDetails().getNgTriggerEntity();

    PollingItem.Builder builder = getBaseInitializedPollingItem(ngTriggerEntity, buildTriggerOpsData);

    String connectorRef =
        buildTriggerHelper.validateAndFetchFromJsonNode(buildTriggerOpsData, "spec.store.spec.connectorRef");
    String chartName = buildTriggerHelper.validateAndFetchFromJsonNode(buildTriggerOpsData, "spec.chartName");
    String helmVersion = buildTriggerHelper.validateAndFetchFromJsonNode(buildTriggerOpsData, "spec.helmVersion");

    return builder
        .setPollingPayloadData(PollingPayloadData.newBuilder()
                                   .setConnectorRef(connectorRef)
                                   .setType(Type.HTTP_HELM)
                                   .setHttpHelmPayload(HttpHelmPayload.newBuilder()
                                                           .setChartName(chartName)
                                                           .setHelmVersion(HelmVersion.valueOf(helmVersion))
                                                           .build())
                                   .build())
        .build();
  }
}
