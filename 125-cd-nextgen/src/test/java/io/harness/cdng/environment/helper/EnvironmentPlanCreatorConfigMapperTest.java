/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.environment.helper;

import static io.harness.cdng.environment.helper.EnvironmentPlanCreatorConfigMapper.toEnvPlanCreatorConfigWithGitops;
import static io.harness.cdng.environment.helper.EnvironmentPlanCreatorConfigMapper.toEnvironmentPlanCreatorConfig;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.cdng.environment.yaml.EnvironmentPlanCreatorConfig;
import io.harness.cdng.environment.yaml.EnvironmentYamlV2;
import io.harness.cdng.gitops.yaml.ClusterYaml;
import io.harness.cdng.infra.mapper.InfrastructureEntityConfigMapper;
import io.harness.ng.core.environment.beans.EnvironmentType;
import io.harness.ng.core.infrastructure.entity.InfrastructureEntity;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;
import io.harness.yaml.core.variables.NGServiceOverrides;

import org.junit.Test;
import org.junit.experimental.categories.Category;

public class EnvironmentPlanCreatorConfigMapperTest extends CategoryTest {
  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  public void testToEnvironmentPlanCreatorConfig() {
    String envYaml = "environment:\n"
        + " name: \"env_name\"\n"
        + " identifier: \"envId\"\n"
        + " type: \"Production\"\n"
        + " tags:\n"
        + "   k: \"v\"\n"
        + " accountId: \"accId\"\n"
        + " orgIdentifier: \"orgId\"\n"
        + " projectIdentifier: \"projId\"\n";
    InfrastructureEntity infraEntity = InfrastructureEntity.builder()
                                           .accountId("accId")
                                           .envIdentifier("envId")
                                           .orgIdentifier("orgId")
                                           .projectIdentifier("projId")
                                           .build();

    NGServiceOverrides serviceOverrides = NGServiceOverrides.builder().serviceRef("ref").build();

    EnvironmentPlanCreatorConfig config = toEnvironmentPlanCreatorConfig(
        envYaml, asList(InfrastructureEntityConfigMapper.toInfrastructureConfig(infraEntity)), serviceOverrides);

    assertThat(config.getEnvironmentRef().getValue()).isEqualTo("envId");
    assertThat(config.getIdentifier()).isEqualTo("envId");
    assertThat(config.getProjectIdentifier()).isEqualTo("projId");
    assertThat(config.getOrgIdentifier()).isEqualTo("orgId");
    assertThat(config.getDescription()).isEqualTo(null);
    assertThat(config.getName()).isEqualTo("env_name");
    assertThat(config.getTags()).hasSize(1);
    assertThat(config.getTags().get("k")).isEqualTo("v");
    assertThat(config.getType()).isEqualTo(EnvironmentType.Production);
    assertThat(config.getInfrastructureDefinitions()).hasSize(1);
    assertThat(config.getServiceOverrides().getServiceRef()).isEqualTo("ref");
  }

  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  public void testToEnvPlanCreatorConfigWithGitops() {
    String envYaml = "environment:\n"
        + " name: \"env_name\"\n"
        + " identifier: \"envId\"\n"
        + " type: \"Production\"\n"
        + " tags:\n"
        + "   k: \"v\"\n"
        + " accountId: \"accId\"\n"
        + " orgIdentifier: \"orgId\"\n"
        + " projectIdentifier: \"projId\"\n";
    EnvironmentYamlV2 envV2 =
        EnvironmentYamlV2.builder()
            .environmentRef(ParameterField.<String>builder().value("envId").build())
            .deployToAll(false)
            .gitOpsClusters(
                asList(ClusterYaml.builder().ref(ParameterField.<String>builder().value("c1").build()).build()))
            .build();
    EnvironmentPlanCreatorConfig config = toEnvPlanCreatorConfigWithGitops(envYaml, envV2, null);

    assertThat(config.getEnvironmentRef().getValue()).isEqualTo("envId");
    assertThat(config.getIdentifier()).isEqualTo("envId");
    assertThat(config.getProjectIdentifier()).isEqualTo("projId");
    assertThat(config.getOrgIdentifier()).isEqualTo("orgId");
    assertThat(config.getDescription()).isEqualTo(null);
    assertThat(config.getName()).isEqualTo("env_name");
    assertThat(config.getTags()).hasSize(1);
    assertThat(config.getTags().get("k")).isEqualTo("v");
    assertThat(config.getType()).isEqualTo(EnvironmentType.Production);
    assertThat(config.getInfrastructureDefinitions()).isNull();
    assertThat(config.getGitOpsClusterRefs()).hasSize(1).containsExactly("c1");
    assertThat(config.isDeployToAll()).isFalse();
  }

  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  public void testToEnvPlanCreatorConfigWithGitopsDeployAll() {
    String envYaml = "environment:\n"
        + " name: \"env_name\"\n"
        + " identifier: \"envId\"\n"
        + " type: \"Production\"\n"
        + " tags:\n"
        + "   k: \"v\"\n"
        + " accountId: \"accId\"\n"
        + " orgIdentifier: \"orgId\"\n"
        + " projectIdentifier: \"projId\"\n";
    EnvironmentYamlV2 envV2 = EnvironmentYamlV2.builder()
                                  .environmentRef(ParameterField.<String>builder().value("envId").build())
                                  .deployToAll(true)
                                  .build();

    NGServiceOverrides serviceOverride = NGServiceOverrides.builder().serviceRef("ref").build();

    EnvironmentPlanCreatorConfig config = toEnvPlanCreatorConfigWithGitops(envYaml, envV2, serviceOverride);

    assertThat(config.getEnvironmentRef().getValue()).isEqualTo("envId");
    assertThat(config.getIdentifier()).isEqualTo("envId");
    assertThat(config.getProjectIdentifier()).isEqualTo("projId");
    assertThat(config.getOrgIdentifier()).isEqualTo("orgId");
    assertThat(config.getDescription()).isEqualTo(null);
    assertThat(config.getName()).isEqualTo("env_name");
    assertThat(config.getTags()).hasSize(1);
    assertThat(config.getTags().get("k")).isEqualTo("v");
    assertThat(config.getType()).isEqualTo(EnvironmentType.Production);
    assertThat(config.getInfrastructureDefinitions()).isNull();
    assertThat(config.getGitOpsClusterRefs()).hasSize(0);
    assertThat(config.isDeployToAll()).isTrue();
    assertThat(config.getServiceOverrides().getServiceRef()).isEqualTo("ref");
  }
}
