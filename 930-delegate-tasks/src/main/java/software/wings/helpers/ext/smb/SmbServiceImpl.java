/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.helpers.ext.smb;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.eraro.ErrorCode.INVALID_ARTIFACT_SERVER;
import static io.harness.exception.WingsException.USER;

import io.harness.annotations.dev.OwnedBy;
import io.harness.eraro.ErrorCode;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.InvalidArtifactServerException;
import io.harness.exception.WingsException;
import io.harness.security.encryption.EncryptedDataDetail;

import software.wings.beans.SmbConfig;
import software.wings.helpers.ext.jenkins.BuildDetails;
import software.wings.service.impl.SmbHelperService;

import com.google.common.collect.Lists;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.hierynomus.mssmb2.SMBApiException;
import java.util.Collections;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(CDC)
@Singleton
@Slf4j
public class SmbServiceImpl implements SmbService {
  @Inject private SmbHelperService smbHelperService;

  @Override
  public List<String> getArtifactPaths(SmbConfig smbConfig, List<EncryptedDataDetail> encryptionDetails) {
    List<String> pathList = Collections.EMPTY_LIST;
    try {
      pathList = smbHelperService.getSmbPaths(smbConfig, encryptionDetails);
    } catch (Exception ex) {
      throw new WingsException(ErrorCode.INVALID_ARTIFACT_SERVER, WingsException.USER)
          .addParam("message", "Could not get Artifact paths from SMB Storage.");
    }
    return pathList;
  }

  @Override
  public boolean isRunning(SmbConfig smbConfig, List<EncryptedDataDetail> encryptionDetails) {
    try {
      smbHelperService.checkConnection(smbConfig, encryptionDetails);
    } catch (SMBApiException SMBe) {
      throw new InvalidArtifactServerException("Invalid Samba Server credentials", SMBe);
    } catch (Exception e) {
      throw new InvalidArtifactServerException(ExceptionUtils.getMessage(e), e);
    }
    return true;
  }

  @Override
  public List<BuildDetails> getBuildDetails(SmbConfig smbConfig, List<EncryptedDataDetail> encryptionDetails,
      List<String> artifactPaths, boolean isExpression) {
    List<BuildDetails> buildDetailsList = Lists.newArrayList();
    try {
      buildDetailsList = smbHelperService.getArtifactDetails(smbConfig, encryptionDetails, artifactPaths);
    } catch (Exception e) {
      throw new WingsException(INVALID_ARTIFACT_SERVER, USER).addParam("message", ExceptionUtils.getMessage(e));
    }
    return buildDetailsList;
  }
}
