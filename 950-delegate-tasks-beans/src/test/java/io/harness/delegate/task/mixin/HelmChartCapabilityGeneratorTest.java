/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.mixin;

import static io.harness.rule.OwnerRule.ABOSII;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.connector.awsconnector.AwsConnectorDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsCredentialDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsCredentialType;
import io.harness.delegate.beans.connector.awsconnector.AwsManualConfigSpecDTO;
import io.harness.delegate.beans.connector.gcpconnector.GcpConnectorCredentialDTO;
import io.harness.delegate.beans.connector.gcpconnector.GcpConnectorDTO;
import io.harness.delegate.beans.connector.gcpconnector.GcpCredentialType;
import io.harness.delegate.beans.connector.gcpconnector.GcpManualDetailsDTO;
import io.harness.delegate.beans.connector.helm.HttpHelmConnectorDTO;
import io.harness.delegate.beans.connector.scm.GitAuthType;
import io.harness.delegate.beans.connector.scm.genericgitconnector.GitConfigDTO;
import io.harness.delegate.beans.connector.scm.genericgitconnector.GitHTTPAuthenticationDTO;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.beans.executioncapability.GitConnectionNGCapability;
import io.harness.delegate.beans.executioncapability.HelmInstallationCapability;
import io.harness.delegate.beans.executioncapability.HttpConnectionExecutionCapability;
import io.harness.delegate.beans.storeconfig.GcsHelmStoreDelegateConfig;
import io.harness.delegate.beans.storeconfig.GitStoreDelegateConfig;
import io.harness.delegate.beans.storeconfig.HttpHelmStoreDelegateConfig;
import io.harness.delegate.beans.storeconfig.S3HelmStoreDelegateConfig;
import io.harness.delegate.task.k8s.HelmChartManifestDelegateConfig;
import io.harness.expression.ExpressionEvaluator;
import io.harness.k8s.model.HelmVersion;
import io.harness.rule.Owner;

import java.util.ArrayList;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

public class HelmChartCapabilityGeneratorTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  @Mock private ExpressionEvaluator expressionEvaluator;

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void generateCapabilitiesGit() {
    HelmChartManifestDelegateConfig manifestDelegateConfig =
        HelmChartManifestDelegateConfig.builder()
            .helmVersion(HelmVersion.V3)
            .storeDelegateConfig(GitStoreDelegateConfig.builder()
                                     .gitConfigDTO(GitConfigDTO.builder()
                                                       .url("https://git.com")
                                                       .gitAuthType(GitAuthType.HTTP)
                                                       .gitAuth(GitHTTPAuthenticationDTO.builder().build())
                                                       .build())
                                     .encryptedDataDetails(new ArrayList<>())
                                     .build())
            .build();

    List<ExecutionCapability> capabilityList =
        HelmChartCapabilityGenerator.generateCapabilities(manifestDelegateConfig, expressionEvaluator);
    assertThat(capabilityList).hasSize(2);
    assertThat(capabilityList.stream().map(Object::getClass))
        .containsExactlyInAnyOrder(HelmInstallationCapability.class, GitConnectionNGCapability.class);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void generateCapabilitiesHttpHelm() {
    HelmChartManifestDelegateConfig manifestDelegateConfig =
        HelmChartManifestDelegateConfig.builder()
            .helmVersion(HelmVersion.V3)
            .storeDelegateConfig(
                HttpHelmStoreDelegateConfig.builder()
                    .httpHelmConnector(
                        HttpHelmConnectorDTO.builder().helmRepoUrl("https://test.helm.repo/charts").build())
                    .encryptedDataDetails(new ArrayList<>())
                    .build())
            .ignoreResponseCode(true)
            .build();

    List<ExecutionCapability> capabilityList =
        HelmChartCapabilityGenerator.generateCapabilities(manifestDelegateConfig, expressionEvaluator);
    assertThat(capabilityList).hasSize(2);
    assertThat(capabilityList.stream().map(Object::getClass))
        .containsExactlyInAnyOrder(HelmInstallationCapability.class, HttpConnectionExecutionCapability.class);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void generateCapabilitiesS3Helm() {
    HelmChartManifestDelegateConfig manifestDelegateConfig =
        HelmChartManifestDelegateConfig.builder()
            .helmVersion(HelmVersion.V3)
            .storeDelegateConfig(
                S3HelmStoreDelegateConfig.builder()
                    .awsConnector(AwsConnectorDTO.builder()
                                      .credential(AwsCredentialDTO.builder()
                                                      .awsCredentialType(AwsCredentialType.MANUAL_CREDENTIALS)
                                                      .config(AwsManualConfigSpecDTO.builder().build())
                                                      .build())
                                      .build())
                    .encryptedDataDetails(new ArrayList<>())
                    .build())
            .ignoreResponseCode(true)
            .build();

    List<ExecutionCapability> capabilityList =
        HelmChartCapabilityGenerator.generateCapabilities(manifestDelegateConfig, expressionEvaluator);
    assertThat(capabilityList).hasSize(2);
    assertThat(capabilityList.stream().map(Object::getClass))
        .containsExactlyInAnyOrder(HttpConnectionExecutionCapability.class, HelmInstallationCapability.class);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void generateCapabilitiesGcsHelm() {
    HelmChartManifestDelegateConfig manifestDelegateConfig =
        HelmChartManifestDelegateConfig.builder()
            .helmVersion(HelmVersion.V3)
            .storeDelegateConfig(
                GcsHelmStoreDelegateConfig.builder()
                    .gcpConnector(GcpConnectorDTO.builder()
                                      .credential(GcpConnectorCredentialDTO.builder()
                                                      .gcpCredentialType(GcpCredentialType.MANUAL_CREDENTIALS)
                                                      .config(GcpManualDetailsDTO.builder().build())
                                                      .build())
                                      .build())
                    .build())
            .ignoreResponseCode(true)
            .build();

    List<ExecutionCapability> capabilityList =
        HelmChartCapabilityGenerator.generateCapabilities(manifestDelegateConfig, expressionEvaluator);
    assertThat(capabilityList).hasSize(2);
    assertThat(capabilityList.stream().map(Object::getClass))
        .containsExactlyInAnyOrder(HttpConnectionExecutionCapability.class, HelmInstallationCapability.class);
  }
}