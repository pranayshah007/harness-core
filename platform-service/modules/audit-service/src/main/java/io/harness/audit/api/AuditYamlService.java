/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.audit.api;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.audit.entities.YamlDiffRecord;

import java.time.Instant;

@OwnedBy(PL)
public interface AuditYamlService {
  YamlDiffRecord get(String auditId);
  YamlDiffRecord save(YamlDiffRecord yamlDiffRecord);
  void purgeYamlDiffOlderThanTimestamp(String accountIdentifier, Instant timestamp);
  boolean delete(String auditId);
}
