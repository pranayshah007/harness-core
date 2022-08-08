/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.aws.model;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.security.encryption.EncryptedDataDetail;

import software.wings.beans.AwsConfig;

import java.util.List;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

@OwnedBy(CDC)
@Data
@EqualsAndHashCode(callSuper = true)
@TargetModule(HarnessModule._950_DELEGATE_TASKS_BEANS)
public class AwsEcrGetAuthTokenRequest extends AwsEcrRequest {
  private String awsAccount;

  @Builder
  public AwsEcrGetAuthTokenRequest(
      AwsConfig awsConfig, List<EncryptedDataDetail> encryptionDetails, String region, String awsAccount) {
    super(awsConfig, encryptionDetails, AwsEcrRequestType.GET_ECR_AUTH_TOKEN, region);
    this.awsAccount = awsAccount;
  }
}
