/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.infra;

import static io.harness.rule.OwnerRule.ARVIND;
import static io.harness.rule.OwnerRule.FILIP;
import static io.harness.rule.OwnerRule.LOVISH_BANSAL;
import static io.harness.rule.OwnerRule.PRAGYESH;
import static io.harness.rule.OwnerRule.TMACARI;
import static io.harness.rule.OwnerRule.VAIBHAV_SI;
import static io.harness.rule.OwnerRule.VITALIE;
import static io.harness.rule.OwnerRule.YOGESH;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.powermock.api.mockito.PowerMockito.spy;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.cdng.elastigroup.ElastigroupConfiguration;
import io.harness.cdng.infra.yaml.AsgInfrastructure;
import io.harness.cdng.infra.yaml.AsgInfrastructure.AsgInfrastructureBuilder;
import io.harness.cdng.infra.yaml.AzureWebAppInfrastructure;
import io.harness.cdng.infra.yaml.ElastigroupInfrastructure;
import io.harness.cdng.infra.yaml.ElastigroupInfrastructure.ElastigroupInfrastructureBuilder;
import io.harness.cdng.infra.yaml.GoogleFunctionsInfrastructure;
import io.harness.cdng.infra.yaml.K8SDirectInfrastructure;
import io.harness.cdng.infra.yaml.K8sAwsInfrastructure;
import io.harness.cdng.infra.yaml.K8sAzureInfrastructure;
import io.harness.cdng.infra.yaml.K8sGcpInfrastructure;
import io.harness.cdng.infra.yaml.PdcInfrastructure;
import io.harness.cdng.infra.yaml.ServerlessAwsLambdaInfrastructure;
import io.harness.cdng.infra.yaml.SshWinRmAwsInfrastructure;
import io.harness.cdng.infra.yaml.SshWinRmAzureInfrastructure;
import io.harness.cdng.manifest.yaml.InlineStoreConfig;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfig;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfigType;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfigWrapper;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.InvalidRequestException;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;

import java.util.Arrays;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

public class InfrastructureValidatorTest extends CategoryTest {
  private InfrastructureValidator validator = spy(new InfrastructureValidator());
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

    assertThatThrownBy(() -> validator.validateInfrastructure(invalidInfra, null, null))
        .isInstanceOf(InvalidArgumentsException.class);
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
    assertThatThrownBy(() -> validator.validateInfrastructure(emptyNamespace, null, null))
        .isInstanceOf(InvalidArgumentsException.class);

    K8sGcpInfrastructure runtimeInputNamespace = K8sGcpInfrastructure.builder()
                                                     .connectorRef(ParameterField.createValueField("connectorId"))
                                                     .namespace(ParameterField.createValueField("<+input>"))
                                                     .releaseName(ParameterField.createValueField("release"))
                                                     .cluster(ParameterField.createValueField("cluster"))
                                                     .build();
    assertThatThrownBy(() -> validator.validateInfrastructure(runtimeInputNamespace, null, null))
        .isInstanceOf(InvalidArgumentsException.class);

    K8sGcpInfrastructure runtimeInputReleaseName = K8sGcpInfrastructure.builder()
                                                       .connectorRef(ParameterField.createValueField("connectorId"))
                                                       .namespace(ParameterField.createValueField("namespace"))
                                                       .releaseName(ParameterField.createValueField("<+input>"))
                                                       .cluster(ParameterField.createValueField("cluster"))
                                                       .build();
    assertThatThrownBy(() -> validator.validateInfrastructure(runtimeInputReleaseName, null, null))
        .isInstanceOf(InvalidArgumentsException.class);

    K8sGcpInfrastructure emptyReleaseName = K8sGcpInfrastructure.builder()
                                                .connectorRef(ParameterField.createValueField("connectorId"))
                                                .namespace(ParameterField.createValueField("namespace"))
                                                .releaseName(ParameterField.createValueField(""))
                                                .cluster(ParameterField.createValueField("cluster"))
                                                .build();
    assertThatThrownBy(() -> validator.validateInfrastructure(emptyReleaseName, null, null))
        .isInstanceOf(InvalidArgumentsException.class);

    K8sGcpInfrastructure emptyClusterName = K8sGcpInfrastructure.builder()
                                                .connectorRef(ParameterField.createValueField("connectorId"))
                                                .namespace(ParameterField.createValueField("namespace"))
                                                .releaseName(ParameterField.createValueField("release"))
                                                .cluster(ParameterField.createValueField(""))
                                                .build();
    assertThatThrownBy(() -> validator.validateInfrastructure(emptyClusterName, null, null))
        .isInstanceOf(InvalidArgumentsException.class);

    K8sGcpInfrastructure runtimeInputClusterName = K8sGcpInfrastructure.builder()
                                                       .connectorRef(ParameterField.createValueField("connectorId"))
                                                       .namespace(ParameterField.createValueField("namespace"))
                                                       .releaseName(ParameterField.createValueField("release"))
                                                       .cluster(ParameterField.createValueField("<+input>"))
                                                       .build();
    assertThatThrownBy(() -> validator.validateInfrastructure(runtimeInputClusterName, null, null))
        .isInstanceOf(InvalidArgumentsException.class);
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void testPdcInfrastructureEmptySshKeyRef() {
    PdcInfrastructure emptySshKeyRef =
        PdcInfrastructure.builder().connectorRef(ParameterField.createValueField("connector-ref")).build();

    assertThatThrownBy(() -> validator.validateInfrastructure(emptySshKeyRef, null, null))
        .isInstanceOf(InvalidArgumentsException.class);
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

    assertThatThrownBy(() -> validator.validateInfrastructure(emptySshKeyRef, null, null))
        .isInstanceOf(InvalidArgumentsException.class);
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
    assertThatThrownBy(() -> validator.validateInfrastructure(emptyNamespace, null, null))
        .isInstanceOf(InvalidArgumentsException.class);

    K8sAzureInfrastructure runtimeInputNamespace =
        K8sAzureInfrastructure.builder()
            .connectorRef(ParameterField.createValueField("connectorId"))
            .namespace(ParameterField.createValueField("<+input>"))
            .releaseName(ParameterField.createValueField("release"))
            .subscriptionId(ParameterField.createValueField("subscriptionId"))
            .resourceGroup(ParameterField.createValueField("resourceGroup"))
            .cluster(ParameterField.createValueField("cluster"))
            .build();
    assertThatThrownBy(() -> validator.validateInfrastructure(runtimeInputNamespace, null, null))
        .isInstanceOf(InvalidArgumentsException.class);

    K8sAzureInfrastructure emptyReleaseName = K8sAzureInfrastructure.builder()
                                                  .connectorRef(ParameterField.createValueField("connectorId"))
                                                  .namespace(ParameterField.createValueField("namespace"))
                                                  .releaseName(ParameterField.createValueField(""))
                                                  .subscriptionId(ParameterField.createValueField("subscriptionId"))
                                                  .resourceGroup(ParameterField.createValueField("resourceGroup"))
                                                  .cluster(ParameterField.createValueField("cluster"))
                                                  .build();
    assertThatThrownBy(() -> validator.validateInfrastructure(emptyReleaseName, null, null))
        .isInstanceOf(InvalidArgumentsException.class);

    K8sAzureInfrastructure runtimeInputReleaseName =
        K8sAzureInfrastructure.builder()
            .connectorRef(ParameterField.createValueField("connectorId"))
            .namespace(ParameterField.createValueField("namespace"))
            .releaseName(ParameterField.createValueField("<+input>"))
            .subscriptionId(ParameterField.createValueField("subscriptionId"))
            .resourceGroup(ParameterField.createValueField("resourceGroup"))
            .cluster(ParameterField.createValueField("cluster"))
            .build();
    assertThatThrownBy(() -> validator.validateInfrastructure(runtimeInputReleaseName, null, null))
        .isInstanceOf(InvalidArgumentsException.class);

    K8sAzureInfrastructure emptySubscription = K8sAzureInfrastructure.builder()
                                                   .connectorRef(ParameterField.createValueField("connectorId"))
                                                   .namespace(ParameterField.createValueField("namespace"))
                                                   .releaseName(ParameterField.createValueField("release"))
                                                   .subscriptionId(ParameterField.createValueField(""))
                                                   .resourceGroup(ParameterField.createValueField("resourceGroup"))
                                                   .cluster(ParameterField.createValueField("cluster"))
                                                   .build();
    assertThatThrownBy(() -> validator.validateInfrastructure(emptySubscription, null, null))
        .isInstanceOf(InvalidArgumentsException.class);

    K8sAzureInfrastructure runtimeInputSubscription =
        K8sAzureInfrastructure.builder()
            .connectorRef(ParameterField.createValueField("connectorId"))
            .namespace(ParameterField.createValueField("namespace"))
            .releaseName(ParameterField.createValueField("release"))
            .subscriptionId(ParameterField.createValueField("<+input>"))
            .resourceGroup(ParameterField.createValueField("resourceGroup"))
            .cluster(ParameterField.createValueField("cluster"))
            .build();
    assertThatThrownBy(() -> validator.validateInfrastructure(runtimeInputSubscription, null, null))
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
    assertThatThrownBy(() -> validator.validateInfrastructure(emptyResourceGroupName, null, null))
        .isInstanceOf(InvalidArgumentsException.class);

    K8sAzureInfrastructure runtimeInputResourceGroupName =
        K8sAzureInfrastructure.builder()
            .connectorRef(ParameterField.createValueField("connectorId"))
            .namespace(ParameterField.createValueField("namespace"))
            .releaseName(ParameterField.createValueField("release"))
            .subscriptionId(ParameterField.createValueField("subscriptionId"))
            .resourceGroup(ParameterField.createValueField("<+input>"))
            .cluster(ParameterField.createValueField("cluster"))
            .build();
    assertThatThrownBy(() -> validator.validateInfrastructure(runtimeInputResourceGroupName, null, null))
        .isInstanceOf(InvalidArgumentsException.class);

    K8sAzureInfrastructure emptyClusterName = K8sAzureInfrastructure.builder()
                                                  .connectorRef(ParameterField.createValueField("connectorId"))
                                                  .namespace(ParameterField.createValueField("namespace"))
                                                  .releaseName(ParameterField.createValueField("release"))
                                                  .subscriptionId(ParameterField.createValueField("subscriptionId"))
                                                  .resourceGroup(ParameterField.createValueField("resourceGroup"))
                                                  .cluster(ParameterField.createValueField(""))
                                                  .build();
    assertThatThrownBy(() -> validator.validateInfrastructure(emptyClusterName, null, null))
        .isInstanceOf(InvalidArgumentsException.class);

    K8sAzureInfrastructure runtimeInputClusterName =
        K8sAzureInfrastructure.builder()
            .connectorRef(ParameterField.createValueField("connectorId"))
            .namespace(ParameterField.createValueField("namespace"))
            .releaseName(ParameterField.createValueField("release"))
            .subscriptionId(ParameterField.createValueField("subscriptionId"))
            .resourceGroup(ParameterField.createValueField("resourceGroup"))
            .cluster(ParameterField.createValueField("<+input>"))
            .build();
    assertThatThrownBy(() -> validator.validateInfrastructure(runtimeInputClusterName, null, null))
        .isInstanceOf(InvalidArgumentsException.class);
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

    assertThatThrownBy(() -> validator.validateInfrastructure(invalidInfra, null, null))
        .isInstanceOf(InvalidArgumentsException.class);
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
    assertThatThrownBy(() -> validator.validateInfrastructure(emptyRegion, null, null))
        .isInstanceOf(InvalidArgumentsException.class);

    ServerlessAwsLambdaInfrastructure emptyStage = ServerlessAwsLambdaInfrastructure.builder()
                                                       .connectorRef(ParameterField.createValueField("connectorId"))
                                                       .region(ParameterField.createValueField("region"))
                                                       .stage(ParameterField.createValueField(""))
                                                       .build();
    assertThatThrownBy(() -> validator.validateInfrastructure(emptyStage, null, null));
  }

  @Test
  @Owner(developers = ARVIND)
  @Category(UnitTests.class)
  public void testElastigroupInfraMapper() {
    assertThatThrownBy(() -> validator.validateInfrastructure(getElastigroupInfrastructure(true, false), null, null))
        .isInstanceOf(InvalidArgumentsException.class);
    assertThatThrownBy(() -> validator.validateInfrastructure(getElastigroupInfrastructure(true, true), null, null))
        .isInstanceOf(InvalidArgumentsException.class);
    assertThatThrownBy(() -> validator.validateInfrastructure(getElastigroupInfrastructure(false, true), null, null))
        .isInstanceOf(InvalidArgumentsException.class);
    assertThatCode(() -> validator.validateInfrastructure(getElastigroupInfrastructure(false, false), null, null))
        .doesNotThrowAnyException();
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
    assertThatThrownBy(() -> validator.validateInfrastructure(getAsgInfrastructure(true, false), null, null))
        .isInstanceOf(InvalidArgumentsException.class);
    assertThatThrownBy(() -> validator.validateInfrastructure(getAsgInfrastructure(true, true), null, null))
        .isInstanceOf(InvalidArgumentsException.class);
    assertThatThrownBy(() -> validator.validateInfrastructure(getAsgInfrastructure(false, true), null, null))
        .isInstanceOf(InvalidArgumentsException.class);
    assertThatCode(() -> validator.validateInfrastructure(getAsgInfrastructure(false, false), null, null))
        .doesNotThrowAnyException();
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

    assertThatThrownBy(() -> validator.validateInfrastructure(emptyReleaseName, null, null))
        .isInstanceOf(InvalidArgumentsException.class);

    K8SDirectInfrastructure runtimeInputReleaseName = K8SDirectInfrastructure.builder()
                                                          .connectorRef(ParameterField.createValueField("connectorId"))
                                                          .namespace(ParameterField.createValueField("namespace"))
                                                          .releaseName(ParameterField.createValueField("<+input>"))
                                                          .build();

    assertThatThrownBy(() -> validator.validateInfrastructure(runtimeInputReleaseName, null, null))
        .isInstanceOf(InvalidArgumentsException.class);

    K8SDirectInfrastructure runtimeInputNamespace = K8SDirectInfrastructure.builder()
                                                        .connectorRef(ParameterField.createValueField("connectorId"))
                                                        .namespace(ParameterField.createValueField("<+input>"))
                                                        .releaseName(ParameterField.createValueField("releaseName"))
                                                        .build();

    assertThatThrownBy(() -> validator.validateInfrastructure(runtimeInputNamespace, null, null))
        .isInstanceOf(InvalidArgumentsException.class);

    K8SDirectInfrastructure emptyNamespace = K8SDirectInfrastructure.builder()
                                                 .connectorRef(ParameterField.createValueField("connectorId"))
                                                 .namespace(ParameterField.createValueField(""))
                                                 .releaseName(ParameterField.createValueField("releaseName"))
                                                 .build();

    assertThatThrownBy(() -> validator.validateInfrastructure(emptyNamespace, null, null))
        .isInstanceOf(InvalidArgumentsException.class);
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
    assertThatThrownBy(() -> validator.validateInfrastructure(emptyNamespace, null, null))
        .isInstanceOf(InvalidArgumentsException.class);

    K8sAwsInfrastructure runtimeInputNamespace = K8sAwsInfrastructure.builder()
                                                     .connectorRef(ParameterField.createValueField("connectorId"))
                                                     .namespace(ParameterField.createValueField(""))
                                                     .releaseName(ParameterField.createValueField("release"))
                                                     .cluster(ParameterField.createValueField("cluster"))
                                                     .build();
    assertThatThrownBy(() -> validator.validateInfrastructure(runtimeInputNamespace, null, null))
        .isInstanceOf(InvalidArgumentsException.class);

    K8sAwsInfrastructure emptyReleaseName = K8sAwsInfrastructure.builder()
                                                .connectorRef(ParameterField.createValueField("connectorId"))
                                                .namespace(ParameterField.createValueField("namespace"))
                                                .releaseName(ParameterField.createValueField(""))
                                                .cluster(ParameterField.createValueField("cluster"))
                                                .build();
    assertThatThrownBy(() -> validator.validateInfrastructure(emptyReleaseName, null, null))
        .isInstanceOf(InvalidArgumentsException.class);

    K8sAwsInfrastructure runtimeInputReleaseName = K8sAwsInfrastructure.builder()
                                                       .connectorRef(ParameterField.createValueField("connectorId"))
                                                       .namespace(ParameterField.createValueField("namespace"))
                                                       .releaseName(ParameterField.createValueField("<+input>"))
                                                       .cluster(ParameterField.createValueField("cluster"))
                                                       .build();
    assertThatThrownBy(() -> validator.validateInfrastructure(runtimeInputReleaseName, null, null))
        .isInstanceOf(InvalidArgumentsException.class);

    K8sAwsInfrastructure emptyClusterName = K8sAwsInfrastructure.builder()
                                                .connectorRef(ParameterField.createValueField("connectorId"))
                                                .namespace(ParameterField.createValueField("namespace"))
                                                .releaseName(ParameterField.createValueField("release"))
                                                .cluster(ParameterField.createValueField(""))
                                                .build();
    assertThatThrownBy(() -> validator.validateInfrastructure(emptyClusterName, null, null))
        .isInstanceOf(InvalidArgumentsException.class);

    K8sAwsInfrastructure runtimeInputClusterName = K8sAwsInfrastructure.builder()
                                                       .connectorRef(ParameterField.createValueField("connectorId"))
                                                       .namespace(ParameterField.createValueField("namespace"))
                                                       .releaseName(ParameterField.createValueField("release"))
                                                       .cluster(ParameterField.createValueField("<+input>"))
                                                       .build();
    assertThatThrownBy(() -> validator.validateInfrastructure(runtimeInputClusterName, null, null))
        .isInstanceOf(InvalidArgumentsException.class);
  }

  @Test
  @Owner(developers = PRAGYESH)
  @Category(UnitTests.class)
  public void testGoogleFunctionInfraMapperEmptyValues() {
    GoogleFunctionsInfrastructure emptyRegionInfra = GoogleFunctionsInfrastructure.builder()
                                                         .connectorRef(ParameterField.createValueField("connectorId"))
                                                         .region(ParameterField.createValueField(""))
                                                         .project(ParameterField.createValueField("project"))
                                                         .build();
    assertThatThrownBy(() -> validator.validateInfrastructure(emptyRegionInfra, null, null))
        .isInstanceOf(InvalidArgumentsException.class);

    GoogleFunctionsInfrastructure emptyProjectInfra = GoogleFunctionsInfrastructure.builder()
                                                          .connectorRef(ParameterField.createValueField("connectorId"))
                                                          .region(ParameterField.createValueField("region"))
                                                          .project(ParameterField.createValueField(""))
                                                          .build();
    assertThatThrownBy(() -> validator.validateInfrastructure(emptyProjectInfra, null, null))
        .isInstanceOf(InvalidArgumentsException.class);
  }

  @Test
  @Owner(developers = VAIBHAV_SI)
  @Category(UnitTests.class)
  public void testValidateInfrastructure() {
    assertThatThrownBy(() -> validator.validateInfrastructure(null, null, null))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Infrastructure definition can't be null or empty");

    K8SDirectInfrastructure.K8SDirectInfrastructureBuilder k8SDirectInfrastructureBuilder =
        K8SDirectInfrastructure.builder()
            .namespace(ParameterField.createValueField("name"))
            .releaseName(ParameterField.createValueField("release"));
    validator.validateInfrastructure(k8SDirectInfrastructureBuilder.build(), null, null);

    k8SDirectInfrastructureBuilder.connectorRef(ParameterField.createValueField("connector"));
    validator.validateInfrastructure(k8SDirectInfrastructureBuilder.build(), null, null);

    ParameterField param = new ParameterField<>(null, null, true, "expression1", null, true);
    k8SDirectInfrastructureBuilder.connectorRef(param);
    doThrow(new InvalidRequestException("Unresolved Expression : [expression1]"))
        .when(validator)
        .validateExpression(eq(param), any());

    assertThatThrownBy(() -> validator.validateInfrastructure(k8SDirectInfrastructureBuilder.build(), null, null))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Unresolved Expression : [expression1]");
  }

  @Test
  @Owner(developers = FILIP)
  @Category(UnitTests.class)
  public void testValidatePdcInfrastructure() {
    PdcInfrastructure infrastructure = PdcInfrastructure.builder()
                                           .credentialsRef(ParameterField.createValueField("ssh-key-ref"))
                                           .hosts(ParameterField.createValueField(Arrays.asList("host1", "host2")))
                                           .build();

    validator.validateInfrastructure(infrastructure, null, null);
  }

  @Test
  @Owner(developers = FILIP)
  @Category(UnitTests.class)
  public void testValidatePdcInfrastructureHostsAndConnectorAreExpressions() {
    ParameterField<String> credentialsRef = ParameterField.createValueField("ssh-key-ref");
    ParameterField hosts = new ParameterField<>(null, null, true, "expression1", null, true);
    ParameterField connectorRef = new ParameterField<>(null, null, true, "expression2", null, true);
    PdcInfrastructure infrastructure =
        PdcInfrastructure.builder().credentialsRef(credentialsRef).hosts(hosts).connectorRef(connectorRef).build();

    doNothing().when(validator).validateExpression(eq(credentialsRef));
    doThrow(new InvalidRequestException("Unresolved Expressions : [expression1] , [expression2]"))
        .when(validator)
        .requireOne(eq(hosts), eq(connectorRef));

    assertThatThrownBy(() -> validator.validateInfrastructure(infrastructure, null, null))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Unresolved Expressions : [expression1] , [expression2]");
  }

  @Test
  @Owner(developers = FILIP)
  @Category(UnitTests.class)
  public void testValidateSshWinRmAzureInfrastructure() {
    SshWinRmAzureInfrastructure infrastructure = SshWinRmAzureInfrastructure.builder()
                                                     .credentialsRef(ParameterField.createValueField("credentials-ref"))
                                                     .connectorRef(ParameterField.createValueField("connector-ref"))
                                                     .subscriptionId(ParameterField.createValueField("subscription-id"))
                                                     .resourceGroup(ParameterField.createValueField("resource-group"))
                                                     .build();

    validator.validateInfrastructure(infrastructure, null, null);
  }

  @Test
  @Owner(developers = FILIP)
  @Category(UnitTests.class)
  public void testValidateSshWinRmAzureInfrastructureCredentialsIsExpression() {
    ParameterField credentialsRef = new ParameterField<>(null, null, true, "expression1", null, true);
    SshWinRmAzureInfrastructure infrastructure = SshWinRmAzureInfrastructure.builder()
                                                     .credentialsRef(credentialsRef)
                                                     .connectorRef(ParameterField.createValueField("connector-ref"))
                                                     .subscriptionId(ParameterField.createValueField("subscription-id"))
                                                     .resourceGroup(ParameterField.createValueField("resource-group"))
                                                     .build();

    doThrow(new InvalidRequestException("Unresolved Expression : [expression1]"))
        .when(validator)
        .validateExpression(any(), any(), any(), eq(credentialsRef));
    assertThatThrownBy(() -> validator.validateInfrastructure(infrastructure, null, null))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Unresolved Expression : [expression1]");
  }

  @Test
  @Owner(developers = FILIP)
  @Category(UnitTests.class)
  public void testValidateSshWinRmAzureInfrastructureConnectorIsExpression() {
    ParameterField connectorRef = new ParameterField<>(null, null, true, "expression1", null, true);
    SshWinRmAzureInfrastructure infrastructure = SshWinRmAzureInfrastructure.builder()
                                                     .credentialsRef(ParameterField.createValueField("credentials-ref"))
                                                     .connectorRef(connectorRef)
                                                     .subscriptionId(ParameterField.createValueField("subscription-id"))
                                                     .resourceGroup(ParameterField.createValueField("resource-group"))
                                                     .build();

    doThrow(new InvalidRequestException("Unresolved Expression : [expression1]"))
        .when(validator)
        .validateExpression(eq(connectorRef), any(), any(), any());
    assertThatThrownBy(() -> validator.validateInfrastructure(infrastructure, null, null))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Unresolved Expression : [expression1]");
  }

  @Test
  @Owner(developers = FILIP)
  @Category(UnitTests.class)
  public void testValidateSshWinRmAzureInfrastructureSubscriptionIsExpression() {
    ParameterField subscriptionId = new ParameterField<>(null, null, true, "expression2", null, true);
    SshWinRmAzureInfrastructure infrastructure = SshWinRmAzureInfrastructure.builder()
                                                     .credentialsRef(ParameterField.createValueField("credentials-ref"))
                                                     .connectorRef(ParameterField.createValueField("connector-ref"))
                                                     .subscriptionId(subscriptionId)
                                                     .resourceGroup(ParameterField.createValueField("resource-group"))
                                                     .build();

    doThrow(new InvalidRequestException("Unresolved Expression : [expression2]"))
        .when(validator)
        .validateExpression(any(), eq(subscriptionId), any(), any());
    assertThatThrownBy(() -> validator.validateInfrastructure(infrastructure, null, null))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Unresolved Expression : [expression2]");
  }

  @Test
  @Owner(developers = FILIP)
  @Category(UnitTests.class)
  public void testValidateSshWinRmAzureInfrastructureResourceGroupIsExpression() {
    ParameterField resourceGroup = new ParameterField<>(null, null, true, "expression2", null, true);
    SshWinRmAzureInfrastructure infrastructure = SshWinRmAzureInfrastructure.builder()
                                                     .credentialsRef(ParameterField.createValueField("credentials-ref"))
                                                     .connectorRef(ParameterField.createValueField("connector-ref"))
                                                     .subscriptionId(ParameterField.createValueField("subscription-id"))
                                                     .resourceGroup(resourceGroup)
                                                     .build();

    doThrow(new InvalidRequestException("Unresolved Expression : [expression2]"))
        .when(validator)
        .validateExpression(any(), any(), eq(resourceGroup), any());
    assertThatThrownBy(() -> validator.validateInfrastructure(infrastructure, null, null))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Unresolved Expression : [expression2]");
  }
  @Test
  @Owner(developers = FILIP)
  @Category(UnitTests.class)
  public void testValidatePdcInfrastructureSshKeyExpression() {
    ParameterField credentialsRef = new ParameterField<>(null, null, true, "expression1", null, true);
    PdcInfrastructure infrastructure = PdcInfrastructure.builder()
                                           .credentialsRef(credentialsRef)
                                           .hosts(ParameterField.createValueField(Arrays.asList("host1", "host2")))
                                           .build();

    doThrow(new InvalidRequestException("Unresolved Expression : [expression1]"))
        .when(validator)
        .validateExpression(eq(credentialsRef));

    assertThatThrownBy(() -> validator.validateInfrastructure(infrastructure, null, null))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Unresolved Expression : [expression1]");
  }

  @Test
  @Owner(developers = ARVIND)
  @Category(UnitTests.class)
  public void testValidateElastigroupInfrastructure() {
    StoreConfig storeConfig =
        InlineStoreConfig.builder().content(ParameterField.createValueField("this is content")).build();

    ElastigroupInfrastructure infrastructure =
        ElastigroupInfrastructure.builder()
            .connectorRef(ParameterField.createValueField("connector"))
            .configuration(
                ElastigroupConfiguration.builder()
                    .store(StoreConfigWrapper.builder().type(StoreConfigType.INLINE).spec(storeConfig).build())
                    .build())
            .build();
    Ambiance ambiance = Mockito.mock(Ambiance.class);
    doThrow(InvalidRequestException.class).when(validator).validateExpression(any());
    assertThatThrownBy(() -> validator.validateInfrastructure(infrastructure, ambiance, null))
        .isInstanceOf(InvalidRequestException.class);
    doNothing().when(validator).validateExpression(any());
    assertThatCode(() -> validator.validateInfrastructure(infrastructure, ambiance, null)).doesNotThrowAnyException();
  }

  @Test
  @Owner(developers = LOVISH_BANSAL)
  @Category(UnitTests.class)
  public void testValidateAsgInfrastructure() {
    AsgInfrastructure infrastructure = AsgInfrastructure.builder()
                                           .connectorRef(ParameterField.createValueField("connector"))
                                           .region(ParameterField.createValueField("region"))
                                           .build();
    Ambiance ambiance = Mockito.mock(Ambiance.class);
    doThrow(InvalidRequestException.class).when(validator).validateExpression(any(), any());
    assertThatThrownBy(() -> validator.validateInfrastructure(infrastructure, ambiance, null))
        .isInstanceOf(InvalidRequestException.class);
    doNothing().when(validator).validateExpression(any(), any());
    assertThatCode(() -> validator.validateInfrastructure(infrastructure, ambiance, null)).doesNotThrowAnyException();
  }

  @Test
  @Owner(developers = VITALIE)
  @Category(UnitTests.class)
  public void testValidateSshWinRmAwsInfrastructure() {
    SshWinRmAwsInfrastructure.SshWinRmAwsInfrastructureBuilder builder = SshWinRmAwsInfrastructure.builder();

    ParameterField credentialsRef = new ParameterField<>(null, null, true, "expression1", null, true);
    builder.credentialsRef(credentialsRef).connectorRef(ParameterField.createValueField("value")).build();

    doThrow(new InvalidRequestException("Unresolved Expression : [expression1]"))
        .when(validator)
        .validateExpression(any(), eq(credentialsRef), any(), any());

    assertThatThrownBy(() -> validator.validateInfrastructure(builder.build(), null, null))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Unresolved Expression : [expression1]");

    ParameterField connectorRef2 = new ParameterField<>(null, null, true, "expression2", null, true);
    builder.connectorRef(new ParameterField<>(null, null, true, "expression2", null, true))
        .credentialsRef(ParameterField.createValueField("value"))
        .build();
    doThrow(new InvalidRequestException("Unresolved Expression : [expression2]"))
        .when(validator)
        .validateExpression(eq(connectorRef2), any(), any(), any());

    assertThatThrownBy(() -> validator.validateInfrastructure(builder.build(), null, null))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Unresolved Expression : [expression2]");
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testValidateAzureWebAppInfrastructure() {
    AzureWebAppInfrastructure infrastructure = AzureWebAppInfrastructure.builder()
                                                   .connectorRef(ParameterField.createValueField("connector-ref"))
                                                   .subscriptionId(ParameterField.createValueField("subscription-id"))
                                                   .resourceGroup(ParameterField.createValueField("resource-group"))
                                                   .build();

    validator.validateInfrastructure(infrastructure, null, null);
  }
}
