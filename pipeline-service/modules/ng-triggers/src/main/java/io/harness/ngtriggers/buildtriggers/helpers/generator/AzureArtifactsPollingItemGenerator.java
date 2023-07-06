/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngtriggers.buildtriggers.helpers.generator;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.OwnedBy;
import io.harness.ngtriggers.beans.entity.NGTriggerEntity;
import io.harness.ngtriggers.buildtriggers.helpers.BuildTriggerHelper;
import io.harness.ngtriggers.buildtriggers.helpers.dtos.BuildTriggerOpsData;
import io.harness.polling.contracts.AzureArtifactsPayload;
import io.harness.polling.contracts.PollingItem;
import io.harness.polling.contracts.PollingPayloadData;
import io.harness.polling.contracts.Type;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.AllArgsConstructor;

@Singleton
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@OwnedBy(CDC)
public class AzureArtifactsPollingItemGenerator implements PollingItemGenerator {
  @Inject BuildTriggerHelper buildTriggerHelper;

  @Override
  public PollingItem generatePollingItem(BuildTriggerOpsData buildTriggerOpsData) {
    NGTriggerEntity ngTriggerEntity = buildTriggerOpsData.getTriggerDetails().getNgTriggerEntity();

    PollingItem.Builder builder = getBaseInitializedPollingItem(ngTriggerEntity, buildTriggerOpsData);

    String connectorRef = buildTriggerHelper.validateAndFetchFromJsonNode(buildTriggerOpsData, "spec.connectorRef");

    String project = buildTriggerHelper.validateAndFetchFromJsonNode(buildTriggerOpsData, "spec.project");

    String feed = buildTriggerHelper.validateAndFetchFromJsonNode(buildTriggerOpsData, "spec.feed");

    String packageName = buildTriggerHelper.validateAndFetchFromJsonNode(buildTriggerOpsData, "spec.package");

    String packageType = buildTriggerHelper.validateAndFetchFromJsonNode(buildTriggerOpsData, "spec.packageType");

    String versionRegex = buildTriggerHelper.validateAndFetchFromJsonNode(buildTriggerOpsData, "spec.versionRegex");

    return builder
        .setPollingPayloadData(PollingPayloadData.newBuilder()
                                   .setConnectorRef(connectorRef)
                                   .setType(Type.AZURE_ARTIFACTS)
                                   .setAzureArtifactsPayload(AzureArtifactsPayload.newBuilder()
                                                                 .setProject(project)
                                                                 .setFeed(feed)
                                                                 .setPackageName(packageName)
                                                                 .setPackageType(packageType)
                                                                 .setVersionRegex(versionRegex)
                                                                 .build())
                                   .build())
        .build();
  }
}
