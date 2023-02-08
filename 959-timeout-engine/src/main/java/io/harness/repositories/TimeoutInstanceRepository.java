/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.repositories;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotation.HarnessRepo;
import io.harness.annotations.dev.OwnedBy;
import io.harness.timeout.TimeoutInstance;

import java.util.List;
import org.springframework.data.repository.CrudRepository;

@OwnedBy(CDC)
@HarnessRepo
public interface TimeoutInstanceRepository extends CrudRepository<TimeoutInstance, String> {
  void deleteByUuidIn(List<String> ids);
}
