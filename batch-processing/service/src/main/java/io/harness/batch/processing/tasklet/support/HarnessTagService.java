/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.tasklet.support;

import io.harness.batch.processing.tasklet.dto.HarnessTags;

import software.wings.beans.HarnessTagLink;
import software.wings.service.intfc.instance.CloudToHarnessMappingService;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class HarnessTagService {
  @Autowired private CloudToHarnessMappingService cloudToHarnessMappingService;
  LoadingCache<CacheKey, List<HarnessTags>> instanceDataCache =
      Caffeine.newBuilder()
          .expireAfterWrite(24, TimeUnit.HOURS)
          .build(key -> getHarnessTags(key.accountId, key.entityId));

  @Value
  private static class CacheKey {
    private String accountId;
    private String entityId;
  }

  public List<HarnessTags> getHarnessTags(String accountId, String entityId) {
    List<HarnessTagLink> tags = cloudToHarnessMappingService.getTagLinksWithEntityId(accountId, entityId);
    return tags.stream()
        .map(tag -> HarnessTags.builder().key(tag.getKey()).value(tag.getValue()).build())
        .collect(Collectors.toList());
  }
}
