/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.connector.mappers.awsmapper;

import static io.harness.rule.OwnerRule.ABHINAV;
import static io.harness.rule.OwnerRule.ALLU_VAMSI;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.connector.entities.embedded.awsconnector.AwsAccessKeyCredential;
import io.harness.connector.entities.embedded.awsconnector.AwsConfig;
import io.harness.connector.entities.embedded.awsconnector.AwsEqualJitterBackoffStrategy;
import io.harness.connector.entities.embedded.awsconnector.AwsFixedDelayBackoffStrategy;
import io.harness.connector.entities.embedded.awsconnector.AwsFullJitterBackoffStrategy;
import io.harness.connector.entities.embedded.awsconnector.AwsIamCredential;
import io.harness.delegate.beans.connector.awsconnector.AwsConnectorDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsCredentialType;
import io.harness.delegate.beans.connector.awsconnector.AwsEqualJitterBackoffStrategySpecDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsFixedDelayBackoffStrategySpecDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsFullJitterBackoffStrategySpecDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsInheritFromDelegateSpecDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsManualConfigSpecDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsSdkClientBackoffStrategyDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsSdkClientBackoffStrategyType;
import io.harness.delegate.beans.connector.awsconnector.CrossAccountAccessDTO;
import io.harness.rule.Owner;

import java.util.Collections;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.MockitoAnnotations;

public class AwsEntityToDTOTest extends CategoryTest {
  @InjectMocks AwsEntityToDTO awsEntityToDTO;
  final String crossAccountRoleArn = "crossAccountRoleArn";
  final String externalRoleArn = "externalRoleArn";
  final String delegateSelector = "delegateSelector";
  final int RETRY_COUNT = 3;
  final long FIXED_BACKOFF = 100;
  final long BASE_DELAY = 100;
  final long MAX_BACKOFF_TIME = 100;

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = ABHINAV)
  @Category(UnitTests.class)
  public void testCreateConnectorDTO() {
    final CrossAccountAccessDTO crossAccountAccess =
        CrossAccountAccessDTO.builder().crossAccountRoleArn(crossAccountRoleArn).externalId(externalRoleArn).build();
    final AwsConfig awsConfig =
        AwsConfig.builder()
            .credentialType(AwsCredentialType.INHERIT_FROM_DELEGATE)
            .crossAccountAccess(crossAccountAccess)
            .credential(AwsIamCredential.builder().delegateSelectors(Collections.singleton(delegateSelector)).build())
            .build();
    final AwsConnectorDTO connectorDTO = awsEntityToDTO.createConnectorDTO(awsConfig);

    assertThat(connectorDTO).isNotNull();
    assertThat(connectorDTO.getCredential()).isNotNull();
    assertThat(connectorDTO.getCredential().getAwsCredentialType()).isEqualTo(AwsCredentialType.INHERIT_FROM_DELEGATE);
    assertThat((AwsInheritFromDelegateSpecDTO) connectorDTO.getCredential().getConfig()).isEqualTo(null);
    assertThat(connectorDTO.getCredential().getCrossAccountAccess()).isEqualTo(crossAccountAccess);

    final String accessKey = "accessKey";
    final AwsConfig awsConfig1 =
        AwsConfig.builder()
            .credentialType(AwsCredentialType.MANUAL_CREDENTIALS)
            .crossAccountAccess(crossAccountAccess)
            .credential(AwsAccessKeyCredential.builder().accessKey(accessKey).secretKeyRef("secretKey").build())
            .build();
    final AwsConnectorDTO connectorDTO1 = awsEntityToDTO.createConnectorDTO(awsConfig1);

    assertThat(connectorDTO1).isNotNull();
    assertThat(connectorDTO1.getCredential()).isNotNull();
    assertThat(connectorDTO1.getCredential().getAwsCredentialType()).isEqualTo(AwsCredentialType.MANUAL_CREDENTIALS);
    assertThat(((AwsManualConfigSpecDTO) connectorDTO1.getCredential().getConfig()).getAccessKey())
        .isEqualTo(accessKey);
    assertThat(connectorDTO1.getCredential().getCrossAccountAccess()).isEqualTo(crossAccountAccess);
  }

  @Test
  @Owner(developers = ALLU_VAMSI)
  @Category(UnitTests.class)
  public void testBackoffStrategyFixedDelayDTO() {
    final CrossAccountAccessDTO crossAccountAccess =
        CrossAccountAccessDTO.builder().crossAccountRoleArn(crossAccountRoleArn).externalId(externalRoleArn).build();
    AwsFixedDelayBackoffStrategy awsFixedDelayBackoffStrategy =
        AwsFixedDelayBackoffStrategy.builder().fixedBackoff(FIXED_BACKOFF).retryCount(RETRY_COUNT).build();
    final AwsConfig awsConfig =
        AwsConfig.builder()
            .credentialType(AwsCredentialType.INHERIT_FROM_DELEGATE)
            .crossAccountAccess(crossAccountAccess)
            .credential(AwsIamCredential.builder().delegateSelectors(Collections.singleton(delegateSelector)).build())
            .awsSdkClientBackoffStrategy(awsFixedDelayBackoffStrategy)
            .build();
    final AwsConnectorDTO connectorDTO = awsEntityToDTO.createConnectorDTO(awsConfig);
    AwsFixedDelayBackoffStrategySpecDTO awsFixedDelayBackoffStrategySpecDTO =
        AwsFixedDelayBackoffStrategySpecDTO.builder().fixedBackoff(FIXED_BACKOFF).retryCount(RETRY_COUNT).build();
    AwsSdkClientBackoffStrategyDTO awsSdkClientBackoffStrategyDTO =
        AwsSdkClientBackoffStrategyDTO.builder()
            .awsSdkClientBackoffStrategyType(AwsSdkClientBackoffStrategyType.FIXED_DELAY_BACKOFF_STRATEGY)
            .backoffStrategyConfig(awsFixedDelayBackoffStrategySpecDTO)
            .build();
    assertThat(connectorDTO.getAwsSdkClientBackOffStrategyOverride()).isEqualTo(awsSdkClientBackoffStrategyDTO);
  }

  @Test
  @Owner(developers = ALLU_VAMSI)
  @Category(UnitTests.class)
  public void testBackoffStrategyEqualJitterDTO() {
    final CrossAccountAccessDTO crossAccountAccess =
        CrossAccountAccessDTO.builder().crossAccountRoleArn(crossAccountRoleArn).externalId(externalRoleArn).build();
    AwsEqualJitterBackoffStrategy awsEqualJitterBackoffStrategy = AwsEqualJitterBackoffStrategy.builder()
                                                                      .baseDelay(BASE_DELAY)
                                                                      .maxBackoffTime(MAX_BACKOFF_TIME)
                                                                      .retryCount(RETRY_COUNT)
                                                                      .build();
    final AwsConfig awsConfig =
        AwsConfig.builder()
            .credentialType(AwsCredentialType.INHERIT_FROM_DELEGATE)
            .crossAccountAccess(crossAccountAccess)
            .credential(AwsIamCredential.builder().delegateSelectors(Collections.singleton(delegateSelector)).build())
            .awsSdkClientBackoffStrategy(awsEqualJitterBackoffStrategy)
            .build();
    final AwsConnectorDTO connectorDTO = awsEntityToDTO.createConnectorDTO(awsConfig);
    AwsEqualJitterBackoffStrategySpecDTO awsEqualJitterBackoffStrategySpecDTO =
        AwsEqualJitterBackoffStrategySpecDTO.builder()
            .baseDelay(BASE_DELAY)
            .maxBackoffTime(MAX_BACKOFF_TIME)
            .retryCount(RETRY_COUNT)
            .build();
    AwsSdkClientBackoffStrategyDTO awsSdkClientBackoffStrategyDTO =
        AwsSdkClientBackoffStrategyDTO.builder()
            .awsSdkClientBackoffStrategyType(AwsSdkClientBackoffStrategyType.EQUAL_JITTER_BACKOFF_STRATEGY)
            .backoffStrategyConfig(awsEqualJitterBackoffStrategySpecDTO)
            .build();
    assertThat(connectorDTO.getAwsSdkClientBackOffStrategyOverride()).isEqualTo(awsSdkClientBackoffStrategyDTO);
  }

  @Test
  @Owner(developers = ALLU_VAMSI)
  @Category(UnitTests.class)
  public void testBackoffStrategyFullJitterDTO() {
    final CrossAccountAccessDTO crossAccountAccess =
        CrossAccountAccessDTO.builder().crossAccountRoleArn(crossAccountRoleArn).externalId(externalRoleArn).build();
    AwsFullJitterBackoffStrategy awsFullJitterBackoffStrategy = AwsFullJitterBackoffStrategy.builder()
                                                                    .baseDelay(BASE_DELAY)
                                                                    .maxBackoffTime(MAX_BACKOFF_TIME)
                                                                    .retryCount(RETRY_COUNT)
                                                                    .build();
    final AwsConfig awsConfig =
        AwsConfig.builder()
            .credentialType(AwsCredentialType.INHERIT_FROM_DELEGATE)
            .crossAccountAccess(crossAccountAccess)
            .credential(AwsIamCredential.builder().delegateSelectors(Collections.singleton(delegateSelector)).build())
            .awsSdkClientBackoffStrategy(awsFullJitterBackoffStrategy)
            .build();
    final AwsConnectorDTO connectorDTO = awsEntityToDTO.createConnectorDTO(awsConfig);
    AwsFullJitterBackoffStrategySpecDTO awsFullJitterBackoffStrategySpecDTO =
        AwsFullJitterBackoffStrategySpecDTO.builder()
            .baseDelay(BASE_DELAY)
            .maxBackoffTime(MAX_BACKOFF_TIME)
            .retryCount(RETRY_COUNT)
            .build();
    AwsSdkClientBackoffStrategyDTO awsSdkClientBackoffStrategyDTO =
        AwsSdkClientBackoffStrategyDTO.builder()
            .awsSdkClientBackoffStrategyType(AwsSdkClientBackoffStrategyType.FULL_JITTER_BACKOFF_STRATEGY)
            .backoffStrategyConfig(awsFullJitterBackoffStrategySpecDTO)
            .build();
    assertThat(connectorDTO.getAwsSdkClientBackOffStrategyOverride()).isEqualTo(awsSdkClientBackoffStrategyDTO);
  }
}
