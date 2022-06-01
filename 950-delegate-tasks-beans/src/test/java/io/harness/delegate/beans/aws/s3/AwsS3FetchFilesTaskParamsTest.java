/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.beans.aws.s3;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.connector.awsconnector.AwsConnectorDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsCredentialDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsCredentialType;
import io.harness.delegate.beans.connector.awsconnector.AwsManualConfigSpecDTO;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.beans.executioncapability.HttpConnectionExecutionCapability;
import io.harness.encryption.SecretRefData;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;

import java.util.Collections;
import java.util.List;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.CDP)
public class AwsS3FetchFilesTaskParamsTest extends CategoryTest {
  @Test
  @Owner(developers = OwnerRule.VLICA)
  @Category(UnitTests.class)
  public void testFetchRequiredExecutionCapabilitiesWithAWSConnector() {
    AwsConnectorDTO awsConnectorDTO =
        AwsConnectorDTO.builder()
            .credential(
                AwsCredentialDTO.builder()
                    .awsCredentialType(AwsCredentialType.MANUAL_CREDENTIALS)
                    .config(AwsManualConfigSpecDTO.builder()
                                .accessKey("test-access-key")
                                .secretKeyRef(SecretRefData.builder().decryptedValue("secret".toCharArray()).build())
                                .build())
                    .build())
            .build();

    AwsS3FetchFileDelegateConfig awsS3FetchFileDelegateConfig =
        AwsS3FetchFileDelegateConfig.builder()
            .identifier("test-s3-identifier")
            .region("test-aws-region")
            .fileDetails(Collections.singletonList(
                S3FileDetailRequest.builder().fileKey("test-fileKey").bucketName("test-bucketName").build()))
            .awsConnector(awsConnectorDTO)
            .build();

    AwsS3FetchFilesTaskParams awsS3FetchFilesTaskParams =
        AwsS3FetchFilesTaskParams.builder()
            .fetchFileDelegateConfigs(Collections.singletonList(awsS3FetchFileDelegateConfig))
            .build();

    List<ExecutionCapability> executionCapabilities =
        awsS3FetchFilesTaskParams.fetchRequiredExecutionCapabilities(null);

    assertThat(executionCapabilities.size()).isEqualTo(1);
    assertThat(executionCapabilities.get(0)).isInstanceOf(HttpConnectionExecutionCapability.class);
    assertThat(((HttpConnectionExecutionCapability) executionCapabilities.get(0)).getHost())
        .isEqualTo("aws.amazon.com");
  }
}
