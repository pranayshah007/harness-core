/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.pipeline.steps;

import static io.harness.rule.OwnerRule.SAHIL;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.cdng.creator.plan.stage.DeploymentStageConfig;
import io.harness.cdng.creator.plan.stage.DeploymentStageNode;
import io.harness.cdng.envgroup.yaml.EnvironmentGroupYaml;
import io.harness.cdng.environment.yaml.EnvironmentYamlV2;
import io.harness.cdng.environment.yaml.EnvironmentsYaml;
import io.harness.cdng.service.beans.ServiceYamlV2;
import io.harness.cdng.service.beans.ServicesYaml;
import io.harness.exception.InvalidRequestException;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;

import io.fabric8.utils.Lists;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class MultiDeploymentSpawnerUtilsTest extends CategoryTest {
  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testHasMultiDeploymentConfigured() {
    ServiceYamlV2 serviceYamlV2 = ServiceYamlV2.builder().serviceRef(ParameterField.createValueField("svc1")).build();
    ServicesYaml servicesYaml =
        ServicesYaml.builder().values(ParameterField.createValueField(Lists.newArrayList(serviceYamlV2))).build();
    DeploymentStageNode deploymentStageNode =
        DeploymentStageNode.builder()
            .deploymentStageConfig(DeploymentStageConfig.builder().services(servicesYaml).build())
            .build();
    assertThat(MultiDeploymentSpawnerUtils.hasMultiDeploymentConfigured(deploymentStageNode)).isTrue();
    deploymentStageNode =
        DeploymentStageNode.builder().deploymentStageConfig(DeploymentStageConfig.builder().build()).build();
    assertThat(MultiDeploymentSpawnerUtils.hasMultiDeploymentConfigured(deploymentStageNode)).isFalse();
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testHasMultiDeploymentConfiguredWithServices() {
    ServiceYamlV2 serviceYamlV2 = ServiceYamlV2.builder().serviceRef(ParameterField.createValueField("svc1")).build();
    ServicesYaml servicesYaml =
        ServicesYaml.builder().values(ParameterField.createValueField(Lists.newArrayList(serviceYamlV2))).build();
    DeploymentStageNode deploymentStageNode =
        DeploymentStageNode.builder()
            .deploymentStageConfig(DeploymentStageConfig.builder().services(servicesYaml).build())
            .build();
    assertThat(MultiDeploymentSpawnerUtils.hasMultiDeploymentConfigured(deploymentStageNode)).isTrue();
    deploymentStageNode =
        DeploymentStageNode.builder().deploymentStageConfig(DeploymentStageConfig.builder().build()).build();
    assertThat(MultiDeploymentSpawnerUtils.hasMultiDeploymentConfigured(deploymentStageNode)).isFalse();
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testHasMultiDeploymentConfiguredWithEnvironments() {
    EnvironmentYamlV2 environmentYamlV2 =
        EnvironmentYamlV2.builder().environmentRef(ParameterField.createValueField("env1")).build();
    EnvironmentsYaml environmentsYaml =
        EnvironmentsYaml.builder()
            .values(ParameterField.createValueField(Lists.newArrayList(environmentYamlV2)))
            .build();
    DeploymentStageNode deploymentStageNode =
        DeploymentStageNode.builder()
            .deploymentStageConfig(DeploymentStageConfig.builder().environments(environmentsYaml).build())
            .build();
    assertThat(MultiDeploymentSpawnerUtils.hasMultiDeploymentConfigured(deploymentStageNode)).isTrue();
    deploymentStageNode =
        DeploymentStageNode.builder().deploymentStageConfig(DeploymentStageConfig.builder().build()).build();
    assertThat(MultiDeploymentSpawnerUtils.hasMultiDeploymentConfigured(deploymentStageNode)).isFalse();
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testHasMultiDeploymentConfiguredWithEnvironmentGroup() {
    EnvironmentYamlV2 environmentYamlV2 =
        EnvironmentYamlV2.builder().environmentRef(ParameterField.createValueField("env1")).build();
    DeploymentStageNode deploymentStageNode =
        DeploymentStageNode.builder()
            .deploymentStageConfig(DeploymentStageConfig.builder()
                                       .environmentGroup(EnvironmentGroupYaml.builder()
                                                             .environments(ParameterField.createValueField(
                                                                 Lists.newArrayList(environmentYamlV2)))
                                                             .build())
                                       .build())
            .build();
    assertThat(MultiDeploymentSpawnerUtils.hasMultiDeploymentConfigured(deploymentStageNode)).isTrue();
    deploymentStageNode =
        DeploymentStageNode.builder().deploymentStageConfig(DeploymentStageConfig.builder().build()).build();
    assertThat(MultiDeploymentSpawnerUtils.hasMultiDeploymentConfigured(deploymentStageNode)).isFalse();
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testHasMultiDeploymentConfiguredWithGitOpsEnabled() {
    EnvironmentYamlV2 environmentYamlV2 =
        EnvironmentYamlV2.builder().environmentRef(ParameterField.createValueField("env1")).build();
    DeploymentStageNode deploymentStageNode =
        DeploymentStageNode.builder()
            .deploymentStageConfig(DeploymentStageConfig.builder()
                                       .gitOpsEnabled(true)
                                       .environmentGroup(EnvironmentGroupYaml.builder()
                                                             .environments(ParameterField.createValueField(
                                                                 Lists.newArrayList(environmentYamlV2)))
                                                             .build())
                                       .build())
            .build();
    assertThat(MultiDeploymentSpawnerUtils.hasMultiDeploymentConfigured(deploymentStageNode)).isFalse();
    deploymentStageNode =
        DeploymentStageNode.builder().deploymentStageConfig(DeploymentStageConfig.builder().build()).build();
    assertThat(MultiDeploymentSpawnerUtils.hasMultiDeploymentConfigured(deploymentStageNode)).isFalse();
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testHasMultiDeploymentConfiguredWithGitOpsEnabledMultiService() {
    ServiceYamlV2 serviceYamlV2 = ServiceYamlV2.builder().serviceRef(ParameterField.createValueField("svc1")).build();
    ServicesYaml servicesYaml =
        ServicesYaml.builder().values(ParameterField.createValueField(Lists.newArrayList(serviceYamlV2))).build();
    DeploymentStageNode deploymentStageNode =
        DeploymentStageNode.builder()
            .deploymentStageConfig(DeploymentStageConfig.builder().gitOpsEnabled(true).services(servicesYaml).build())
            .build();
    assertThat(MultiDeploymentSpawnerUtils.hasMultiDeploymentConfigured(deploymentStageNode)).isTrue();
    deploymentStageNode =
        DeploymentStageNode.builder().deploymentStageConfig(DeploymentStageConfig.builder().build()).build();
    assertThat(MultiDeploymentSpawnerUtils.hasMultiDeploymentConfigured(deploymentStageNode)).isFalse();
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testValidateMultiServiceInfra() {
    ServicesYaml servicesYaml =
        ServicesYaml.builder().values(ParameterField.createValueField(Lists.newArrayList())).build();
    DeploymentStageNode deploymentStageNode =
        DeploymentStageNode.builder()
            .deploymentStageConfig(DeploymentStageConfig.builder().services(servicesYaml).build())
            .build();
    assertThat(MultiDeploymentSpawnerUtils.hasMultiDeploymentConfigured(deploymentStageNode)).isTrue();
    assertThatThrownBy(
        () -> MultiDeploymentSpawnerUtils.validateMultiServiceInfra(deploymentStageNode.getDeploymentStageConfig()))
        .isInstanceOf(InvalidRequestException.class);
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testValidateMultiServiceInfraNoEnvironment() {
    EnvironmentsYaml environmentsYaml =
        EnvironmentsYaml.builder().values(ParameterField.createValueField(Lists.newArrayList())).build();
    DeploymentStageNode deploymentStageNode =
        DeploymentStageNode.builder()
            .deploymentStageConfig(DeploymentStageConfig.builder().environments(environmentsYaml).build())
            .build();
    assertThat(MultiDeploymentSpawnerUtils.hasMultiDeploymentConfigured(deploymentStageNode)).isTrue();
    assertThatThrownBy(
        () -> MultiDeploymentSpawnerUtils.validateMultiServiceInfra(deploymentStageNode.getDeploymentStageConfig()))
        .isInstanceOf(InvalidRequestException.class);
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testValidateMultiServiceInfraNoInfrastructures() {
    EnvironmentYamlV2 environmentYamlV2 =
        EnvironmentYamlV2.builder().environmentRef(ParameterField.createValueField("env1")).build();
    EnvironmentsYaml environmentsYaml =
        EnvironmentsYaml.builder()
            .values(ParameterField.createValueField(Lists.newArrayList(environmentYamlV2)))
            .build();
    DeploymentStageNode deploymentStageNode =
        DeploymentStageNode.builder()
            .deploymentStageConfig(DeploymentStageConfig.builder().environments(environmentsYaml).build())
            .build();
    assertThat(MultiDeploymentSpawnerUtils.hasMultiDeploymentConfigured(deploymentStageNode)).isTrue();
    assertThatThrownBy(
        () -> MultiDeploymentSpawnerUtils.validateMultiServiceInfra(deploymentStageNode.getDeploymentStageConfig()))
        .isInstanceOf(InvalidRequestException.class);
  }
}