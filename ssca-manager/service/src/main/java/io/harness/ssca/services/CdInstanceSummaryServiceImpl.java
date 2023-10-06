/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ssca.services;

import io.harness.entities.Instance;
import io.harness.repositories.CdInstanceSummaryRepo;
import io.harness.spec.server.ssca.v1.model.ArtifactDeploymentViewRequestBody;
import io.harness.ssca.beans.EnvType;
import io.harness.ssca.entities.ArtifactEntity;
import io.harness.ssca.entities.CdInstanceSummary;
import io.harness.ssca.entities.CdInstanceSummary.CdInstanceSummaryKeys;

import com.google.inject.Inject;
import java.time.Instant;
import java.util.Collections;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.query.Criteria;

@Slf4j
public class CdInstanceSummaryServiceImpl implements CdInstanceSummaryService {
  @Inject CdInstanceSummaryRepo cdInstanceSummaryRepo;

  @Override
  public boolean upsertInstance(Instance instance) {
    if (Objects.isNull(instance.getPrimaryArtifact().getArtifactIdentity())
        || Objects.isNull(instance.getPrimaryArtifact().getArtifactIdentity().getImage())) {
      return true;
    }

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
    return true;
  }

  @Override
  public boolean removeInstance(Instance instance) {
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
    return true;
  }

  @Override
  public Page<CdInstanceSummary> getCdInstanceSummaries(String accountId, String orgIdentifier,
      String projectIdentifier, ArtifactEntity artifact, ArtifactDeploymentViewRequestBody filterBody,
      Pageable pageable) {
    Criteria criteria = Criteria.where(CdInstanceSummaryKeys.artifactCorrelationId)
                            .is(artifact.getArtifactCorrelationId())
                            .and(CdInstanceSummaryKeys.accountIdentifier)
                            .is(accountId)
                            .and(CdInstanceSummaryKeys.orgIdentifier)
                            .is(orgIdentifier)
                            .and(CdInstanceSummaryKeys.projectIdentifier)
                            .is(projectIdentifier);

    return cdInstanceSummaryRepo.findAll(criteria, pageable);
  }

  private CdInstanceSummary createInstanceSummary(Instance instance) {
    return CdInstanceSummary.builder()
        .artifactCorrelationId(instance.getPrimaryArtifact().getArtifactIdentity().getImage())
        .accountIdentifier(instance.getAccountIdentifier())
        .orgIdentifier(instance.getOrgIdentifier())
        .projectIdentifier(instance.getProjectIdentifier())
        .lastPipelineExecutionId(instance.getLastPipelineExecutionId())
        .lastPipelineExecutionName(instance.getLastPipelineExecutionName())
        .lastDeployedById(instance.getLastDeployedById())
        .lastDeployedByName(instance.getLastDeployedByName())
        .lastDeployedAt(instance.getLastDeployedAt())
        .envIdentifier(instance.getEnvIdentifier())
        .envName(instance.getEnvName())
        .envType(EnvType.valueOf(instance.getEnvType().name()))
        .createdAt(Instant.now().toEpochMilli())
        .instanceIds(Collections.singleton(instance.getId()))
        .build();
  }
}
