/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.client;

import io.harness.cvng.beans.CVNGPerpetualTaskDTO;
import io.harness.cvng.beans.DataCollectionConnectorBundle;
import io.harness.cvng.beans.DataCollectionRequest;
import io.harness.cvng.beans.change.HarnessCDCurrentGenEventMetadata;

import java.time.Instant;
import java.util.List;

public interface VerificationManagerService {
  String createDataCollectionTask(
      String accountId, String orgIdentifier, String projectIdentifier, DataCollectionConnectorBundle bundle);

  void resetDataCollectionTask(String accountId, String orgIdentifier, String projectIdentifier, String perpetualTaskId,
      DataCollectionConnectorBundle bundle);

  void deletePerpetualTask(String accountId, String perpetualTaskId);
  String getDataCollectionResponse(
      String accountId, String orgIdentifier, String projectIdentifier, DataCollectionRequest request);
  List<String> getKubernetesNamespaces(
      String accountId, String orgIdentifier, String projectIdentifier, String connectorIdentifier, String filter);
  List<String> getKubernetesWorkloads(String accountId, String orgIdentifier, String projectIdentifier,
      String connectorIdentifier, String namespace, String filter);

  List<String> checkCapabilityToGetKubernetesEvents(
      String accountId, String orgIdentifier, String projectIdentifier, String connectorIdentifier);

  List<HarnessCDCurrentGenEventMetadata> getCurrentGenEvents(String accountId, String harnessApplicationId,
      String harnessEnvironmentId, String harnessServiceId, Instant startTime, Instant endTime);

  CVNGPerpetualTaskDTO getPerpetualTaskStatus(String perpetualTaskId);
}
