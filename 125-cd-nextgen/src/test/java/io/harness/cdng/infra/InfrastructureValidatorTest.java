/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.infra;

import static io.harness.rule.OwnerRule.ARVIND;
import static io.harness.rule.OwnerRule.LOVISH_BANSAL;
import static io.harness.rule.OwnerRule.YOGESH;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.cdng.elastigroup.ElastigroupConfiguration;
import io.harness.cdng.infra.yaml.AsgInfrastructure;
import io.harness.cdng.infra.yaml.AsgInfrastructure.AsgInfrastructureBuilder;
import io.harness.cdng.infra.yaml.ElastigroupInfrastructure;
import io.harness.cdng.infra.yaml.ElastigroupInfrastructure.ElastigroupInfrastructureBuilder;
import io.harness.cdng.infra.yaml.K8SDirectInfrastructure;
import io.harness.cdng.infra.yaml.K8sAwsInfrastructure;
import io.harness.cdng.infra.yaml.K8sAzureInfrastructure;
import io.harness.cdng.infra.yaml.K8sGcpInfrastructure;
import io.harness.cdng.infra.yaml.PdcInfrastructure;
import io.harness.cdng.infra.yaml.ServerlessAwsLambdaInfrastructure;
import io.harness.cdng.infra.yaml.SshWinRmAzureInfrastructure;
import io.harness.cdng.manifest.yaml.InlineStoreConfig;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfig;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfigType;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfigWrapper;
import io.harness.exception.InvalidArgumentsException;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.MockitoAnnotations;

public class InfrastructureValidatorTest extends CategoryTest {
  private InfrastructureValidator validator = new InfrastructureValidator();
  private AutoCloseable mocks;

  @Before
  public void setUp() throws Exception {
    mocks = MockitoAnnotations.openMocks(this);
  }

  @After
  public void tearDown() throws Exception {
    if (mocks != null) {
      mocks.close();
    }
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void testSshWinRmAzureInfrastructureEmptyCredentialsRefAndResourceGroup() {
    SshWinRmAzureInfrastructure invalidInfra = SshWinRmAzureInfrastructure.builder()
                                                   .credentialsRef(ParameterField.ofNull())
                                                   .resourceGroup(ParameterField.ofNull())
                                                   .connectorRef(ParameterField.createValueField("connector-ref"))
                                                   .subscriptionId(ParameterField.createValueField("sub-id"))
                                                   .build();

    assertThatThrownBy(() -> validator.validate(invalidInfra)).isInstanceOf(InvalidArgumentsException.class);
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void testK8sGcpInfraMapperEmptyValues() {
    K8sGcpInfrastructure emptyNamespace = K8sGcpInfrastructure.builder()
                                              .connectorRef(ParameterField.createValueField("connectorId"))
                                              .namespace(ParameterField.createValueField(""))
                                              .releaseName(ParameterField.createValueField("release"))
                                              .cluster(ParameterField.createValueField("cluster"))
                                              .build();
    assertThatThrownBy(() -> validator.validate(emptyNamespace)).isInstanceOf(InvalidArgumentsException.class);

    K8sGcpInfrastructure runtimeInputNamespace = K8sGcpInfrastructure.builder()
                                                     .connectorRef(ParameterField.createValueField("connectorId"))
                                                     .namespace(ParameterField.createValueField("<+input>"))
                                                     .releaseName(ParameterField.createValueField("release"))
                                                     .cluster(ParameterField.createValueField("cluster"))
                                                     .build();
    assertThatThrownBy(() -> validator.validate(runtimeInputNamespace)).isInstanceOf(InvalidArgumentsException.class);

    K8sGcpInfrastructure runtimeInputReleaseName = K8sGcpInfrastructure.builder()
                                                       .connectorRef(ParameterField.createValueField("connectorId"))
                                                       .namespace(ParameterField.createValueField("namespace"))
                                                       .releaseName(ParameterField.createValueField("<+input>"))
                                                       .cluster(ParameterField.createValueField("cluster"))
                                                       .build();
    assertThatThrownBy(() -> validator.validate(runtimeInputReleaseName)).isInstanceOf(InvalidArgumentsException.class);

    K8sGcpInfrastructure emptyReleaseName = K8sGcpInfrastructure.builder()
                                                .connectorRef(ParameterField.createValueField("connectorId"))
                                                .namespace(ParameterField.createValueField("namespace"))
                                                .releaseName(ParameterField.createValueField(""))
                                                .cluster(ParameterField.createValueField("cluster"))
                                                .build();
    assertThatThrownBy(() -> validator.validate(emptyReleaseName)).isInstanceOf(InvalidArgumentsException.class);

    K8sGcpInfrastructure emptyClusterName = K8sGcpInfrastructure.builder()
                                                .connectorRef(ParameterField.createValueField("connectorId"))
                                                .namespace(ParameterField.createValueField("namespace"))
                                                .releaseName(ParameterField.createValueField("release"))
                                                .cluster(ParameterField.createValueField(""))
                                                .build();
    assertThatThrownBy(() -> validator.validate(emptyClusterName)).isInstanceOf(InvalidArgumentsException.class);

    K8sGcpInfrastructure runtimeInputClusterName = K8sGcpInfrastructure.builder()
                                                       .connectorRef(ParameterField.createValueField("connectorId"))
                                                       .namespace(ParameterField.createValueField("namespace"))
                                                       .releaseName(ParameterField.createValueField("release"))
                                                       .cluster(ParameterField.createValueField("<+input>"))
                                                       .build();
    assertThatThrownBy(() -> validator.validate(runtimeInputClusterName)).isInstanceOf(InvalidArgumentsException.class);
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void testPdcInfrastructureEmptySshKeyRef() {
    PdcInfrastructure emptySshKeyRef =
        PdcInfrastructure.builder().connectorRef(ParameterField.createValueField("connector-ref")).build();

    assertThatThrownBy(() -> validator.validate(emptySshKeyRef)).isInstanceOf(InvalidArgumentsException.class);
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void testPdcInfrastructureEmptyHostsAndConnector() {
    PdcInfrastructure emptySshKeyRef = PdcInfrastructure.builder()
                                           .credentialsRef(ParameterField.createValueField("ssh-key-ref"))
                                           .hosts(ParameterField.ofNull())
                                           .connectorRef(ParameterField.ofNull())
                                           .build();

    assertThatThrownBy(() -> validator.validate(emptySshKeyRef)).isInstanceOf(InvalidArgumentsException.class);
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void testK8sAzureInfraMapperEmptyValues() {
    K8sAzureInfrastructure emptyNamespace = K8sAzureInfrastructure.builder()
                                                .connectorRef(ParameterField.createValueField("connectorId"))
                                                .namespace(ParameterField.createValueField(""))
                                                .releaseName(ParameterField.createValueField("release"))
                                                .subscriptionId(ParameterField.createValueField("subscriptionId"))
                                                .resourceGroup(ParameterField.createValueField("resourceGroup"))
                                                .cluster(ParameterField.createValueField("cluster"))
                                                .build();
    assertThatThrownBy(() -> validator.validate(emptyNamespace)).isInstanceOf(InvalidArgumentsException.class);

    K8sAzureInfrastructure runtimeInputNamespace =
        K8sAzureInfrastructure.builder()
            .connectorRef(ParameterField.createValueField("connectorId"))
            .namespace(ParameterField.createValueField("<+input>"))
            .releaseName(ParameterField.createValueField("release"))
            .subscriptionId(ParameterField.createValueField("subscriptionId"))
            .resourceGroup(ParameterField.createValueField("resourceGroup"))
            .cluster(ParameterField.createValueField("cluster"))
            .build();
    assertThatThrownBy(() -> validator.validate(runtimeInputNamespace)).isInstanceOf(InvalidArgumentsException.class);

    K8sAzureInfrastructure emptyReleaseName = K8sAzureInfrastructure.builder()
                                                  .connectorRef(ParameterField.createValueField("connectorId"))
                                                  .namespace(ParameterField.createValueField("namespace"))
                                                  .releaseName(ParameterField.createValueField(""))
                                                  .subscriptionId(ParameterField.createValueField("subscriptionId"))
                                                  .resourceGroup(ParameterField.createValueField("resourceGroup"))
                                                  .cluster(ParameterField.createValueField("cluster"))
                                                  .build();
    assertThatThrownBy(() -> validator.validate(emptyReleaseName)).isInstanceOf(InvalidArgumentsException.class);

    K8sAzureInfrastructure runtimeInputReleaseName =
        K8sAzureInfrastructure.builder()
            .connectorRef(ParameterField.createValueField("connectorId"))
            .namespace(ParameterField.createValueField("namespace"))
            .releaseName(ParameterField.createValueField("<+input>"))
            .subscriptionId(ParameterField.createValueField("subscriptionId"))
            .resourceGroup(ParameterField.createValueField("resourceGroup"))
            .cluster(ParameterField.createValueField("cluster"))
            .build();
    assertThatThrownBy(() -> validator.validate(runtimeInputReleaseName)).isInstanceOf(InvalidArgumentsException.class);

    K8sAzureInfrastructure emptySubscription = K8sAzureInfrastructure.builder()
                                                   .connectorRef(ParameterField.createValueField("connectorId"))
                                                   .namespace(ParameterField.createValueField("namespace"))
                                                   .releaseName(ParameterField.createValueField("release"))
                                                   .subscriptionId(ParameterField.createValueField(""))
                                                   .resourceGroup(ParameterField.createValueField("resourceGroup"))
                                                   .cluster(ParameterField.createValueField("cluster"))
                                                   .build();
    assertThatThrownBy(() -> validator.validate(emptySubscription)).isInstanceOf(InvalidArgumentsException.class);

    K8sAzureInfrastructure runtimeInputSubscription =
        K8sAzureInfrastructure.builder()
            .connectorRef(ParameterField.createValueField("connectorId"))
            .namespace(ParameterField.createValueField("namespace"))
            .releaseName(ParameterField.createValueField("release"))
            .subscriptionId(ParameterField.createValueField("<+input>"))
            .resourceGroup(ParameterField.createValueField("resourceGroup"))
            .cluster(ParameterField.createValueField("cluster"))
            .build();
    assertThatThrownBy(() -> validator.validate(runtimeInputSubscription))
        .isInstanceOf(InvalidArgumentsException.class);

    K8sAzureInfrastructure emptyResourceGroupName =
        K8sAzureInfrastructure.builder()
            .connectorRef(ParameterField.createValueField("connectorId"))
            .namespace(ParameterField.createValueField("namespace"))
            .releaseName(ParameterField.createValueField("release"))
            .subscriptionId(ParameterField.createValueField("subscriptionId"))
            .resourceGroup(ParameterField.createValueField(""))
            .cluster(ParameterField.createValueField("cluster"))
            .build();
    assertThatThrownBy(() -> validator.validate(emptyResourceGroupName)).isInstanceOf(InvalidArgumentsException.class);

    K8sAzureInfrastructure runtimeInputResourceGroupName =
        K8sAzureInfrastructure.builder()
            .connectorRef(ParameterField.createValueField("connectorId"))
            .namespace(ParameterField.createValueField("namespace"))
            .releaseName(ParameterField.createValueField("release"))
            .subscriptionId(ParameterField.createValueField("subscriptionId"))
            .resourceGroup(ParameterField.createValueField("<+input>"))
            .cluster(ParameterField.createValueField("cluster"))
            .build();
    assertThatThrownBy(() -> validator.validate(runtimeInputResourceGroupName))
        .isInstanceOf(InvalidArgumentsException.class);

    K8sAzureInfrastructure emptyClusterName = K8sAzureInfrastructure.builder()
                                                  .connectorRef(ParameterField.createValueField("connectorId"))
                                                  .namespace(ParameterField.createValueField("namespace"))
                                                  .releaseName(ParameterField.createValueField("release"))
                                                  .subscriptionId(ParameterField.createValueField("subscriptionId"))
                                                  .resourceGroup(ParameterField.createValueField("resourceGroup"))
                                                  .cluster(ParameterField.createValueField(""))
                                                  .build();
    assertThatThrownBy(() -> validator.validate(emptyClusterName)).isInstanceOf(InvalidArgumentsException.class);

    K8sAzureInfrastructure runtimeInputClusterName =
        K8sAzureInfrastructure.builder()
            .connectorRef(ParameterField.createValueField("connectorId"))
            .namespace(ParameterField.createValueField("namespace"))
            .releaseName(ParameterField.createValueField("release"))
            .subscriptionId(ParameterField.createValueField("subscriptionId"))
            .resourceGroup(ParameterField.createValueField("resourceGroup"))
            .cluster(ParameterField.createValueField("<+input>"))
            .build();
    assertThatThrownBy(() -> validator.validate(runtimeInputClusterName)).isInstanceOf(InvalidArgumentsException.class);
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void testSshWinRmAzureInfrastructureEmptySubscriptionIdAndConnectorId() {
    SshWinRmAzureInfrastructure invalidInfra = SshWinRmAzureInfrastructure.builder()
                                                   .subscriptionId(ParameterField.ofNull())
                                                   .connectorRef(ParameterField.ofNull())
                                                   .credentialsRef(ParameterField.createValueField("ssh-key-ref"))
                                                   .resourceGroup(ParameterField.createValueField("resource-id"))
                                                   .build();

    assertThatThrownBy(() -> validator.validate(invalidInfra)).isInstanceOf(InvalidArgumentsException.class);
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void testServerlessAwsInfraMapperEmptyValues() {
    ServerlessAwsLambdaInfrastructure emptyRegion = ServerlessAwsLambdaInfrastructure.builder()
                                                        .connectorRef(ParameterField.createValueField("connectorId"))
                                                        .region(ParameterField.createValueField(""))
                                                        .stage(ParameterField.createValueField("stage"))
                                                        .build();
    assertThatThrownBy(() -> validator.validate(emptyRegion)).isInstanceOf(InvalidArgumentsException.class);

    ServerlessAwsLambdaInfrastructure emptyStage = ServerlessAwsLambdaInfrastructure.builder()
                                                       .connectorRef(ParameterField.createValueField("connectorId"))
                                                       .region(ParameterField.createValueField("region"))
                                                       .stage(ParameterField.createValueField(""))
                                                       .build();
    assertThatThrownBy(() -> validator.validate(emptyStage));
  }

  @Test
  @Owner(developers = ARVIND)
  @Category(UnitTests.class)
  public void testElastigroupInfraMapper() {
    assertThatThrownBy(() -> validator.validate(getElastigroupInfrastructure(true, false)))
        .isInstanceOf(InvalidArgumentsException.class);
    assertThatThrownBy(() -> validator.validate(getElastigroupInfrastructure(true, true)))
        .isInstanceOf(InvalidArgumentsException.class);
    assertThatThrownBy(() -> validator.validate(getElastigroupInfrastructure(false, true)))
        .isInstanceOf(InvalidArgumentsException.class);
    assertThatCode(() -> validator.validate(getElastigroupInfrastructure(false, false))).doesNotThrowAnyException();
  }

  private ElastigroupInfrastructure getElastigroupInfrastructure(boolean emptyConnector, boolean emptyConfiguration) {
    StoreConfig storeConfig =
        InlineStoreConfig.builder().content(ParameterField.createValueField("this is content")).build();
    ElastigroupInfrastructureBuilder builder = ElastigroupInfrastructure.builder();
    if (!emptyConnector) {
      builder.connectorRef(ParameterField.createValueField("connector"));
    }
    if (!emptyConfiguration) {
      builder.configuration(
          ElastigroupConfiguration.builder()
              .store(StoreConfigWrapper.builder().type(StoreConfigType.INLINE).spec(storeConfig).build())
              .build());
    }
    return builder.build();
  }

  @Test
  @Owner(developers = LOVISH_BANSAL)
  @Category(UnitTests.class)
  public void testAsgInfraMapper() {
    assertThatThrownBy(() -> validator.validate(getAsgInfrastructure(true, false)))
        .isInstanceOf(InvalidArgumentsException.class);
    assertThatThrownBy(() -> validator.validate(getAsgInfrastructure(true, true)))
        .isInstanceOf(InvalidArgumentsException.class);
    assertThatThrownBy(() -> validator.validate(getAsgInfrastructure(false, true)))
        .isInstanceOf(InvalidArgumentsException.class);
    assertThatCode(() -> validator.validate(getAsgInfrastructure(false, false))).doesNotThrowAnyException();
  }

  private AsgInfrastructure getAsgInfrastructure(boolean emptyConnector, boolean emptyRegion) {
    AsgInfrastructureBuilder builder = AsgInfrastructure.builder();
    if (!emptyConnector) {
      builder.connectorRef(ParameterField.createValueField("connector"));
    }
    if (!emptyRegion) {
      builder.region(ParameterField.createValueField("region"));
    }
    return builder.build();
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void testToOutcomeEmptyValues() {
    K8SDirectInfrastructure emptyReleaseName = K8SDirectInfrastructure.builder()
                                                   .connectorRef(ParameterField.createValueField("connectorId"))
                                                   .namespace(ParameterField.createValueField("namespace"))
                                                   .releaseName(ParameterField.createValueField(""))
                                                   .build();

    assertThatThrownBy(() -> validator.validate(emptyReleaseName)).isInstanceOf(InvalidArgumentsException.class);

    K8SDirectInfrastructure runtimeInputReleaseName = K8SDirectInfrastructure.builder()
                                                          .connectorRef(ParameterField.createValueField("connectorId"))
                                                          .namespace(ParameterField.createValueField("namespace"))
                                                          .releaseName(ParameterField.createValueField("<+input>"))
                                                          .build();

    assertThatThrownBy(() -> validator.validate(runtimeInputReleaseName)).isInstanceOf(InvalidArgumentsException.class);

    K8SDirectInfrastructure runtimeInputNamespace = K8SDirectInfrastructure.builder()
                                                        .connectorRef(ParameterField.createValueField("connectorId"))
                                                        .namespace(ParameterField.createValueField("<+input>"))
                                                        .releaseName(ParameterField.createValueField("releaseName"))
                                                        .build();

    assertThatThrownBy(() -> validator.validate(runtimeInputNamespace)).isInstanceOf(InvalidArgumentsException.class);

    K8SDirectInfrastructure emptyNamespace = K8SDirectInfrastructure.builder()
                                                 .connectorRef(ParameterField.createValueField("connectorId"))
                                                 .namespace(ParameterField.createValueField(""))
                                                 .releaseName(ParameterField.createValueField("releaseName"))
                                                 .build();

    assertThatThrownBy(() -> validator.validate(emptyNamespace)).isInstanceOf(InvalidArgumentsException.class);
  }

  @Test
  @Owner(developers = LOVISH_BANSAL)
  @Category(UnitTests.class)
  public void testK8sAwsInfraMapperEmptyValues() {
    K8sAwsInfrastructure emptyNamespace = K8sAwsInfrastructure.builder()
                                              .connectorRef(ParameterField.createValueField("connectorId"))
                                              .namespace(ParameterField.createValueField(""))
                                              .releaseName(ParameterField.createValueField("release"))
                                              .cluster(ParameterField.createValueField("cluster"))
                                              .build();
    assertThatThrownBy(() -> validator.validate(emptyNamespace)).isInstanceOf(InvalidArgumentsException.class);

    K8sAwsInfrastructure runtimeInputNamespace = K8sAwsInfrastructure.builder()
                                                     .connectorRef(ParameterField.createValueField("connectorId"))
                                                     .namespace(ParameterField.createValueField(""))
                                                     .releaseName(ParameterField.createValueField("release"))
                                                     .cluster(ParameterField.createValueField("cluster"))
                                                     .build();
    assertThatThrownBy(() -> validator.validate(runtimeInputNamespace)).isInstanceOf(InvalidArgumentsException.class);

    K8sAwsInfrastructure emptyReleaseName = K8sAwsInfrastructure.builder()
                                                .connectorRef(ParameterField.createValueField("connectorId"))
                                                .namespace(ParameterField.createValueField("namespace"))
                                                .releaseName(ParameterField.createValueField(""))
                                                .cluster(ParameterField.createValueField("cluster"))
                                                .build();
    assertThatThrownBy(() -> validator.validate(emptyReleaseName)).isInstanceOf(InvalidArgumentsException.class);

    K8sAwsInfrastructure runtimeInputReleaseName = K8sAwsInfrastructure.builder()
                                                       .connectorRef(ParameterField.createValueField("connectorId"))
                                                       .namespace(ParameterField.createValueField("namespace"))
                                                       .releaseName(ParameterField.createValueField("<+input>"))
                                                       .cluster(ParameterField.createValueField("cluster"))
                                                       .build();
    assertThatThrownBy(() -> validator.validate(runtimeInputReleaseName)).isInstanceOf(InvalidArgumentsException.class);

    K8sAwsInfrastructure emptyClusterName = K8sAwsInfrastructure.builder()
                                                .connectorRef(ParameterField.createValueField("connectorId"))
                                                .namespace(ParameterField.createValueField("namespace"))
                                                .releaseName(ParameterField.createValueField("release"))
                                                .cluster(ParameterField.createValueField(""))
                                                .build();
    assertThatThrownBy(() -> validator.validate(emptyClusterName)).isInstanceOf(InvalidArgumentsException.class);

    K8sAwsInfrastructure runtimeInputClusterName = K8sAwsInfrastructure.builder()
                                                       .connectorRef(ParameterField.createValueField("connectorId"))
                                                       .namespace(ParameterField.createValueField("namespace"))
                                                       .releaseName(ParameterField.createValueField("release"))
                                                       .cluster(ParameterField.createValueField("<+input>"))
                                                       .build();
    assertThatThrownBy(() -> validator.validate(runtimeInputClusterName)).isInstanceOf(InvalidArgumentsException.class);
  }
}
