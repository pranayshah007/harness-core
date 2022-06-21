/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.verificationjob.services.impl;

import static io.harness.cvng.beans.job.VerificationJobType.HEALTH;
import static io.harness.exception.WingsException.USER_SRE;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cvng.beans.job.VerificationJobDTO;
import io.harness.cvng.verificationjob.entities.BlueGreenVerificationJob;
import io.harness.cvng.verificationjob.entities.CanaryVerificationJob;
import io.harness.cvng.verificationjob.entities.HealthVerificationJob;
import io.harness.cvng.verificationjob.entities.TestVerificationJob;
import io.harness.cvng.verificationjob.entities.VerificationJob;
import io.harness.cvng.verificationjob.entities.VerificationJob.VerificationJobKeys;
import io.harness.cvng.verificationjob.services.api.VerificationJobService;
import io.harness.exception.DuplicateFieldException;
import io.harness.persistence.HPersistence;

import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import com.mongodb.DuplicateKeyException;
import javax.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.CV)
public class VerificationJobServiceImpl implements VerificationJobService {
  @Inject private HPersistence hPersistence;

  @Override
  @Nullable
  public VerificationJobDTO getVerificationJobDTO(
      String accountId, String orgIdentifier, String projectIdentifier, String identifier) {
    VerificationJob verificationJob = getVerificationJob(accountId, orgIdentifier, projectIdentifier, identifier);
    if (verificationJob == null) {
      return null;
    }
    return verificationJob.getVerificationJobDTO();
  }

  @Override
  public void create(String accountId, VerificationJobDTO verificationJobDTO) {
    VerificationJob verificationJob = fromDto(verificationJobDTO);
    verificationJob.setAccountId(accountId);
    try {
      verificationJob.validate();
      hPersistence.save(verificationJob);
    } catch (DuplicateKeyException ex) {
      throw new DuplicateFieldException(
          String.format(
              "A Verification Job  with identifier %s and orgIdentifier %s and projectIdentifier %s is already present",
              verificationJob.getIdentifier(), verificationJob.getOrgIdentifier(),
              verificationJob.getProjectIdentifier()),
          USER_SRE, ex);
    }
  }

  @Override
  public void save(VerificationJob verificationJob) {
    hPersistence.save(verificationJob);
  }

  @Override
  public VerificationJob getVerificationJob(
      String accountId, String orgIdentifier, String projectIdentifier, String identifier) {
    Preconditions.checkNotNull(accountId);
    Preconditions.checkNotNull(orgIdentifier);
    Preconditions.checkNotNull(projectIdentifier);
    Preconditions.checkNotNull(identifier);
    return hPersistence.createQuery(VerificationJob.class)
        .filter(VerificationJobKeys.accountId, accountId)
        .filter(VerificationJobKeys.orgIdentifier, orgIdentifier)
        .filter(VerificationJobKeys.projectIdentifier, projectIdentifier)
        .filter(VerificationJobKeys.identifier, identifier)
        .get();
  }

  @Override
  public VerificationJob getResolvedHealthVerificationJob(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, String envIdentifier, String serviceIdentifier) {
    Preconditions.checkNotNull(accountIdentifier);
    Preconditions.checkNotNull(orgIdentifier);
    Preconditions.checkNotNull(projectIdentifier);
    Preconditions.checkNotNull(envIdentifier);
    Preconditions.checkNotNull(serviceIdentifier);
    VerificationJob defaultHealthVerificationJob =
        getDefaultHealthVerificationJob(accountIdentifier, orgIdentifier, projectIdentifier);
    defaultHealthVerificationJob.setServiceIdentifier(serviceIdentifier, false);
    defaultHealthVerificationJob.setEnvIdentifier(envIdentifier, false);
    defaultHealthVerificationJob.setDuration("15m", false);
    return defaultHealthVerificationJob;
  }

  @Override
  public VerificationJob getDefaultHealthVerificationJob(
      String accountId, String orgIdentifier, String projectIdentifier) {
    VerificationJob defaultJob = hPersistence.createQuery(VerificationJob.class)
                                     .filter(VerificationJobKeys.accountId, accountId)
                                     .filter(VerificationJobKeys.projectIdentifier, projectIdentifier)
                                     .filter(VerificationJobKeys.orgIdentifier, orgIdentifier)
                                     .filter(VerificationJobKeys.type, HEALTH)
                                     .filter(VerificationJobKeys.isDefaultJob, true)
                                     .get();
    Preconditions.checkNotNull(defaultJob,
        String.format(
            "Default Health job cannot be null for accountIdentifier [%s], orgIdentifier [%s], projectIdentifier [%s]",
            accountId, orgIdentifier, projectIdentifier));
    return defaultJob;
  }

  @Override
  public VerificationJob fromDto(VerificationJobDTO verificationJobDTO) {
    Preconditions.checkNotNull(verificationJobDTO);
    VerificationJob job;
    switch (verificationJobDTO.getType()) {
      case HEALTH:
        job = new HealthVerificationJob();
        break;
      case CANARY:
        job = new CanaryVerificationJob();
        break;
      case TEST:
        job = new TestVerificationJob();
        break;
      case BLUE_GREEN:
        job = new BlueGreenVerificationJob();
        break;
      default:
        throw new IllegalStateException("Invalid type " + verificationJobDTO.getType());
    }
    job.fromDTO(verificationJobDTO);
    return job;
  }

  @Override
  public void createDefaultVerificationJobs(String accountId, String orgIdentifier, String projectIdentifier) {
    saveDefaultJob(HealthVerificationJob.createDefaultJob(accountId, orgIdentifier, projectIdentifier));
    saveDefaultJob(TestVerificationJob.createDefaultJob(accountId, orgIdentifier, projectIdentifier));
    saveDefaultJob(CanaryVerificationJob.createDefaultJob(accountId, orgIdentifier, projectIdentifier));
    saveDefaultJob(BlueGreenVerificationJob.createDefaultJob(accountId, orgIdentifier, projectIdentifier));
  }

  private void saveDefaultJob(VerificationJob verificationJob) {
    try {
      verificationJob.validate();
      hPersistence.save(verificationJob);
    } catch (DuplicateKeyException ex) {
      log.info(String.format(
          "A Default Verification Job  with identifier %s and orgIdentifier %s and projectIdentifier %s is already present",
          verificationJob.getIdentifier(), verificationJob.getOrgIdentifier(), verificationJob.getProjectIdentifier()));
    }
  }
}
