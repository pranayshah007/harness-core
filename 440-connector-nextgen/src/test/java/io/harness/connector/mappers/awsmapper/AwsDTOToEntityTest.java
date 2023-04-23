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
import io.harness.delegate.beans.connector.awsconnector.AwsConnectorDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsCredentialDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsCredentialType;
import io.harness.delegate.beans.connector.awsconnector.AwsEqualJitterBackoffStrategySpecDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsFixedDelayBackoffStrategySpecDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsFullJitterBackoffStrategySpecDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsInheritFromDelegateSpecDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsManualConfigSpecDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsSdkClientBackoffStrategyDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsSdkClientBackoffStrategyType;
import io.harness.delegate.beans.connector.awsconnector.CrossAccountAccessDTO;
import io.harness.encryption.SecretRefData;
import io.harness.rule.Owner;

import java.util.Collections;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.MockitoAnnotations;

public class AwsDTOToEntityTest extends CategoryTest {
  @InjectMocks AwsDTOToEntity awsDTOToEntity;
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
  public void testToConnectorEntity() {
    final CrossAccountAccessDTO crossAccountAccess =
        CrossAccountAccessDTO.builder().crossAccountRoleArn(crossAccountRoleArn).externalId(externalRoleArn).build();
    final AwsCredentialDTO awsCredentialDTO = AwsCredentialDTO.builder()
                                                  .awsCredentialType(AwsCredentialType.INHERIT_FROM_DELEGATE)
                                                  .crossAccountAccess(crossAccountAccess)
                                                  .config(AwsInheritFromDelegateSpecDTO.builder().build())
                                                  .build();
    final AwsConnectorDTO awsConnectorDTO = AwsConnectorDTO.builder()
                                                .credential(awsCredentialDTO)
                                                .delegateSelectors(Collections.singleton(delegateSelector))
                                                .build();
    final AwsConfig awsConfig = awsDTOToEntity.toConnectorEntity(awsConnectorDTO);

    assertThat(awsConfig).isNotNull();
    assertThat(awsConfig.getCredentialType()).isEqualTo(AwsCredentialType.INHERIT_FROM_DELEGATE);
    assertThat(awsConfig.getCrossAccountAccess()).isEqualTo(crossAccountAccess);
    assertThat(awsConfig.getCredential()).isNull();
    assertThat(awsConnectorDTO.getDelegateSelectors()).isEqualTo(Collections.singleton(delegateSelector));

    final String accessKey = "accessKey";
    final AwsCredentialDTO awsCredentialDTO1 = AwsCredentialDTO.builder()
                                                   .awsCredentialType(AwsCredentialType.MANUAL_CREDENTIALS)
                                                   .crossAccountAccess(crossAccountAccess)
                                                   .config(AwsManualConfigSpecDTO.builder()
                                                               .accessKey(accessKey)
                                                               .secretKeyRef(SecretRefData.builder().build())
                                                               .build())
                                                   .build();
    final AwsConnectorDTO awsConnectorDTO1 = AwsConnectorDTO.builder().credential(awsCredentialDTO1).build();
    final AwsConfig awsConfig1 = awsDTOToEntity.toConnectorEntity(awsConnectorDTO1);

    assertThat(awsConfig1).isNotNull();
    assertThat(awsConfig1.getCredentialType()).isEqualTo(AwsCredentialType.MANUAL_CREDENTIALS);
    assertThat(awsConfig1.getCrossAccountAccess()).isEqualTo(crossAccountAccess);
    assertThat(awsConfig1.getCredential()).isNotNull();
    assertThat(((AwsAccessKeyCredential) awsConfig1.getCredential()).getAccessKey()).isEqualTo(accessKey);
  }

  @Test
  @Owner(developers = ALLU_VAMSI)
  @Category(UnitTests.class)
  public void buildAwsSdkClientBackoffFixedDelayStrategyTest() {
    AwsFixedDelayBackoffStrategySpecDTO awsFixedDelayBackoffStrategySpecDTO =
        AwsFixedDelayBackoffStrategySpecDTO.builder().fixedBackoff(FIXED_BACKOFF).retryCount(RETRY_COUNT).build();
    AwsSdkClientBackoffStrategyDTO awsSdkClientBackoffStrategyDTO =
        AwsSdkClientBackoffStrategyDTO.builder()
            .awsSdkClientBackoffStrategyType(AwsSdkClientBackoffStrategyType.FIXED_DELAY_BACKOFF_STRATEGY)
            .backoffStrategyConfig(awsFixedDelayBackoffStrategySpecDTO)
            .build();
    final CrossAccountAccessDTO crossAccountAccess =
        CrossAccountAccessDTO.builder().crossAccountRoleArn(crossAccountRoleArn).externalId(externalRoleArn).build();
    final AwsCredentialDTO awsCredentialDTO = AwsCredentialDTO.builder()
                                                  .awsCredentialType(AwsCredentialType.INHERIT_FROM_DELEGATE)
                                                  .crossAccountAccess(crossAccountAccess)
                                                  .config(AwsInheritFromDelegateSpecDTO.builder().build())
                                                  .build();
    final AwsConnectorDTO awsConnectorDTO = AwsConnectorDTO.builder()
                                                .credential(awsCredentialDTO)
                                                .awsSdkClientBackOffStrategyOverride(awsSdkClientBackoffStrategyDTO)
                                                .delegateSelectors(Collections.singleton(delegateSelector))
                                                .build();
    AwsConfig awsConfig = awsDTOToEntity.toConnectorEntity(awsConnectorDTO);
    AwsFixedDelayBackoffStrategy awsFixedDelayBackoffStrategy =
        AwsFixedDelayBackoffStrategy.builder()
            .fixedBackoff(awsFixedDelayBackoffStrategySpecDTO.getFixedBackoff())
            .retryCount(awsFixedDelayBackoffStrategySpecDTO.getRetryCount())
            .build();
    assertThat(awsConfig.getAwsSdkClientBackoffStrategy()).isEqualTo(awsFixedDelayBackoffStrategy);
  }

  @Test
  @Owner(developers = ALLU_VAMSI)
  @Category(UnitTests.class)
  public void buildAwsSdkClientBackoffEqualJitterStrategyTest() {
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
    final CrossAccountAccessDTO crossAccountAccess =
        CrossAccountAccessDTO.builder().crossAccountRoleArn(crossAccountRoleArn).externalId(externalRoleArn).build();
    final AwsCredentialDTO awsCredentialDTO = AwsCredentialDTO.builder()
                                                  .awsCredentialType(AwsCredentialType.INHERIT_FROM_DELEGATE)
                                                  .crossAccountAccess(crossAccountAccess)
                                                  .config(AwsInheritFromDelegateSpecDTO.builder().build())
                                                  .build();
    final AwsConnectorDTO awsConnectorDTO = AwsConnectorDTO.builder()
                                                .credential(awsCredentialDTO)
                                                .awsSdkClientBackOffStrategyOverride(awsSdkClientBackoffStrategyDTO)
                                                .delegateSelectors(Collections.singleton(delegateSelector))
                                                .build();
    AwsConfig awsConfig = awsDTOToEntity.toConnectorEntity(awsConnectorDTO);
    AwsEqualJitterBackoffStrategy awsEqualJitterBackoffStrategy =
        AwsEqualJitterBackoffStrategy.builder()
            .baseDelay(awsEqualJitterBackoffStrategySpecDTO.getBaseDelay())
            .maxBackoffTime(awsEqualJitterBackoffStrategySpecDTO.getMaxBackoffTime())
            .retryCount(awsEqualJitterBackoffStrategySpecDTO.getRetryCount())
            .build();
    assertThat(awsConfig.getAwsSdkClientBackoffStrategy()).isEqualTo(awsEqualJitterBackoffStrategy);
  }

  @Test
  @Owner(developers = ALLU_VAMSI)
  @Category(UnitTests.class)
  public void buildAwsSdkClientBackoffFullJitterStrategyTest() {
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
    final CrossAccountAccessDTO crossAccountAccess =
        CrossAccountAccessDTO.builder().crossAccountRoleArn(crossAccountRoleArn).externalId(externalRoleArn).build();
    final AwsCredentialDTO awsCredentialDTO = AwsCredentialDTO.builder()
                                                  .awsCredentialType(AwsCredentialType.INHERIT_FROM_DELEGATE)
                                                  .crossAccountAccess(crossAccountAccess)
                                                  .config(AwsInheritFromDelegateSpecDTO.builder().build())
                                                  .build();
    final AwsConnectorDTO awsConnectorDTO = AwsConnectorDTO.builder()
                                                .credential(awsCredentialDTO)
                                                .awsSdkClientBackOffStrategyOverride(awsSdkClientBackoffStrategyDTO)
                                                .delegateSelectors(Collections.singleton(delegateSelector))
                                                .build();
    AwsConfig awsConfig = awsDTOToEntity.toConnectorEntity(awsConnectorDTO);
    AwsFullJitterBackoffStrategy awsFullJitterBackoffStrategy =
        AwsFullJitterBackoffStrategy.builder()
            .baseDelay(awsFullJitterBackoffStrategySpecDTO.getBaseDelay())
            .maxBackoffTime(awsFullJitterBackoffStrategySpecDTO.getMaxBackoffTime())
            .retryCount(awsFullJitterBackoffStrategySpecDTO.getRetryCount())
            .build();
    assertThat(awsConfig.getAwsSdkClientBackoffStrategy()).isEqualTo(awsFullJitterBackoffStrategy);
  }
}
