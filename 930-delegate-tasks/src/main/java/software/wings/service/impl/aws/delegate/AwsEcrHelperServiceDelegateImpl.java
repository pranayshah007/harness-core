/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.aws.delegate;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.security.encryption.EncryptedDataDetail;

import software.wings.beans.AwsConfig;
import software.wings.service.impl.delegate.AwsEcrApiHelperServiceDelegate;
import software.wings.service.intfc.aws.delegate.AwsEcrHelperServiceDelegate;
import software.wings.service.mappers.artifact.AwsConfigToInternalMapper;

import com.amazonaws.services.ecr.AmazonECRClient;
import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;

@OwnedBy(CDC)
@Singleton
@TargetModule(HarnessModule._930_DELEGATE_TASKS)
public class AwsEcrHelperServiceDelegateImpl
    extends AwsHelperServiceDelegateBase implements AwsEcrHelperServiceDelegate {
  @Inject AwsEcrApiHelperServiceDelegate awsEcrApiHelperServiceDelegate;

  @VisibleForTesting
  AmazonECRClient getAmazonEcrClient(AwsConfig awsConfig, String region) {
    return awsEcrApiHelperServiceDelegate.getAmazonEcrClient(
        AwsConfigToInternalMapper.toAwsInternalConfig(awsConfig), region);
  }

  @Override
  public String getEcrImageUrl(
      AwsConfig awsConfig, List<EncryptedDataDetail> encryptionDetails, String region, String imageName) {
    encryptionService.decrypt(awsConfig, encryptionDetails, false);
    return awsEcrApiHelperServiceDelegate.getEcrImageUrl(
        AwsConfigToInternalMapper.toAwsInternalConfig(awsConfig), region, imageName);
  }

  @Override
  public String getAmazonEcrAuthToken(
      AwsConfig awsConfig, List<EncryptedDataDetail> encryptionDetails, String awsAccount, String region) {
    encryptionService.decrypt(awsConfig, encryptionDetails, false);
    return awsEcrApiHelperServiceDelegate.getAmazonEcrAuthToken(
        AwsConfigToInternalMapper.toAwsInternalConfig(awsConfig), awsAccount, region);
  }
}
