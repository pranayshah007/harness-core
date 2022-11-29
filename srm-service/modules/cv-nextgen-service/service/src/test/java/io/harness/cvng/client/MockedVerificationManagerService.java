/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.client;

import static io.harness.data.structure.UUIDGenerator.generateUuid;

import io.harness.cvng.BuilderFactory;
import io.harness.cvng.beans.CVNGPerpetualTaskDTO;
import io.harness.cvng.beans.CVNGPerpetualTaskState;
import io.harness.cvng.beans.DataCollectionConnectorBundle;
import io.harness.cvng.beans.DataCollectionRequest;
import io.harness.cvng.beans.change.HarnessCDCurrentGenEventMetadata;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

public class MockedVerificationManagerService implements VerificationManagerService {
  @Override
  public String createDataCollectionTask(
      String accountId, String orgIdentifier, String projectIdentifier, DataCollectionConnectorBundle bundle) {
    return generateUuid();
  }

  @Override
  public void resetDataCollectionTask(String accountId, String orgIdentifier, String projectIdentifier,
      String perpetualTaskId, DataCollectionConnectorBundle bundle) {}

  @Override
  public void deletePerpetualTask(String accountId, String perpetualTaskId) {}

  @Override
  public String getDataCollectionResponse(
      String accountId, String orgIdentifier, String projectIdentifier, DataCollectionRequest request) {
    return generateUuid();
  }

  @Override
  public List<String> getKubernetesNamespaces(
      String accountId, String orgIdentifier, String projectIdentifier, String connectorIdentifier, String filter) {
    return Collections.singletonList("k8_namespace_mocked");
  }

  @Override
  public List<String> getKubernetesWorkloads(String accountId, String orgIdentifier, String projectIdentifier,
      String connectorIdentifier, String namespace, String filter) {
    return Collections.singletonList("k8_workspace_mocked");
  }

  @Override
  public List<String> checkCapabilityToGetKubernetesEvents(
      String accountId, String orgIdentifier, String projectIdentifier, String connectorIdentifier) {
    return Collections.singletonList("mocked");
  }

  @Override
  public List<HarnessCDCurrentGenEventMetadata> getCurrentGenEvents(String accountId, String harnessApplicationId,
      String harnessEnvironmentId, String harnessServiceId, Instant startTime, Instant endTime) {
    return Collections.singletonList((HarnessCDCurrentGenEventMetadata) BuilderFactory.getDefault()
                                         .harnessCDChangeEventDTOBuilder()
                                         .build()
                                         .getMetadata());
  }

  @Override
  public CVNGPerpetualTaskDTO getPerpetualTaskStatus(String perpetualTaskId) {
    return CVNGPerpetualTaskDTO.builder()
        .accountId("some-accountId")
        .delegateId("some-delegate-id")
        .cvngPerpetualTaskState(CVNGPerpetualTaskState.TASK_UNASSIGNED)
        .build();
  }
}
