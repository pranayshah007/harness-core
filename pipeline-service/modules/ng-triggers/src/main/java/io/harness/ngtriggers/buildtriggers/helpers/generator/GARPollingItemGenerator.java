package io.harness.ngtriggers.buildtriggers.helpers.generator;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ngtriggers.beans.entity.NGTriggerEntity;
import io.harness.ngtriggers.buildtriggers.helpers.BuildTriggerHelper;
import io.harness.ngtriggers.buildtriggers.helpers.dtos.BuildTriggerOpsData;
import io.harness.polling.contracts.GARPayload;
import io.harness.polling.contracts.PollingItem;
import io.harness.polling.contracts.PollingPayloadData;
import io.harness.polling.contracts.Type;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.AllArgsConstructor;

@Singleton
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@OwnedBy(HarnessTeam.CDC)
public class GARPollingItemGenerator implements PollingItemGenerator {
  @Inject BuildTriggerHelper buildTriggerHelper;
  @Override
  public PollingItem generatePollingItem(BuildTriggerOpsData buildTriggerOpsData) {
    NGTriggerEntity ngTriggerEntity = buildTriggerOpsData.getTriggerDetails().getNgTriggerEntity();
    PollingItem.Builder builder = getBaseInitializedPollingItem(ngTriggerEntity);
    String connectorRef = buildTriggerHelper.validateAndFetchFromJsonNode(buildTriggerOpsData, "spec.connectorRef");
    String version = buildTriggerHelper.validateAndFetchFromJsonNode(buildTriggerOpsData, "spec.version");
    return builder
        .setPollingPayloadData(PollingPayloadData.newBuilder()
                                   .setConnectorRef(connectorRef)
                                   .setType(Type.GOOGLE_ARTIFACT_REGISTRY)
                                   .setGarPayload(GARPayload.newBuilder().setVersion(version).build())
                                   .build())
        .build();
  }
}
