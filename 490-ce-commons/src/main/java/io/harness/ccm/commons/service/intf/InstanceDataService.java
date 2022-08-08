/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ccm.commons.service.intf;

import static io.harness.annotations.dev.HarnessTeam.CE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.ccm.commons.entities.batch.InstanceData;

import java.util.List;
import java.util.Map;

@OwnedBy(CE)
public interface InstanceDataService {
  InstanceData get(String instanceId);
  List<InstanceData> fetchInstanceDataForGivenInstances(List<String> instanceIds);
  List<InstanceData> fetchInstanceDataForGivenInstances(String accountId, String clusterId, List<String> instanceIds);
  Map<String, Map<String, String>> fetchLabelsForGivenInstances(String accountId, List<String> instanceIds);
}
