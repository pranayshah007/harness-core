/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.audit.api;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.audit.beans.AuditEventDTO;
import io.harness.audit.beans.AuditFilterPropertiesDTO;
import io.harness.audit.entities.AuditEvent;
import io.harness.ng.beans.PageRequest;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.domain.Page;

@OwnedBy(PL)
public interface AuditService {
  Boolean create(AuditEventDTO auditEventDTO);

  Optional<AuditEvent> get(String accountIdentifier, String auditId);

  Page<AuditEvent> list(
      String accountIdentifier, PageRequest pageRequest, AuditFilterPropertiesDTO auditFilterPropertiesDTO);

  void purgeAuditsOlderThanTimestamp(String accountIdentifier, Instant timestamp);

  Set<String> getUniqueAuditedAccounts();
}
