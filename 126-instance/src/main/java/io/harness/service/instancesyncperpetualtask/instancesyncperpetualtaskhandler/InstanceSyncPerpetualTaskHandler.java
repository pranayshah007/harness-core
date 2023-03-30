/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.service.instancesyncperpetualtask.instancesyncperpetualtaskhandler;

import static io.harness.delegate.beans.NgSetupFields.NG;
import static io.harness.delegate.beans.NgSetupFields.OWNER;

import io.harness.cdng.infra.beans.InfrastructureOutcome;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.delegate.Capability;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.dtos.InfrastructureMappingDTO;
import io.harness.dtos.deploymentinfo.DeploymentInfoDTO;
import io.harness.perpetualtask.PerpetualTaskExecutionBundle;
import io.harness.serializer.KryoSerializer;

import com.google.inject.Inject;
import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import java.util.List;
import javax.validation.constraints.NotNull;
import org.apache.groovy.util.Maps;

public abstract class InstanceSyncPerpetualTaskHandler {
  @Inject protected KryoSerializer kryoSerializer;

  public abstract PerpetualTaskExecutionBundle getExecutionBundle(InfrastructureMappingDTO infrastructureMappingDTO,
      List<DeploymentInfoDTO> deploymentInfoDTOList, InfrastructureOutcome infrastructureOutcome);

  public PerpetualTaskExecutionBundle getExecutionBundleForV2(
      InfrastructureMappingDTO infrastructureMappingDTO, ConnectorInfoDTO connectorInfoDTO) {
    throw new UnsupportedOperationException();
  }

  @NotNull
  protected PerpetualTaskExecutionBundle createPerpetualTaskExecutionBundle(Any perpetualTaskPack,
      List<ExecutionCapability> executionCapabilities, String orgIdentifier, String projectIdentifier) {
    PerpetualTaskExecutionBundle.Builder builder = PerpetualTaskExecutionBundle.newBuilder();
    executionCapabilities.forEach(executionCapability
        -> builder
               .addCapabilities(
                   Capability.newBuilder()
                       .setKryoCapability(ByteString.copyFrom(kryoSerializer.asDeflatedBytes(executionCapability)))
                       .build())
               .build());
    return builder.setTaskParams(perpetualTaskPack)
        .putAllSetupAbstractions(Maps.of(NG, "true", OWNER, orgIdentifier + "/" + projectIdentifier))
        .build();
  }
}
