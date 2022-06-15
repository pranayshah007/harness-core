/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.infra;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.ABOSII;
import static io.harness.rule.OwnerRule.ACASIAN;
import static io.harness.rule.OwnerRule.FILIP;
import static io.harness.rule.OwnerRule.MLUKIC;
import static io.harness.rule.OwnerRule.PIYUSH_BHUWALKA;
import static io.harness.rule.OwnerRule.TMACARI;
import static io.harness.rule.OwnerRule.VAIBHAV_SI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.cdng.infra.beans.AzureWebAppInfrastructureOutcome;
import io.harness.cdng.infra.beans.InfrastructureOutcome;
import io.harness.cdng.infra.beans.K8sAzureInfrastructureOutcome;
import io.harness.cdng.infra.beans.K8sDirectInfrastructureOutcome;
import io.harness.cdng.infra.beans.K8sGcpInfrastructureOutcome;
import io.harness.cdng.infra.beans.PdcInfrastructureOutcome;
import io.harness.cdng.infra.beans.ServerlessAwsLambdaInfrastructureOutcome;
import io.harness.cdng.infra.beans.SshWinRmAzureInfrastructureOutcome;
import io.harness.cdng.infra.yaml.AzureWebAppInfrastructure;
import io.harness.cdng.infra.yaml.K8SDirectInfrastructure;
import io.harness.cdng.infra.yaml.K8sAzureInfrastructure;
import io.harness.cdng.infra.yaml.K8sGcpInfrastructure;
import io.harness.cdng.infra.yaml.PdcInfrastructure;
import io.harness.cdng.infra.yaml.ServerlessAwsLambdaInfrastructure;
import io.harness.cdng.infra.yaml.SshWinRmAzureInfrastructure;
import io.harness.cdng.service.steps.ServiceStepOutcome;
import io.harness.exception.InvalidArgumentsException;
import io.harness.ng.core.environment.beans.EnvironmentType;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;
import io.harness.steps.environment.EnvironmentOutcome;

import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(CDP)
public class InfrastructureMapperTest extends CategoryTest {
  private final EnvironmentOutcome environment =
      EnvironmentOutcome.builder().identifier("env").type(EnvironmentType.Production).build();
  private final ServiceStepOutcome serviceOutcome = ServiceStepOutcome.builder().identifier("service").build();

  @Test
  @Owner(developers = VAIBHAV_SI)
  @Category(UnitTests.class)
  public void testToOutcome() {
    K8SDirectInfrastructure k8SDirectInfrastructure = K8SDirectInfrastructure.builder()
                                                          .connectorRef(ParameterField.createValueField("connectorId"))
                                                          .namespace(ParameterField.createValueField("namespace"))
                                                          .releaseName(ParameterField.createValueField("release"))
                                                          .build();

    K8sDirectInfrastructureOutcome k8sDirectInfrastructureOutcome =
        K8sDirectInfrastructureOutcome.builder()
            .connectorRef("connectorId")
            .namespace("namespace")
            .releaseName("release")
            .environment(environment)
            .infrastructureKey("11f6673d11711af46238bf33972cb99a4a869244")
            .build();

    InfrastructureOutcome infrastructureOutcome =
        InfrastructureMapper.toOutcome(k8SDirectInfrastructure, environment, serviceOutcome);
    assertThat(infrastructureOutcome).isEqualTo(k8sDirectInfrastructureOutcome);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testToOutcomeEmptyValues() {
    K8SDirectInfrastructure emptyReleaseName = K8SDirectInfrastructure.builder()
                                                   .connectorRef(ParameterField.createValueField("connectorId"))
                                                   .namespace(ParameterField.createValueField("namespace"))
                                                   .releaseName(ParameterField.createValueField(""))
                                                   .build();

    assertThatThrownBy(() -> InfrastructureMapper.toOutcome(emptyReleaseName, environment, serviceOutcome))
        .isInstanceOf(InvalidArgumentsException.class);

    K8SDirectInfrastructure emptyNamespace = K8SDirectInfrastructure.builder()
                                                 .connectorRef(ParameterField.createValueField("connectorId"))
                                                 .namespace(ParameterField.createValueField(""))
                                                 .releaseName(ParameterField.createValueField("releaseName"))
                                                 .build();

    assertThatThrownBy(() -> InfrastructureMapper.toOutcome(emptyNamespace, environment, serviceOutcome))
        .isInstanceOf(InvalidArgumentsException.class);
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void testK8sGcpInfraMapper() {
    K8sGcpInfrastructure k8SGcpInfrastructure = K8sGcpInfrastructure.builder()
                                                    .connectorRef(ParameterField.createValueField("connectorId"))
                                                    .namespace(ParameterField.createValueField("namespace"))
                                                    .releaseName(ParameterField.createValueField("release"))
                                                    .cluster(ParameterField.createValueField("cluster"))
                                                    .build();

    K8sGcpInfrastructureOutcome k8sGcpInfrastructureOutcome =
        K8sGcpInfrastructureOutcome.builder()
            .connectorRef("connectorId")
            .namespace("namespace")
            .releaseName("release")
            .cluster("cluster")
            .environment(environment)
            .infrastructureKey("54874007d7082ff0ab54cd51865954f5e78c5c88")
            .build();

    InfrastructureOutcome infrastructureOutcome =
        InfrastructureMapper.toOutcome(k8SGcpInfrastructure, environment, serviceOutcome);
    assertThat(infrastructureOutcome).isEqualTo(k8sGcpInfrastructureOutcome);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testK8sGcpInfraMapperEmptyValues() {
    K8sGcpInfrastructure emptyNamespace = K8sGcpInfrastructure.builder()
                                              .connectorRef(ParameterField.createValueField("connectorId"))
                                              .namespace(ParameterField.createValueField(""))
                                              .releaseName(ParameterField.createValueField("release"))
                                              .cluster(ParameterField.createValueField("cluster"))
                                              .build();
    assertThatThrownBy(() -> InfrastructureMapper.toOutcome(emptyNamespace, environment, serviceOutcome))
        .isInstanceOf(InvalidArgumentsException.class);

    K8sGcpInfrastructure emptyReleaseName = K8sGcpInfrastructure.builder()
                                                .connectorRef(ParameterField.createValueField("connectorId"))
                                                .namespace(ParameterField.createValueField("namespace"))
                                                .releaseName(ParameterField.createValueField(""))
                                                .cluster(ParameterField.createValueField("cluster"))
                                                .build();
    assertThatThrownBy(() -> InfrastructureMapper.toOutcome(emptyReleaseName, environment, serviceOutcome))
        .isInstanceOf(InvalidArgumentsException.class);

    K8sGcpInfrastructure emptyClusterName = K8sGcpInfrastructure.builder()
                                                .connectorRef(ParameterField.createValueField("connectorId"))
                                                .namespace(ParameterField.createValueField("namespace"))
                                                .releaseName(ParameterField.createValueField("release"))
                                                .cluster(ParameterField.createValueField(""))
                                                .build();
    assertThatThrownBy(() -> InfrastructureMapper.toOutcome(emptyClusterName, environment, serviceOutcome))
        .isInstanceOf(InvalidArgumentsException.class);
  }

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void testServerlessAwsInfraMapper() {
    ServerlessAwsLambdaInfrastructure serverlessAwsLambdaInfrastructure =
        ServerlessAwsLambdaInfrastructure.builder()
            .connectorRef(ParameterField.createValueField("connectorId"))
            .region(ParameterField.createValueField("region"))
            .stage(ParameterField.createValueField("stage"))
            .build();

    ServerlessAwsLambdaInfrastructureOutcome serverlessAwsLambdaInfrastructureOutcome =
        ServerlessAwsLambdaInfrastructureOutcome.builder()
            .connectorRef("connectorId")
            .region("region")
            .stage("stage")
            .environment(environment)
            .infrastructureKey("ad53b5ff347a533d21b0d02bab1ae1d62506068c")
            .build();

    InfrastructureOutcome infrastructureOutcome =
        InfrastructureMapper.toOutcome(serverlessAwsLambdaInfrastructure, environment, serviceOutcome);
    assertThat(infrastructureOutcome).isEqualTo(serverlessAwsLambdaInfrastructureOutcome);
  }

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void testServerlessAwsInfraMapperEmptyValues() {
    ServerlessAwsLambdaInfrastructure emptyRegion = ServerlessAwsLambdaInfrastructure.builder()
                                                        .connectorRef(ParameterField.createValueField("connectorId"))
                                                        .region(ParameterField.createValueField(""))
                                                        .stage(ParameterField.createValueField("stage"))
                                                        .build();
    assertThatThrownBy(() -> InfrastructureMapper.toOutcome(emptyRegion, environment, serviceOutcome))
        .isInstanceOf(InvalidArgumentsException.class);

    ServerlessAwsLambdaInfrastructure emptyStage = ServerlessAwsLambdaInfrastructure.builder()
                                                       .connectorRef(ParameterField.createValueField("connectorId"))
                                                       .region(ParameterField.createValueField("region"))
                                                       .stage(ParameterField.createValueField(""))
                                                       .build();
    assertThatThrownBy(() -> InfrastructureMapper.toOutcome(emptyStage, environment, serviceOutcome));
  }

  @Owner(developers = FILIP)
  @Category(UnitTests.class)
  public void testPdcInfrastructureWithConnectorToOutcome() {
    PdcInfrastructure infrastructure =
        PdcInfrastructure.builder()
            .credentialsRef(ParameterField.createValueField("ssh-key-ref"))
            .connectorRef(ParameterField.createValueField("connector-ref"))
            .hostFilters(ParameterField.createValueField(Arrays.asList("host1", "host2")))
            .build();

    InfrastructureOutcome infrastructureOutcome =
        InfrastructureMapper.toOutcome(infrastructure, environment, serviceOutcome);

    assertThat(infrastructureOutcome)
        .isEqualToIgnoringGivenFields(PdcInfrastructureOutcome.builder()
                                          .credentialsRef("ssh-key-ref")
                                          .connectorRef("connector-ref")
                                          .hostFilters(Arrays.asList("host1", "host2"))
                                          .environment(environment)
                                          .build(),
            "infrastructureKey");
  }

  @Test
  @Owner(developers = FILIP)
  @Category(UnitTests.class)
  public void testPdcInfrastructureWithHostsToOutcome() {
    PdcInfrastructure infrastructure =
        PdcInfrastructure.builder()
            .credentialsRef(ParameterField.createValueField("ssh-key-ref"))
            .hosts(ParameterField.createValueField(Arrays.asList("host1", "host2", "host3")))
            .build();

    InfrastructureOutcome infrastructureOutcome =
        InfrastructureMapper.toOutcome(infrastructure, environment, serviceOutcome);

    assertThat(infrastructureOutcome)
        .isEqualToIgnoringGivenFields(PdcInfrastructureOutcome.builder()
                                          .credentialsRef("ssh-key-ref")
                                          .hosts(Arrays.asList("host1", "host2", "host3"))
                                          .environment(environment)
                                          .build(),
            "infrastructureKey");
  }

  @Test
  @Owner(developers = FILIP)
  @Category(UnitTests.class)
  public void testPdcInfrastructureEmptySshKeyRef() {
    PdcInfrastructure emptySshKeyRef =
        PdcInfrastructure.builder().connectorRef(ParameterField.createValueField("connector-ref")).build();

    assertThatThrownBy(() -> InfrastructureMapper.toOutcome(emptySshKeyRef, environment, serviceOutcome))
        .isInstanceOf(InvalidArgumentsException.class);
  }

  @Test
  @Owner(developers = FILIP)
  @Category(UnitTests.class)
  public void testPdcInfrastructureEmptyHostsAndConnector() {
    PdcInfrastructure emptySshKeyRef = PdcInfrastructure.builder()
                                           .credentialsRef(ParameterField.createValueField("ssh-key-ref"))
                                           .hosts(ParameterField.ofNull())
                                           .connectorRef(ParameterField.ofNull())
                                           .build();

    assertThatThrownBy(() -> InfrastructureMapper.toOutcome(emptySshKeyRef, environment, serviceOutcome))
        .isInstanceOf(InvalidArgumentsException.class);
  }

  @Test
  @Owner(developers = FILIP)
  @Category(UnitTests.class)
  public void testSshWinRmAzureInfrastructureToOutcome() {
    SshWinRmAzureInfrastructure infrastructure =
        SshWinRmAzureInfrastructure.builder()
            .connectorRef(ParameterField.createValueField("connector-ref"))
            .credentialsRef(ParameterField.createValueField("credentials-ref"))
            .resourceGroup(ParameterField.createValueField("res-group"))
            .subscriptionId(ParameterField.createValueField("sub-id"))
            .tags(ParameterField.createValueField(Collections.singletonMap("tag", "val")))
            .usePublicDns(ParameterField.createValueField(true))
            .build();

    InfrastructureOutcome infrastructureOutcome =
        InfrastructureMapper.toOutcome(infrastructure, environment, serviceOutcome);

    assertThat(infrastructureOutcome)
        .isEqualToIgnoringGivenFields(SshWinRmAzureInfrastructureOutcome.builder()
                                          .connectorRef("connector-ref")
                                          .credentialsRef("credentials-ref")
                                          .resourceGroup("res-group")
                                          .subscriptionId("sub-id")
                                          .tags(Collections.singletonMap("tag", "val"))
                                          .usePublicDns(true)
                                          .environment(environment)
                                          .build(),
            "infrastructureKey");
  }

  @Test
  @Owner(developers = FILIP)
  @Category(UnitTests.class)
  public void testSshWinRmAzureInfrastructureEmptyCredentialsRefAndResourceGroup() {
    SshWinRmAzureInfrastructure invalidInfra = SshWinRmAzureInfrastructure.builder()
                                                   .credentialsRef(ParameterField.ofNull())
                                                   .resourceGroup(ParameterField.ofNull())
                                                   .connectorRef(ParameterField.createValueField("connector-ref"))
                                                   .subscriptionId(ParameterField.createValueField("sub-id"))
                                                   .build();

    assertThatThrownBy(() -> InfrastructureMapper.toOutcome(invalidInfra, environment, serviceOutcome))
        .isInstanceOf(InvalidArgumentsException.class);
  }

  @Test
  @Owner(developers = FILIP)
  @Category(UnitTests.class)
  public void testSshWinRmAzureInfrastructureEmptySubscriptionIdAndConnectorId() {
    SshWinRmAzureInfrastructure invalidInfra = SshWinRmAzureInfrastructure.builder()
                                                   .subscriptionId(ParameterField.ofNull())
                                                   .connectorRef(ParameterField.ofNull())
                                                   .credentialsRef(ParameterField.createValueField("ssh-key-ref"))
                                                   .resourceGroup(ParameterField.createValueField("resource-id"))
                                                   .build();

    assertThatThrownBy(() -> InfrastructureMapper.toOutcome(invalidInfra, environment, serviceOutcome))
        .isInstanceOf(InvalidArgumentsException.class);
  }

  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void testK8sAzureInfraMapper() {
    K8sAzureInfrastructure k8SAzureInfrastructure =
        K8sAzureInfrastructure.builder()
            .connectorRef(ParameterField.createValueField("connectorId"))
            .namespace(ParameterField.createValueField("namespace"))
            .releaseName(ParameterField.createValueField("release"))
            .subscriptionId(ParameterField.createValueField("subscriptionId"))
            .resourceGroup(ParameterField.createValueField("resourceGroup"))
            .cluster(ParameterField.createValueField("cluster"))
            .build();

    K8sAzureInfrastructureOutcome k8sAzureInfrastructureOutcome =
        K8sAzureInfrastructureOutcome.builder()
            .connectorRef("connectorId")
            .namespace("namespace")
            .releaseName("release")
            .subscription("subscriptionId")
            .resourceGroup("resourceGroup")
            .cluster("cluster")
            .environment(environment)
            .infrastructureKey("8f62fc4abbc11a8400589ccac4b76f32ba0f7df2")
            .build();

    InfrastructureOutcome infrastructureOutcome =
        InfrastructureMapper.toOutcome(k8SAzureInfrastructure, environment, serviceOutcome);
    assertThat(infrastructureOutcome).isEqualTo(k8sAzureInfrastructureOutcome);
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testAzureWebAppInfraMapper() {
    AzureWebAppInfrastructure azureWebAppInfrastructure =
        AzureWebAppInfrastructure.builder()
            .connectorRef(ParameterField.createValueField("connectorId"))
            .subscriptionId(ParameterField.createValueField("subscriptionId"))
            .resourceGroup(ParameterField.createValueField("resourceGroup"))
            .appService(ParameterField.createValueField("appService"))
            .deploymentSlot(ParameterField.createValueField("deploymentSlot"))
            .build();

    InfrastructureOutcome infrastructureOutcome =
        InfrastructureMapper.toOutcome(azureWebAppInfrastructure, environment, serviceOutcome);
    assertThat(infrastructureOutcome)
        .isEqualToIgnoringGivenFields(AzureWebAppInfrastructureOutcome.builder()
                                          .connectorRef("connectorId")
                                          .subscription("subscriptionId")
                                          .resourceGroup("resourceGroup")
                                          .appService("appService")
                                          .deploymentSlot("deploymentSlot")
                                          .environment(environment)
                                          .build(),
            "infrastructureKey");
  }

  @Test
  @Owner(developers = MLUKIC)
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
    assertThatThrownBy(() -> InfrastructureMapper.toOutcome(emptyNamespace, environment, serviceOutcome))
        .isInstanceOf(InvalidArgumentsException.class);

    K8sAzureInfrastructure emptyReleaseName = K8sAzureInfrastructure.builder()
                                                  .connectorRef(ParameterField.createValueField("connectorId"))
                                                  .namespace(ParameterField.createValueField("namespace"))
                                                  .releaseName(ParameterField.createValueField(""))
                                                  .subscriptionId(ParameterField.createValueField("subscriptionId"))
                                                  .resourceGroup(ParameterField.createValueField("resourceGroup"))
                                                  .cluster(ParameterField.createValueField("cluster"))
                                                  .build();
    assertThatThrownBy(() -> InfrastructureMapper.toOutcome(emptyReleaseName, environment, serviceOutcome))
        .isInstanceOf(InvalidArgumentsException.class);

    K8sAzureInfrastructure emptySubscription = K8sAzureInfrastructure.builder()
                                                   .connectorRef(ParameterField.createValueField("connectorId"))
                                                   .namespace(ParameterField.createValueField("namespace"))
                                                   .releaseName(ParameterField.createValueField("release"))
                                                   .subscriptionId(ParameterField.createValueField(""))
                                                   .resourceGroup(ParameterField.createValueField("resourceGroup"))
                                                   .cluster(ParameterField.createValueField("cluster"))
                                                   .build();
    assertThatThrownBy(() -> InfrastructureMapper.toOutcome(emptySubscription, environment, serviceOutcome))
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
    assertThatThrownBy(() -> InfrastructureMapper.toOutcome(emptyResourceGroupName, environment, serviceOutcome))
        .isInstanceOf(InvalidArgumentsException.class);

    K8sAzureInfrastructure emptyClusterName = K8sAzureInfrastructure.builder()
                                                  .connectorRef(ParameterField.createValueField("connectorId"))
                                                  .namespace(ParameterField.createValueField("namespace"))
                                                  .releaseName(ParameterField.createValueField("release"))
                                                  .subscriptionId(ParameterField.createValueField("subscriptionId"))
                                                  .resourceGroup(ParameterField.createValueField("resourceGroup"))
                                                  .cluster(ParameterField.createValueField(""))
                                                  .build();
    assertThatThrownBy(() -> InfrastructureMapper.toOutcome(emptyClusterName, environment, serviceOutcome))
        .isInstanceOf(InvalidArgumentsException.class);
  }
}
