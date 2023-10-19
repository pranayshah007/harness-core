/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.polling.service.impl.manifest;
import static io.harness.delegate.task.helm.HelmValuesFetchRequest.getHelmExecutionCapabilities;
import static io.harness.utils.DelegateOwner.getNGTaskSetupAbstractionsWithOwner;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.cdng.k8s.K8sStepHelper;
import io.harness.cdng.manifest.ManifestType;
import io.harness.cdng.manifest.yaml.HelmChartManifestOutcome;
import io.harness.delegate.Capability;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.task.k8s.HelmChartManifestDelegateConfig;
import io.harness.exception.InvalidRequestException;
import io.harness.perpetualtask.PerpetualTaskExecutionBundle;
import io.harness.perpetualtask.polling.ManifestCollectionTaskParamsNg;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.plan.execution.SetupAbstractionKeys;
import io.harness.polling.bean.ManifestInfo;
import io.harness.polling.bean.PollingDocument;
import io.harness.serializer.KryoSerializer;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;

@CodePulse(
    module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_COMMON_STEPS})
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Singleton
@OwnedBy(HarnessTeam.CDC)
public class ManifestPerpetualTaskHelperNg {
  K8sStepHelper k8sStepHelper;
  KryoSerializer kryoSerializer;

  public PerpetualTaskExecutionBundle createPerpetualTaskExecutionBundle(PollingDocument pollingDocument) {
    Any perpetualTaskParams;
    List<ExecutionCapability> executionCapabilities;
    ManifestInfo manifestInfo = (ManifestInfo) pollingDocument.getPollingInfo();
    String accountId = pollingDocument.getAccountId();

    // This is a hack so that we can re-use same methods.
    // Do note that Ambiance is incomplete here. In future, if need be, populate accordingly to avoid failures.
    Map<String, String> abstractions = new HashMap<>();
    abstractions.put(SetupAbstractionKeys.accountId, accountId);
    if (pollingDocument.getOrgIdentifier() != null) {
      abstractions.put(SetupAbstractionKeys.orgIdentifier, pollingDocument.getOrgIdentifier());
    }
    if (pollingDocument.getProjectIdentifier() != null) {
      abstractions.put(SetupAbstractionKeys.projectIdentifier, pollingDocument.getProjectIdentifier());
    }
    Ambiance ambiance = Ambiance.newBuilder().putAllSetupAbstractions(abstractions).build();

    final Map<String, String> ngTaskSetupAbstractionsWithOwner = getNGTaskSetupAbstractionsWithOwner(
        accountId, pollingDocument.getOrgIdentifier(), pollingDocument.getProjectIdentifier());

    if (ManifestType.HelmChart.equals(manifestInfo.getType())) {
      HelmChartManifestOutcome helmChartManifestOutcome = (HelmChartManifestOutcome) manifestInfo.toManifestOutcome();
      HelmChartManifestDelegateConfig helmManifest =
          (HelmChartManifestDelegateConfig) k8sStepHelper.getManifestDelegateConfig(helmChartManifestOutcome, ambiance);
      executionCapabilities =
          getHelmExecutionCapabilities(helmManifest.getHelmVersion(), helmManifest.getStoreDelegateConfig(), null,
              helmManifest.isIgnoreResponseCode(), helmManifest.getChartName());
      ManifestCollectionTaskParamsNg manifestCollectionTaskParamsNg =
          ManifestCollectionTaskParamsNg.newBuilder()
              .setAccountId(accountId)
              .setPollingDocId(pollingDocument.getUuid())
              .setManifestCollectionParams(ByteString.copyFrom(kryoSerializer.asBytes(helmManifest)))
              .build();
      perpetualTaskParams = Any.pack(manifestCollectionTaskParamsNg);
    } else {
      throw new InvalidRequestException(String.format("Invalid type %s for polling", manifestInfo.getType()));
    }

    PerpetualTaskExecutionBundle.Builder builder = PerpetualTaskExecutionBundle.newBuilder();
    executionCapabilities.forEach(executionCapability
        -> builder
               .addCapabilities(
                   Capability.newBuilder()
                       .setKryoCapability(ByteString.copyFrom(kryoSerializer.asDeflatedBytes(executionCapability)))
                       .build())
               .build());
    return builder.setTaskParams(perpetualTaskParams).putAllSetupAbstractions(ngTaskSetupAbstractionsWithOwner).build();
  }
}
