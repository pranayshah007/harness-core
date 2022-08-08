/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.beans.connector;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.aws.AwsCFTemplatesType;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.connector.awsconnector.AwsCFTaskParamsRequest;
import io.harness.delegate.beans.connector.awsconnector.AwsConnectorDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsCredentialDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsCredentialType;
import io.harness.delegate.beans.connector.awsconnector.AwsManualConfigSpecDTO;
import io.harness.delegate.beans.connector.scm.genericgitconnector.GitConfigDTO;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.beans.executioncapability.GitConnectionNGCapability;
import io.harness.delegate.beans.executioncapability.HttpConnectionExecutionCapability;
import io.harness.delegate.beans.executioncapability.SelectorCapability;
import io.harness.delegate.beans.storeconfig.GitStoreDelegateConfig;
import io.harness.encryption.SecretRefData;
import io.harness.ng.core.dto.secrets.SSHKeySpecDTO;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;
import io.harness.security.encryption.EncryptedDataDetail;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.CDP)
public class AwsCFTaskParamsRequestTest extends CategoryTest {
  @Test
  @Owner(developers = OwnerRule.VLICA)
  @Category(UnitTests.class)
  public void testFetchingAWSCFExecutionCapabilities() {
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

    AwsCFTaskParamsRequest awsCFTaskParamsRequest =
        AwsCFTaskParamsRequest.builder()
            .gitStoreDelegateConfig(
                GitStoreDelegateConfig.builder()
                    .gitConfigDTO(GitConfigDTO.builder()
                                      .delegateSelectors(new HashSet<>(Arrays.asList("delegate-selector")))
                                      .build())
                    .encryptedDataDetails(Collections.singletonList(EncryptedDataDetail.builder().build()))
                    .sshKeySpecDTO(SSHKeySpecDTO.builder().build())
                    .build())
            .fileStoreType(AwsCFTemplatesType.GIT)
            .awsConnector(awsConnectorDTO)
            .build();

    List<ExecutionCapability> executionCapabilities = awsCFTaskParamsRequest.fetchRequiredExecutionCapabilities(null);
    assertThat(executionCapabilities.size()).isEqualTo(3);
    assertThat(executionCapabilities.get(0)).isInstanceOf(GitConnectionNGCapability.class);
    assertThat(executionCapabilities.get(0).getCapabilityType().name()).isEqualTo("GIT_CONNECTION_NG");
    assertThat(executionCapabilities.get(1)).isInstanceOf(SelectorCapability.class);
    assertThat(((SelectorCapability) executionCapabilities.get(1)).getSelectors().contains("delegate-selector"));
    assertThat(executionCapabilities.get(2)).isInstanceOf(HttpConnectionExecutionCapability.class);
    assertThat(((HttpConnectionExecutionCapability) executionCapabilities.get(2)).getHost())
        .isEqualTo("aws.amazon.com");
  }
}
