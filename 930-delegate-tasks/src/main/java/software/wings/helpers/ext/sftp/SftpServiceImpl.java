/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.helpers.ext.sftp;

import static io.harness.annotations.dev.HarnessModule._960_API_SERVICES;
import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.exception.WingsException.USER;

import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.InvalidArtifactServerException;
import io.harness.security.encryption.EncryptedDataDetail;

import software.wings.beans.SftpConfig;
import software.wings.helpers.ext.jenkins.BuildDetails;
import software.wings.service.impl.SftpHelperService;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(CDC)
@TargetModule(_960_API_SERVICES)
@Singleton
@Slf4j
public class SftpServiceImpl implements SftpService {
  @Inject private SftpHelperService sftpHelperService;

  @Override
  public List<String> getArtifactPaths(SftpConfig sftpConfig, List<EncryptedDataDetail> encryptionDetails) {
    try {
      return sftpHelperService.getSftpPaths(sftpConfig, encryptionDetails);
    } catch (Exception ex) {
      throw new InvalidArtifactServerException("Could not get Artifact paths from SFTP Storage.", USER);
    }
  }

  @Override
  public boolean isRunning(SftpConfig sftpConfig, List<EncryptedDataDetail> encryptionDetails) {
    return true;
  }

  @Override
  public List<BuildDetails> getBuildDetails(SftpConfig sftpConfig, List<EncryptedDataDetail> encryptionDetails,
      List<String> artifactPaths, boolean isExpression) {
    List<BuildDetails> buildDetailsList;
    try {
      buildDetailsList = sftpHelperService.getArtifactDetails(sftpConfig, encryptionDetails, artifactPaths);
    } catch (Exception e) {
      log.error("Error while retrieving artifacts build details from SFTP Server : {}", sftpConfig.getSftpUrl());
      throw new InvalidArtifactServerException(ExceptionUtils.getMessage(e), USER);
    }
    return buildDetailsList;
  }
}
