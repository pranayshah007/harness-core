package io.harness.ngtriggers.buildtriggers.helpers.generator;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.OwnedBy;
import io.harness.ngtriggers.beans.entity.NGTriggerEntity;
import io.harness.ngtriggers.buildtriggers.helpers.BuildTriggerHelper;
import io.harness.ngtriggers.buildtriggers.helpers.dtos.BuildTriggerOpsData;
import io.harness.polling.contracts.GithubPackagesPollingPayload;
import io.harness.polling.contracts.PollingItem;
import io.harness.polling.contracts.PollingPayloadData;
import io.harness.polling.contracts.Type;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.AllArgsConstructor;

@Singleton
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@OwnedBy(CDC)
public class GithubPackagesPollingItemGenerator implements PollingItemGenerator {
  @Inject BuildTriggerHelper buildTriggerHelper;
  @Override
  public PollingItem generatePollingItem(BuildTriggerOpsData buildTriggerOpsData) {
    NGTriggerEntity ngTriggerEntity = buildTriggerOpsData.getTriggerDetails().getNgTriggerEntity();
    PollingItem.Builder builder = getBaseInitializedPollingItem(ngTriggerEntity);
    String connectorRef = buildTriggerHelper.validateAndFetchFromJsonNode(buildTriggerOpsData, "spec.connectorRef");
    String packageName = buildTriggerHelper.validateAndFetchFromJsonNode(buildTriggerOpsData, "spec.packageName");
    String org = buildTriggerHelper.validateAndFetchFromJsonNode(buildTriggerOpsData, "spec.org");
    String packageType = buildTriggerHelper.validateAndFetchFromJsonNode(buildTriggerOpsData, "spec.packageType");
    return builder
        .setPollingPayloadData(PollingPayloadData.newBuilder()
                                   .setConnectorRef(connectorRef)
                                   .setType(Type.GITHUB_PACKAGES)
                                   .setGithubPackagesPollingPayload(GithubPackagesPollingPayload.newBuilder()
                                                                        .setOrg(org)
                                                                        .setPackageName(packageName)
                                                                        .setPackageType(packageType)
                                                                        .build())
                                   .build())
        .build();
  }
}
