/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.verificationjob.services.api;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cvng.beans.job.VerificationJobDTO;
import io.harness.cvng.verificationjob.entities.VerificationJob;
@OwnedBy(HarnessTeam.CV)
public interface VerificationJobService {
  VerificationJobDTO getVerificationJobDTO(
      String accountId, String orgIdentifier, String projectIdentifier, String identifier);
  VerificationJob getVerificationJob(
      String accountId, String orgIdentifier, String projectIdentifier, String identifier);
  void create(String accountId, VerificationJobDTO verificationJobDTO);
  void save(VerificationJob verificationJob);

  VerificationJob getResolvedHealthVerificationJob(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, String envIdentifier, String serviceIdentifier);
  VerificationJob getDefaultHealthVerificationJob(String accountId, String orgIdentifier, String projectIdentifier);

  VerificationJob fromDto(VerificationJobDTO verificationJobDTO);
  void createDefaultVerificationJobs(String accountId, String orgIdentifier, String projectIdentifier);
}
