/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.audit.api.impl;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.annotations.dev.OwnedBy;
import io.harness.audit.api.AuditYamlService;
import io.harness.audit.entities.YamlDiffRecord;
import io.harness.audit.entities.YamlDiffRecord.YamlDiffRecordKeys;
import io.harness.audit.repositories.AuditYamlRepository;
import io.harness.exception.InvalidRequestException;

import com.google.inject.Inject;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.mongodb.core.query.Criteria;

@OwnedBy(PL)
public class AuditYamlServiceImpl implements AuditYamlService {
  @Inject private AuditYamlRepository auditYamlRepository;

  @Override
  public YamlDiffRecord get(String auditId) {
    if (isNotEmpty(auditId)) {
      Optional<YamlDiffRecord> yamlDiffRecord = auditYamlRepository.findByAuditId(auditId);
      if (yamlDiffRecord.isPresent()) {
        return yamlDiffRecord.get();
      }
    }
    throw new InvalidRequestException(
        String.format("Yaml Diff corresponding to audit with id %s does not exist", auditId));
  }

  @Override
  public YamlDiffRecord save(YamlDiffRecord yamlDiffRecord) {
    return auditYamlRepository.save(yamlDiffRecord);
  }

  @Override
  public boolean delete(String auditId) {
    if (isNotEmpty(auditId)) {
      auditYamlRepository.deleteByAuditId(auditId);
      return true;
    }
    return false;
  }

  @Override
  public void deleteByAccount(String accountId) {
    auditYamlRepository.deleteAllByAccountIdentifier(accountId);
  }

  @Override
  public void purgeYamlDiffOlderThanTimestamp(String accountIdentifier, Instant timestamp) {
    auditYamlRepository.delete(Criteria.where(YamlDiffRecordKeys.timestamp)
                                   .lte(timestamp)
                                   .and(YamlDiffRecordKeys.accountIdentifier)
                                   .is(accountIdentifier));
  }
}
