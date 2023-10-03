/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ssca.services;

import io.harness.entities.Instance;
import io.harness.repositories.CdInstanceSummaryRepo;
import io.harness.ssca.beans.EnvType;
import io.harness.ssca.entities.CdInstanceSummary;
import io.harness.ssca.entities.CdInstanceSummary.CdInstanceSummaryKeys;

import com.google.inject.Inject;
import java.time.Instant;
import java.util.Collections;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.query.Criteria;

@Slf4j
public class CdInstanceSummaryServiceImpl implements CdInstanceSummaryService {
  @Inject CdInstanceSummaryRepo cdInstanceSummaryRepo;

  @Override
  public void upsertInstance(Instance instance) {
    Criteria criteria = Criteria.where(CdInstanceSummaryKeys.artifactCorrelationId)
                            .is(instance.getPrimaryArtifact().getArtifactIdentity().getImage())
                            .and(CdInstanceSummaryKeys.accountIdentifier)
                            .is(instance.getAccountIdentifier())
                            .and(CdInstanceSummaryKeys.orgIdentifier)
                            .is(instance.getOrgIdentifier())
                            .and(CdInstanceSummaryKeys.projectIdentifier)
                            .is(instance.getProjectIdentifier())
                            .and(CdInstanceSummaryKeys.envIdentifier)
                            .is(instance.getEnvIdentifier());

    CdInstanceSummary cdInstanceSummary = cdInstanceSummaryRepo.findOne(criteria);

    if (Objects.nonNull(cdInstanceSummary)) {
      cdInstanceSummary.getInstanceIds().add(instance.getId());
      cdInstanceSummaryRepo.save(cdInstanceSummary);
    } else {
      cdInstanceSummaryRepo.save(createInstanceSummary(instance));
    }
  }

  @Override
  public void removeInstance(Instance instance) {
    Criteria criteria = Criteria.where(CdInstanceSummaryKeys.artifactCorrelationId)
                            .is(instance.getPrimaryArtifact().getArtifactIdentity().getImage())
                            .and(CdInstanceSummaryKeys.accountIdentifier)
                            .is(instance.getAccountIdentifier())
                            .and(CdInstanceSummaryKeys.orgIdentifier)
                            .is(instance.getOrgIdentifier())
                            .and(CdInstanceSummaryKeys.projectIdentifier)
                            .is(instance.getProjectIdentifier())
                            .and(CdInstanceSummaryKeys.envIdentifier)
                            .is(instance.getEnvIdentifier());

    CdInstanceSummary cdInstanceSummary = cdInstanceSummaryRepo.findOne(criteria);

    if (Objects.nonNull(cdInstanceSummary)) {
      cdInstanceSummary.getInstanceIds().remove(instance.getId());
      if (cdInstanceSummary.getInstanceIds().isEmpty()) {
        cdInstanceSummaryRepo.delete(cdInstanceSummary);
      } else {
        cdInstanceSummaryRepo.save(cdInstanceSummary);
      }
    }
  }

  private CdInstanceSummary createInstanceSummary(Instance instance) {
    return CdInstanceSummary.builder()
        .artifactCorrelationId(instance.getPrimaryArtifact().getArtifactIdentity().getImage())
        .accountIdentifier(instance.getAccountIdentifier())
        .orgIdentifier(instance.getOrgIdentifier())
        .projectIdentifier(instance.getProjectIdentifier())
        .lastPipelineExecutionId(instance.getLastPipelineExecutionId())
        .lastPipelineExecutionName(instance.getLastPipelineExecutionName())
        .envIdentifier(instance.getEnvIdentifier())
        .envName(instance.getEnvName())
        .envType(EnvType.valueOf(instance.getEnvType().name()))
        .createdAt(Instant.now().toEpochMilli())
        .instanceIds(Collections.singleton(instance.getId()))
        .build();
  }
}
