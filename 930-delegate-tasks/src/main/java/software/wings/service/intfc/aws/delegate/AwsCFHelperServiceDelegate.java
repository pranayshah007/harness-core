/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.intfc.aws.delegate;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.aws.beans.AwsInternalConfig;
import io.harness.security.encryption.EncryptedDataDetail;

import software.wings.beans.GitConfig;
import software.wings.beans.GitFileConfig;
import software.wings.service.impl.aws.model.AwsCFTemplateParamsData;

import java.util.List;

@TargetModule(HarnessModule._930_DELEGATE_TASKS)
@OwnedBy(CDP)
public interface AwsCFHelperServiceDelegate {
  String getStackBody(AwsInternalConfig awsConfig, String region, String stackId);

  List<AwsCFTemplateParamsData> getParamsData(AwsInternalConfig awsConfig, String region, String data, String type,
      GitFileConfig gitFileConfig, GitConfig gitConfig, List<EncryptedDataDetail> sourceRepoEncryptedDetail);

  List<String> getCapabilities(AwsInternalConfig awsConfig, String region, String data, String templateType);

  String normalizeS3TemplatePath(String s3Path);
}
