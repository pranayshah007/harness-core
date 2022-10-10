/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.writer;

import io.harness.batch.processing.service.intfc.InstanceDataService;
import io.harness.ccm.commons.entities.batch.InstanceData;
import io.harness.exception.InvalidRequestException;

import software.wings.service.intfc.instance.CloudToHarnessMappingService;

import java.time.Instant;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

@Slf4j
public abstract class EventWriter {
  @Autowired protected InstanceDataService instanceDataService;
  @Autowired protected CloudToHarnessMappingService cloudToHarnessMappingService;

  protected InstanceData fetchInstanceData(String accountId, String instanceId) {
    InstanceData ec2InstanceData = instanceDataService.fetchInstanceData(accountId, instanceId);
    if (null == ec2InstanceData) {
      log.error("Instance detail not present {} ", instanceId);
      throw new InvalidRequestException("EC2 Instance detail not present");
    }
    return ec2InstanceData;
  }

  protected Set<String> fetchActiveInstanceAtTime(String accountId, String clusterId, Instant startTime) {
    return instanceDataService.fetchClusterActiveInstanceIds(accountId, clusterId, startTime);
  }
}
