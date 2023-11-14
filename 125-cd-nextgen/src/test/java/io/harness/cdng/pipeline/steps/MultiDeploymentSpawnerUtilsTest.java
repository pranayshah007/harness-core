/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.pipeline.steps;

import static io.harness.rule.OwnerRule.HINGER;
import static io.harness.rule.OwnerRule.MLUKIC;
import static io.harness.rule.OwnerRule.SAHIL;
import static io.harness.rule.OwnerRule.TATHAGAT;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.cdng.creator.plan.stage.DeploymentStageConfig;
import io.harness.cdng.creator.plan.stage.DeploymentStageNode;
import io.harness.cdng.creator.plan.stage.v1.DeploymentStageConfigV1;
import io.harness.cdng.creator.plan.stage.v1.DeploymentStageNodeV1;
import io.harness.cdng.envgroup.yaml.EnvironmentGroupYaml;
import io.harness.cdng.environment.yaml.EnvironmentYamlV2;
import io.harness.cdng.environment.yaml.EnvironmentsYaml;
import io.harness.cdng.infra.yaml.InfraStructureDefinitionYaml;
import io.harness.cdng.service.beans.ServiceYamlV2;
import io.harness.cdng.service.beans.ServicesYaml;
import io.harness.data.structure.ListUtils;
import io.harness.encryption.Scope;
import io.harness.exception.InvalidRequestException;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;

import java.util.Map;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class MultiDeploymentSpawnerUtilsTest extends CategoryTest {
  @Test
  @Owner(developers = {SAHIL, MLUKIC})
  @Category(UnitTests.class)
  public void testHasMultiDeploymentConfigured() {
    ServiceYamlV2 serviceYamlV2 = ServiceYamlV2.builder().serviceRef(ParameterField.createValueField("svc1")).build();
    ServicesYaml servicesYaml =
        ServicesYaml.builder().values(ParameterField.createValueField(ListUtils.newArrayList(serviceYamlV2))).build();
    testHasMultiDeploymentConfiguredForDeploymentStageNode(servicesYaml);
    testHasMultiDeploymentConfiguredForDeploymentStageNodeV1(servicesYaml);
  }

  private void testHasMultiDeploymentConfiguredForDeploymentStageNode(ServicesYaml servicesYaml) {
    DeploymentStageNode deploymentStageNode =
        DeploymentStageNode.builder()
            .deploymentStageConfig(DeploymentStageConfig.builder().services(servicesYaml).build())
            .build();
    assertThat(MultiDeploymentSpawnerUtils.hasMultiDeploymentConfigured(deploymentStageNode)).isTrue();
    deploymentStageNode =
        DeploymentStageNode.builder().deploymentStageConfig(DeploymentStageConfig.builder().build()).build();
    assertThat(MultiDeploymentSpawnerUtils.hasMultiDeploymentConfigured(deploymentStageNode)).isFalse();
  }

  private void testHasMultiDeploymentConfiguredForDeploymentStageNodeV1(ServicesYaml servicesYaml) {
    DeploymentStageNodeV1 deploymentStageNode =
        DeploymentStageNodeV1.builder().spec(DeploymentStageConfigV1.builder().services(servicesYaml).build()).build();
    assertThat(MultiDeploymentSpawnerUtils.hasMultiDeploymentConfigured(deploymentStageNode)).isTrue();
    deploymentStageNode = DeploymentStageNodeV1.builder().spec(DeploymentStageConfigV1.builder().build()).build();
    assertThat(MultiDeploymentSpawnerUtils.hasMultiDeploymentConfigured(deploymentStageNode)).isFalse();
  }

  @Test
  @Owner(developers = {SAHIL, MLUKIC})
  @Category(UnitTests.class)
  public void testHasMultiDeploymentConfiguredWithServices() {
    ServiceYamlV2 serviceYamlV2 = ServiceYamlV2.builder().serviceRef(ParameterField.createValueField("svc1")).build();
    ServicesYaml servicesYaml =
        ServicesYaml.builder().values(ParameterField.createValueField(ListUtils.newArrayList(serviceYamlV2))).build();
    testHasMultiDeploymentConfiguredWithServicesForDeploymentStageNode(servicesYaml);
    testHasMultiDeploymentConfiguredWithServicesForDeploymentStageNodeV1(servicesYaml);
  }

  private void testHasMultiDeploymentConfiguredWithServicesForDeploymentStageNode(ServicesYaml servicesYaml) {
    DeploymentStageNode deploymentStageNode =
        DeploymentStageNode.builder()
            .deploymentStageConfig(DeploymentStageConfig.builder().services(servicesYaml).build())
            .build();
    assertThat(MultiDeploymentSpawnerUtils.hasMultiDeploymentConfigured(deploymentStageNode)).isTrue();
    deploymentStageNode =
        DeploymentStageNode.builder().deploymentStageConfig(DeploymentStageConfig.builder().build()).build();
    assertThat(MultiDeploymentSpawnerUtils.hasMultiDeploymentConfigured(deploymentStageNode)).isFalse();
  }

  private void testHasMultiDeploymentConfiguredWithServicesForDeploymentStageNodeV1(ServicesYaml servicesYaml) {
    DeploymentStageNodeV1 deploymentStageNode =
        DeploymentStageNodeV1.builder().spec(DeploymentStageConfigV1.builder().services(servicesYaml).build()).build();
    assertThat(MultiDeploymentSpawnerUtils.hasMultiDeploymentConfigured(deploymentStageNode)).isTrue();
    deploymentStageNode = DeploymentStageNodeV1.builder().spec(DeploymentStageConfigV1.builder().build()).build();
    assertThat(MultiDeploymentSpawnerUtils.hasMultiDeploymentConfigured(deploymentStageNode)).isFalse();
  }

  @Test
  @Owner(developers = {SAHIL, MLUKIC})
  @Category(UnitTests.class)
  public void testHasMultiDeploymentConfiguredWithEnvironments() {
    EnvironmentYamlV2 environmentYamlV2 =
        EnvironmentYamlV2.builder().environmentRef(ParameterField.createValueField("env1")).build();
    EnvironmentsYaml environmentsYaml =
        EnvironmentsYaml.builder()
            .values(ParameterField.createValueField(ListUtils.newArrayList(environmentYamlV2)))
            .build();
    testHasMultiDeploymentConfiguredWithEnvironmentsForDeploymentStageNode(environmentsYaml);
    testHasMultiDeploymentConfiguredWithEnvironmentsForDeploymentStageNodeV1(environmentsYaml);
  }

  private void testHasMultiDeploymentConfiguredWithEnvironmentsForDeploymentStageNode(
      EnvironmentsYaml environmentsYaml) {
    DeploymentStageNode deploymentStageNode =
        DeploymentStageNode.builder()
            .deploymentStageConfig(DeploymentStageConfig.builder().environments(environmentsYaml).build())
            .build();
    assertThat(MultiDeploymentSpawnerUtils.hasMultiDeploymentConfigured(deploymentStageNode)).isTrue();
    deploymentStageNode =
        DeploymentStageNode.builder().deploymentStageConfig(DeploymentStageConfig.builder().build()).build();
    assertThat(MultiDeploymentSpawnerUtils.hasMultiDeploymentConfigured(deploymentStageNode)).isFalse();
  }

  private void testHasMultiDeploymentConfiguredWithEnvironmentsForDeploymentStageNodeV1(
      EnvironmentsYaml environmentsYaml) {
    DeploymentStageNodeV1 deploymentStageNode =
        DeploymentStageNodeV1.builder()
            .spec(DeploymentStageConfigV1.builder().environments(environmentsYaml).build())
            .build();
    assertThat(MultiDeploymentSpawnerUtils.hasMultiDeploymentConfigured(deploymentStageNode)).isTrue();
    deploymentStageNode = DeploymentStageNodeV1.builder().spec(DeploymentStageConfigV1.builder().build()).build();
    assertThat(MultiDeploymentSpawnerUtils.hasMultiDeploymentConfigured(deploymentStageNode)).isFalse();
  }

  @Test
  @Owner(developers = {SAHIL, MLUKIC})
  @Category(UnitTests.class)
  public void testHasMultiDeploymentConfiguredWithEnvironmentGroup() {
    EnvironmentYamlV2 environmentYamlV2 =
        EnvironmentYamlV2.builder().environmentRef(ParameterField.createValueField("env1")).build();
    testHasMultiDeploymentConfiguredWithEnvironmentGroupForDeploymentStageNode(environmentYamlV2);
    testHasMultiDeploymentConfiguredWithEnvironmentGroupForDeploymentStageNodeV1(environmentYamlV2);
  }

  private void testHasMultiDeploymentConfiguredWithEnvironmentGroupForDeploymentStageNode(
      EnvironmentYamlV2 environmentYamlV2) {
    DeploymentStageNode deploymentStageNode =
        DeploymentStageNode.builder()
            .deploymentStageConfig(DeploymentStageConfig.builder()
                                       .environmentGroup(EnvironmentGroupYaml.builder()
                                                             .environments(ParameterField.createValueField(
                                                                 ListUtils.newArrayList(environmentYamlV2)))
                                                             .build())
                                       .build())
            .build();
    assertThat(MultiDeploymentSpawnerUtils.hasMultiDeploymentConfigured(deploymentStageNode)).isTrue();
    deploymentStageNode =
        DeploymentStageNode.builder().deploymentStageConfig(DeploymentStageConfig.builder().build()).build();
    assertThat(MultiDeploymentSpawnerUtils.hasMultiDeploymentConfigured(deploymentStageNode)).isFalse();
  }

  private void testHasMultiDeploymentConfiguredWithEnvironmentGroupForDeploymentStageNodeV1(
      EnvironmentYamlV2 environmentYamlV2) {
    DeploymentStageNodeV1 deploymentStageNode =
        DeploymentStageNodeV1.builder()
            .spec(DeploymentStageConfigV1.builder()
                      .environmentGroup(
                          EnvironmentGroupYaml.builder()
                              .environments(ParameterField.createValueField(ListUtils.newArrayList(environmentYamlV2)))
                              .build())
                      .build())
            .build();
    assertThat(MultiDeploymentSpawnerUtils.hasMultiDeploymentConfigured(deploymentStageNode)).isTrue();
    deploymentStageNode = DeploymentStageNodeV1.builder().spec(DeploymentStageConfigV1.builder().build()).build();
    assertThat(MultiDeploymentSpawnerUtils.hasMultiDeploymentConfigured(deploymentStageNode)).isFalse();
  }

  @Test
  @Owner(developers = {SAHIL, MLUKIC})
  @Category(UnitTests.class)
  public void testHasMultiDeploymentConfiguredWithGitOpsEnabled() {
    EnvironmentYamlV2 environmentYamlV2 =
        EnvironmentYamlV2.builder().environmentRef(ParameterField.createValueField("env1")).build();
    testHasMultiDeploymentConfiguredWithGitOpsEnabledForDeploymentStageNode(environmentYamlV2);
    testHasMultiDeploymentConfiguredWithGitOpsEnabledForDeploymentStageNodeV1(environmentYamlV2);
  }

  private void testHasMultiDeploymentConfiguredWithGitOpsEnabledForDeploymentStageNode(
      EnvironmentYamlV2 environmentYamlV2) {
    DeploymentStageNode deploymentStageNode =
        DeploymentStageNode.builder()
            .deploymentStageConfig(DeploymentStageConfig.builder()
                                       .gitOpsEnabled(true)
                                       .environmentGroup(EnvironmentGroupYaml.builder()
                                                             .environments(ParameterField.createValueField(
                                                                 ListUtils.newArrayList(environmentYamlV2)))
                                                             .build())
                                       .build())
            .build();
    assertThat(MultiDeploymentSpawnerUtils.hasMultiDeploymentConfigured(deploymentStageNode)).isFalse();
    deploymentStageNode =
        DeploymentStageNode.builder().deploymentStageConfig(DeploymentStageConfig.builder().build()).build();
    assertThat(MultiDeploymentSpawnerUtils.hasMultiDeploymentConfigured(deploymentStageNode)).isFalse();
  }

  private void testHasMultiDeploymentConfiguredWithGitOpsEnabledForDeploymentStageNodeV1(
      EnvironmentYamlV2 environmentYamlV2) {
    DeploymentStageNodeV1 deploymentStageNode =
        DeploymentStageNodeV1.builder()
            .spec(DeploymentStageConfigV1.builder()
                      .gitOpsEnabled(true)
                      .environmentGroup(
                          EnvironmentGroupYaml.builder()
                              .environments(ParameterField.createValueField(ListUtils.newArrayList(environmentYamlV2)))
                              .build())
                      .build())
            .build();
    assertThat(MultiDeploymentSpawnerUtils.hasMultiDeploymentConfigured(deploymentStageNode)).isFalse();
    deploymentStageNode = DeploymentStageNodeV1.builder().spec(DeploymentStageConfigV1.builder().build()).build();
    assertThat(MultiDeploymentSpawnerUtils.hasMultiDeploymentConfigured(deploymentStageNode)).isFalse();
  }

  @Test
  @Owner(developers = {SAHIL, MLUKIC})
  @Category(UnitTests.class)
  public void testHasMultiDeploymentConfiguredWithGitOpsEnabledMultiService() {
    ServiceYamlV2 serviceYamlV2 = ServiceYamlV2.builder().serviceRef(ParameterField.createValueField("svc1")).build();
    ServicesYaml servicesYaml =
        ServicesYaml.builder().values(ParameterField.createValueField(ListUtils.newArrayList(serviceYamlV2))).build();
    testHasMultiDeploymentConfiguredWithGitOpsEnabledMultiServiceForDeploymentStageNode(servicesYaml);
    testHasMultiDeploymentConfiguredWithGitOpsEnabledMultiServiceForDeploymentStageNodeV1(servicesYaml);
  }

  private void testHasMultiDeploymentConfiguredWithGitOpsEnabledMultiServiceForDeploymentStageNode(
      ServicesYaml servicesYaml) {
    DeploymentStageNode deploymentStageNode =
        DeploymentStageNode.builder()
            .deploymentStageConfig(DeploymentStageConfig.builder().gitOpsEnabled(true).services(servicesYaml).build())
            .build();
    assertThat(MultiDeploymentSpawnerUtils.hasMultiDeploymentConfigured(deploymentStageNode)).isTrue();
    deploymentStageNode =
        DeploymentStageNode.builder().deploymentStageConfig(DeploymentStageConfig.builder().build()).build();
    assertThat(MultiDeploymentSpawnerUtils.hasMultiDeploymentConfigured(deploymentStageNode)).isFalse();
  }

  private void testHasMultiDeploymentConfiguredWithGitOpsEnabledMultiServiceForDeploymentStageNodeV1(
      ServicesYaml servicesYaml) {
    DeploymentStageNodeV1 deploymentStageNode =
        DeploymentStageNodeV1.builder()
            .spec(DeploymentStageConfigV1.builder().gitOpsEnabled(true).services(servicesYaml).build())
            .build();
    assertThat(MultiDeploymentSpawnerUtils.hasMultiDeploymentConfigured(deploymentStageNode)).isTrue();
    deploymentStageNode = DeploymentStageNodeV1.builder().spec(DeploymentStageConfigV1.builder().build()).build();
    assertThat(MultiDeploymentSpawnerUtils.hasMultiDeploymentConfigured(deploymentStageNode)).isFalse();
  }

  @Test
  @Owner(developers = {SAHIL, MLUKIC})
  @Category(UnitTests.class)
  public void testValidateMultiServiceInfra() {
    ServicesYaml servicesYaml =
        ServicesYaml.builder().values(ParameterField.createValueField(ListUtils.newArrayList())).build();
    testValidateMultiServiceInfraForDeploymentStageConfig(servicesYaml);
    testValidateMultiServiceInfraForDeploymentStageConfigV1(servicesYaml);
  }

  private void testValidateMultiServiceInfraForDeploymentStageConfig(ServicesYaml servicesYaml) {
    DeploymentStageNode deploymentStageNode =
        DeploymentStageNode.builder()
            .deploymentStageConfig(DeploymentStageConfig.builder().services(servicesYaml).build())
            .build();
    assertThat(MultiDeploymentSpawnerUtils.hasMultiDeploymentConfigured(deploymentStageNode)).isTrue();
    assertThatThrownBy(
        () -> MultiDeploymentSpawnerUtils.validateMultiServiceInfra(deploymentStageNode.getDeploymentStageConfig()))
        .isInstanceOf(InvalidRequestException.class);
  }

  private void testValidateMultiServiceInfraForDeploymentStageConfigV1(ServicesYaml servicesYaml) {
    DeploymentStageNodeV1 deploymentStageNode =
        DeploymentStageNodeV1.builder().spec(DeploymentStageConfigV1.builder().services(servicesYaml).build()).build();
    assertThat(MultiDeploymentSpawnerUtils.hasMultiDeploymentConfigured(deploymentStageNode)).isTrue();
    assertThatThrownBy(() -> MultiDeploymentSpawnerUtils.validateMultiServiceInfra(deploymentStageNode.getSpec()))
        .isInstanceOf(InvalidRequestException.class);
  }

  @Test
  @Owner(developers = {SAHIL, MLUKIC})
  @Category(UnitTests.class)
  public void testValidateMultiServiceInfraNoEnvironment() {
    EnvironmentsYaml environmentsYaml =
        EnvironmentsYaml.builder().values(ParameterField.createValueField(ListUtils.newArrayList())).build();
    testValidateMultiServiceInfraNoEnvironmentForDeploymentStageConfig(environmentsYaml);
    testValidateMultiServiceInfraNoEnvironmentForDeploymentStageConfigV1(environmentsYaml);
  }

  private void testValidateMultiServiceInfraNoEnvironmentForDeploymentStageConfig(EnvironmentsYaml environmentsYaml) {
    DeploymentStageNode deploymentStageNode =
        DeploymentStageNode.builder()
            .deploymentStageConfig(DeploymentStageConfig.builder().environments(environmentsYaml).build())
            .build();
    assertThat(MultiDeploymentSpawnerUtils.hasMultiDeploymentConfigured(deploymentStageNode)).isTrue();
    assertThatThrownBy(
        () -> MultiDeploymentSpawnerUtils.validateMultiServiceInfra(deploymentStageNode.getDeploymentStageConfig()))
        .isInstanceOf(InvalidRequestException.class);
  }

  private void testValidateMultiServiceInfraNoEnvironmentForDeploymentStageConfigV1(EnvironmentsYaml environmentsYaml) {
    DeploymentStageNodeV1 deploymentStageNode =
        DeploymentStageNodeV1.builder()
            .spec(DeploymentStageConfigV1.builder().environments(environmentsYaml).build())
            .build();
    assertThat(MultiDeploymentSpawnerUtils.hasMultiDeploymentConfigured(deploymentStageNode)).isTrue();
    assertThatThrownBy(() -> MultiDeploymentSpawnerUtils.validateMultiServiceInfra(deploymentStageNode.getSpec()))
        .isInstanceOf(InvalidRequestException.class);
  }

  @Test
  @Owner(developers = {SAHIL, MLUKIC})
  @Category(UnitTests.class)
  public void testValidateMultiServiceInfraNoInfrastructures() {
    EnvironmentYamlV2 environmentYamlV2 =
        EnvironmentYamlV2.builder().environmentRef(ParameterField.createValueField("env1")).build();
    EnvironmentsYaml environmentsYaml =
        EnvironmentsYaml.builder()
            .values(ParameterField.createValueField(ListUtils.newArrayList(environmentYamlV2)))
            .build();
    testValidateMultiServiceInfraNoInfrastructuresForDeploymentStageConfig(environmentsYaml);
    testValidateMultiServiceInfraNoInfrastructuresForDeploymentStageConfigV1(environmentsYaml);
  }

  private void testValidateMultiServiceInfraNoInfrastructuresForDeploymentStageConfig(
      EnvironmentsYaml environmentsYaml) {
    DeploymentStageNode deploymentStageNode =
        DeploymentStageNode.builder()
            .deploymentStageConfig(DeploymentStageConfig.builder().environments(environmentsYaml).build())
            .build();
    assertThat(MultiDeploymentSpawnerUtils.hasMultiDeploymentConfigured(deploymentStageNode)).isTrue();
    assertThatThrownBy(
        () -> MultiDeploymentSpawnerUtils.validateMultiServiceInfra(deploymentStageNode.getDeploymentStageConfig()))
        .isInstanceOf(InvalidRequestException.class);
  }

  private void testValidateMultiServiceInfraNoInfrastructuresForDeploymentStageConfigV1(
      EnvironmentsYaml environmentsYaml) {
    DeploymentStageNodeV1 deploymentStageNode =
        DeploymentStageNodeV1.builder()
            .spec(DeploymentStageConfigV1.builder().environments(environmentsYaml).build())
            .build();
    assertThat(MultiDeploymentSpawnerUtils.hasMultiDeploymentConfigured(deploymentStageNode)).isTrue();
    assertThatThrownBy(() -> MultiDeploymentSpawnerUtils.validateMultiServiceInfra(deploymentStageNode.getSpec()))
        .isInstanceOf(InvalidRequestException.class);
  }

  @Test
  @Owner(developers = HINGER)
  @Category(UnitTests.class)
  public void testGetScopedEnvRefWithEnvironmentGroup() {
    EnvironmentYamlV2 environmentYamlV2 = EnvironmentYamlV2.builder()
                                              .environmentRef(ParameterField.createValueField("env1"))
                                              .gitBranch("envGitBranchValue")
                                              .build();
    InfraStructureDefinitionYaml infraStructureDefinitionYaml =
        InfraStructureDefinitionYaml.builder().identifier(ParameterField.createValueField("identifier")).build();
    Map<String, String> envMap = MultiDeploymentSpawnerUtils.getMapFromEnvironmentYaml(
        environmentYamlV2, infraStructureDefinitionYaml, Scope.ACCOUNT);
    assertThat(envMap.get("environmentRef")).isEqualTo("account.env1");
    assertThat(envMap.get("envGitBranch")).isEqualTo("envGitBranchValue");
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testGetMapFromEnvironmentYamlInfraAsNull() {
    EnvironmentYamlV2 environmentYamlV2 =
        EnvironmentYamlV2.builder().environmentRef(ParameterField.createValueField("account.env1")).build();

    assertThatThrownBy(
        () -> MultiDeploymentSpawnerUtils.getMapFromEnvironmentYaml(environmentYamlV2, null, Scope.ACCOUNT))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining(
            "Infrastructure Definition is not provided for environment account.env1, Please provide infrastructure definition and try again");
  }

  @Test
  @Owner(developers = HINGER)
  @Category(UnitTests.class)
  public void testGetScopedEnvRefWithEnvironmentGroup_withScopedEnvRef() {
    EnvironmentYamlV2 environmentYamlV2 =
        EnvironmentYamlV2.builder().environmentRef(ParameterField.createValueField("account.env1")).build();
    InfraStructureDefinitionYaml infraStructureDefinitionYaml =
        InfraStructureDefinitionYaml.builder().identifier(ParameterField.createValueField("identifier")).build();
    Map<String, String> envMap = MultiDeploymentSpawnerUtils.getMapFromEnvironmentYaml(
        environmentYamlV2, infraStructureDefinitionYaml, Scope.ACCOUNT);
    assertThat(envMap.get("environmentRef")).isEqualTo("account.env1");
  }

  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void testGetUuidForMultiDeploymentWithServices() {
    ServicesYaml servicesYaml = ServicesYaml.builder().uuid("1234567890").build();

    DeploymentStageConfigV1 deploymentStageConfig = DeploymentStageConfigV1.builder().services(servicesYaml).build();

    DeploymentStageNodeV1 deploymentStageNode = DeploymentStageNodeV1.builder().spec(deploymentStageConfig).build();

    assertThat(MultiDeploymentSpawnerUtils.getUuidForMultiDeployment(deploymentStageNode)).isEqualTo("1234567890");
  }

  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void testGetUuidForMultiDeploymentWithGitOpsEnabled() {
    DeploymentStageConfigV1 deploymentStageConfig = DeploymentStageConfigV1.builder().build();

    DeploymentStageNodeV1 deploymentStageNode = DeploymentStageNodeV1.builder().spec(deploymentStageConfig).build();
    deploymentStageNode.setUuid("0987654321");

    assertThat(MultiDeploymentSpawnerUtils.getUuidForMultiDeployment(deploymentStageNode)).isEqualTo("0987654321");
  }

  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void testGetUuidForMultiDeploymentWithGitOpsDisabledAndEnvironments() {
    EnvironmentsYaml environmentsYaml = EnvironmentsYaml.builder().uuid("123456654321").build();

    DeploymentStageConfigV1 deploymentStageConfig =
        DeploymentStageConfigV1.builder().environments(environmentsYaml).build();

    DeploymentStageNodeV1 deploymentStageNode = DeploymentStageNodeV1.builder().spec(deploymentStageConfig).build();

    assertThat(MultiDeploymentSpawnerUtils.getUuidForMultiDeployment(deploymentStageNode)).isEqualTo("123456654321");
  }

  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void testGetUuidForMultiDeploymentWithGitOpsDisabledAndEnvironmentGroup() {
    EnvironmentGroupYaml environmentGroupYaml = EnvironmentGroupYaml.builder().uuid("098765567890").build();

    DeploymentStageConfigV1 deploymentStageConfig =
        DeploymentStageConfigV1.builder().environmentGroup(environmentGroupYaml).build();

    DeploymentStageNodeV1 deploymentStageNode = DeploymentStageNodeV1.builder().spec(deploymentStageConfig).build();

    assertThat(MultiDeploymentSpawnerUtils.getUuidForMultiDeployment(deploymentStageNode)).isEqualTo("098765567890");
  }
}