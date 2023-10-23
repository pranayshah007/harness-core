/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.helpers.ext.ecr;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.OwnedBy;
import io.harness.security.encryption.EncryptedDataDetail;

import software.wings.beans.EcrConfig;
import software.wings.helpers.ext.jenkins.BuildDetails;

import java.util.List;

/**
 * Created by brett on 7/15/17
 */
@OwnedBy(CDC)
public interface EcrClassicService {
  /**
   * Gets builds.
   *
   * @param ecrConfig         the ecr config
   * @param imageName         the image name
   * @param maxNumberOfBuilds the max number of builds
   * @return the builds
   */
  List<BuildDetails> getBuilds(
      EcrConfig ecrConfig, List<EncryptedDataDetail> encryptionDetails, String imageName, int maxNumberOfBuilds);

  /**
   * Gets last successful build.
   *
   * @param ecrConfig the ecr config
   * @param imageName the image name
   * @return the last successful build
   */
  BuildDetails getLastSuccessfulBuild(
      EcrConfig ecrConfig, List<EncryptedDataDetail> encryptionDetails, String imageName);

  /**
   * Validates the Image
   *
   * @param ecrConfig the ecr config
   * @param imageName the image name
   * @return the boolean
   */
  boolean verifyRepository(EcrConfig ecrConfig, List<EncryptedDataDetail> encryptionDetails, String imageName);

  /**
   * Validate the credentials
   *
   * @param ecrConfig the ecr config
   * @return boolean
   */
  boolean validateCredentials(EcrConfig ecrConfig, List<EncryptedDataDetail> encryptionDetails);

  /**
   * List ecr registry list.
   *
   * @param ecrConfig the ecr config
   * @return the list
   */
  List<String> listEcrRegistry(EcrConfig ecrConfig, List<EncryptedDataDetail> encryptionDetails);

  /**
   * Get the ECR repository info for the given name
   *
   * @param ecrConfig         ecr artifact server / ConnectorDisconnectHandler config
   * @param imageName
   * @return ecr image url
   */
  String getEcrImageUrl(EcrConfig ecrConfig, String imageName);
}
