/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.tasklet.support;

import io.harness.exception.InvalidRequestException;

import software.wings.service.intfc.instance.CloudToHarnessMappingService;

import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class HarnessEntitiesService {
  public enum HarnessEntities { APP, SERVICE, ENV }

  @Value
  public static class CacheKey {
    public String entityId;
    public HarnessEntities entity;
  }

  @Autowired private CloudToHarnessMappingService cloudToHarnessMappingService;

  public String fetchEntityName(HarnessEntities entity, String entityId) {
    switch (entity) {
      case APP:
        return cloudToHarnessMappingService.getApplicationName(entityId);
      case ENV:
        return cloudToHarnessMappingService.getEnvironmentName(entityId);
      case SERVICE:
        return cloudToHarnessMappingService.getServiceName(entityId);
      default:
        throw new InvalidRequestException("Invalid EntityType " + entity);
    }
  }
}
