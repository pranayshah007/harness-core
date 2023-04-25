/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.services.impl;

import static io.harness.persistence.HQuery.excludeAuthority;

import io.harness.cvng.beans.HostRecordDTO;
import io.harness.cvng.core.entities.HostRecord;
import io.harness.cvng.core.entities.HostRecord.HostRecordKeys;
import io.harness.cvng.core.services.api.HostRecordService;
import io.harness.persistence.HPersistence;

import com.google.inject.Inject;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class HostRecordServiceImpl implements HostRecordService {
  @Inject private HPersistence hPersistence;

  @Override
  public void save(HostRecordDTO hostRecordDTO) {
    hPersistence.save(toHostRecord(hostRecordDTO));
  }

  @Override
  public void save(List<HostRecordDTO> hostRecordDTOs) {
    saveRecords(hostRecordDTOs.stream().map(this::toHostRecord).collect(Collectors.toList()));
  }

  @Override
  public Set<String> get(String verificationTaskId, Instant startTime, Instant endTime) {
    List<HostRecord> hostRecords = hPersistence.createQuery(HostRecord.class, excludeAuthority)
                                       .filter(HostRecordKeys.verificationTaskId, verificationTaskId)
                                       .field(HostRecordKeys.startTime)
                                       .greaterThanOrEq(startTime)
                                       .field(HostRecordKeys.endTime)
                                       .lessThanOrEq(endTime)
                                       .asList();
    return hostRecords.stream()
        .map(hostRecord -> hostRecord.getHosts())
        .flatMap(hosts -> hosts.stream())
        .collect(Collectors.toSet());
  }

  @Override
  public Set<String> get(Set<String> verificationTaskIds, Instant startTime, Instant endTime) {
    List<HostRecord> hostRecords = hPersistence.createQuery(HostRecord.class, excludeAuthority)
                                       .field(HostRecordKeys.verificationTaskId)
                                       .in(verificationTaskIds)
                                       .field(HostRecordKeys.startTime)
                                       .greaterThanOrEq(startTime)
                                       .field(HostRecordKeys.endTime)
                                       .lessThanOrEq(endTime)
                                       .asList();
    return hostRecords.stream()
        .map(hostRecord -> hostRecord.getHosts())
        .flatMap(hosts -> hosts.stream())
        .collect(Collectors.toSet());
  }

  private void saveRecords(List<HostRecord> hostRecords) {
    hPersistence.saveBatch(hostRecords);
  }
  private HostRecord toHostRecord(HostRecordDTO hostRecordDTOs) {
    return HostRecord.builder()
        .accountId(hostRecordDTOs.getAccountId())
        .verificationTaskId(hostRecordDTOs.getVerificationTaskId())
        .hosts(filterNullHosts(hostRecordDTOs.getHosts()))
        .startTime(hostRecordDTOs.getStartTime())
        .endTime(hostRecordDTOs.getEndTime())
        .build();
  }

  private Set<String> filterNullHosts(Set<String> hosts) {
    Set<String> hostSet = new HashSet<>();
    if (hosts != null) {
      return hosts.stream().filter(host -> host != null).collect(Collectors.toSet());
    }
    return hostSet;
  }
}
