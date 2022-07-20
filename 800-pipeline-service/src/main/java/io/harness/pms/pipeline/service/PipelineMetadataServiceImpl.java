/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.pipeline.service;

import io.harness.exception.InvalidRequestException;
import io.harness.lock.AcquiredLock;
import io.harness.lock.PersistentLocker;
import io.harness.pms.pipeline.PipelineEntity;
import io.harness.pms.pipeline.PipelineMetadataV2;
import io.harness.repositories.pipeline.PipelineMetadataV2Repository;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Update;

@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@Singleton
@Slf4j
public class PipelineMetadataServiceImpl implements PipelineMetadataService {
  private PipelineMetadataV2Repository pipelineMetadataV2Repository;
  private PersistentLocker persistentLocker;

  @Override
  public int incrementRunSequence(PipelineEntity pipelineEntity) {
    String accountId = pipelineEntity.getAccountId();
    String orgIdentifier = pipelineEntity.getOrgIdentifier();
    String projectIdentifier = pipelineEntity.getProjectIdentifier();
    int count = incrementExecutionCounter(accountId, orgIdentifier, projectIdentifier, pipelineEntity.getIdentifier());
    if (count == -1) {
      try {
        PipelineMetadataV2 metadata = PipelineMetadataV2.builder()
                                          .accountIdentifier(pipelineEntity.getAccountIdentifier())
                                          .orgIdentifier(orgIdentifier)
                                          .projectIdentifier(projectIdentifier)
                                          .runSequence(pipelineEntity.getRunSequence() + 1)
                                          .identifier(pipelineEntity.getIdentifier())
                                          .build();
        return save(metadata).getRunSequence();
      } catch (DuplicateKeyException exception) {
        // retry insert if above fails
        return incrementExecutionCounter(accountId, orgIdentifier, projectIdentifier, pipelineEntity.getIdentifier());
      }
    }
    return count;
  }

  @Override
  public int incrementExecutionCounter(
      String accountId, String orgIdentifier, String projectIdentifier, String pipelineIdentifier) {
    PipelineMetadataV2 pipelineMetadataV2 =
        pipelineMetadataV2Repository.incCounter(accountId, orgIdentifier, projectIdentifier, pipelineIdentifier);
    if (pipelineMetadataV2 != null) {
      return pipelineMetadataV2.getRunSequence();
    }
    String lockName =
        String.format("pipelineMetadata/%s/%s/%s/%s", accountId, orgIdentifier, projectIdentifier, pipelineIdentifier);
    try (AcquiredLock<?> lock =
             persistentLocker.waitToAcquireLock(lockName, Duration.ofSeconds(1), Duration.ofSeconds(2))) {
      if (lock == null) {
        log.error("Count not acquire lock");
        throw new InvalidRequestException("Unable to update build sequence, please retry the execution");
      }
      Optional<PipelineMetadataV2> pipelineMetadataV2Updated = pipelineMetadataV2Repository.cloneFromPipelineMetadata(
          accountId, orgIdentifier, projectIdentifier, pipelineIdentifier);
      return pipelineMetadataV2Updated.map(PipelineMetadataV2::getRunSequence).orElse(-1);
    }
  }

  @Override
  public PipelineMetadataV2 save(PipelineMetadataV2 metadata) {
    return pipelineMetadataV2Repository.save(metadata);
  }

  @Override
  public PipelineMetadataV2 update(Criteria criteria, Update update) {
    return pipelineMetadataV2Repository.update(criteria, update);
  }

  @Override
  public Optional<PipelineMetadataV2> getMetadata(
      String accountId, String orgIdentifier, String projectIdentifier, String identifier) {
    return pipelineMetadataV2Repository.getPipelineMetadata(accountId, orgIdentifier, projectIdentifier, identifier);
  }

  @Override
  public Map<String, PipelineMetadataV2> getMetadataForGivenPipelineIds(
      String accountId, String orgIdentifier, String projectIdentifier, List<String> identifiers) {
    List<PipelineMetadataV2> pipelineMetadataList = pipelineMetadataV2Repository.getMetadataForGivenPipelineIds(
        accountId, orgIdentifier, projectIdentifier, identifiers);
    return pipelineMetadataList.stream().collect(
        Collectors.toMap(PipelineMetadataV2::getIdentifier, Function.identity()));
  }
}
