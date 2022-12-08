/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.api.terraform;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.provision.TfVarSource;
import io.harness.security.encryption.EncryptedDataDetail;

import software.wings.beans.AwsConfig;
import software.wings.beans.S3FileConfig;

import java.util.List;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

@OwnedBy(HarnessTeam.CDP)
@Data
@Builder
@EqualsAndHashCode(of = "s3FileConfig")
@TargetModule(HarnessModule._950_DELEGATE_TASKS_BEANS)
public class TfVarS3Source implements TfVarSource {
  private AwsConfig awsConfig;
  private S3FileConfig s3FileConfig;
  private List<EncryptedDataDetail> encryptedDataDetails;

  @Override
  public TfVarSourceType getTfVarSourceType() {
    return TfVarSourceType.S3;
  }
}
