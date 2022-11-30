/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.services.api;

import io.harness.cvng.beans.HostRecordDTO;

import java.time.Instant;
import java.util.List;
import java.util.Set;

public interface HostRecordService {
  void save(HostRecordDTO hostRecordDTO);
  void save(List<HostRecordDTO> hostRecordDTOs);
  Set<String> get(String verificationTaskId, Instant startTime, Instant endTime);
  Set<String> get(Set<String> verificationTaskIds, Instant startTime, Instant endTime);
}
