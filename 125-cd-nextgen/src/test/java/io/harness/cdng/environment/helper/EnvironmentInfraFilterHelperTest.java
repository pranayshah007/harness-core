/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.environment.helper;

import static io.harness.rule.OwnerRule.VAIBHAV_SI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.category.element.UnitTests;
import io.harness.cdng.envGroup.beans.EnvironmentGroupEntity;
import io.harness.cdng.envGroup.services.EnvironmentGroupService;
import io.harness.cdng.envgroup.yaml.EnvironmentGroupYaml;
import io.harness.cdng.environment.filters.Entity;
import io.harness.cdng.environment.filters.FilterType;
import io.harness.cdng.environment.filters.FilterYaml;
import io.harness.cdng.environment.filters.MatchType;
import io.harness.cdng.environment.filters.TagsFilter;
import io.harness.cdng.environment.yaml.EnvironmentYamlV2;
import io.harness.cdng.environment.yaml.EnvironmentsYaml;
import io.harness.cdng.infra.yaml.InfraStructureDefinitionYaml;
import io.harness.cdng.service.beans.ServiceDefinitionType;
import io.harness.ng.core.environment.beans.Environment;
import io.harness.ng.core.environment.services.EnvironmentService;
import io.harness.ng.core.infrastructure.entity.InfrastructureEntity;
import io.harness.ng.core.infrastructure.services.InfrastructureEntityService;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;
import io.harness.utils.NGFeatureFlagHelperService;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Optional;
import org.assertj.core.api.Assertions;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.springframework.data.domain.PageImpl;

@OwnedBy(HarnessTeam.GITOPS)
public class EnvironmentInfraFilterHelperTest extends CategoryTest {
  public static final String ACC_ID = "ACC_ID";
  public static final String ORG_ID = "ORG_ID";
  public static final String PROJ_ID = "PROJ_ID";
  @Mock NGFeatureFlagHelperService featureFlagHelperService;
  @Mock EnvironmentService environmentService;
  @Mock InfrastructureEntityService infrastructureEntityService;
  @Mock EnvironmentGroupService environmentGroupService;

  @InjectMocks EnvironmentInfraFilterHelper environmentInfraFilterHelper;
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  @Test
  @Owner(developers = VAIBHAV_SI)
  @Category(UnitTests.class)
  public void shouldProcessEnvInfraFilteringForEnvFilterWithNoEnvironments() {
    FilterYaml envFilter =
        FilterYaml.builder().entities(new HashSet<>(Arrays.asList(Entity.environments))).type(FilterType.all).build();
    EnvironmentsYaml environmentsYaml =
        EnvironmentsYaml.builder().filters(ParameterField.createValueField(Arrays.asList(envFilter))).build();

    doReturn(true).when(featureFlagHelperService).isEnabled(ACC_ID, FeatureName.CDS_FILTER_INFRA_CLUSTERS_ON_TAGS);
    doReturn(new PageImpl<>(Collections.emptyList())).when(environmentService).list(any(), any());

    Assertions
        .assertThatThrownBy(()
                                -> environmentInfraFilterHelper.processEnvInfraFiltering(
                                    ACC_ID, ORG_ID, PROJ_ID, environmentsYaml, null, ServiceDefinitionType.KUBERNETES))
        .hasMessageContaining("No environments exists in the project");
  }

  @Test
  @Owner(developers = VAIBHAV_SI)
  @Category(UnitTests.class)
  public void shouldProcessEnvInfraFilteringForEnvFilterWithNoFilteredEnvironments() {
    FilterYaml envFilter = FilterYaml.builder()
                               .entities(new HashSet<>(Arrays.asList(Entity.environments)))
                               .type(FilterType.tags)
                               .spec(TagsFilter.builder()
                                         .matchType(ParameterField.createValueField(MatchType.all.name()))
                                         .tags(ParameterField.createValueField(null))
                                         .build())
                               .build();
    EnvironmentsYaml environmentsYaml =
        EnvironmentsYaml.builder().filters(ParameterField.createValueField(Arrays.asList(envFilter))).build();
    Environment env1 = Environment.builder().build();

    doReturn(true).when(featureFlagHelperService).isEnabled(ACC_ID, FeatureName.CDS_FILTER_INFRA_CLUSTERS_ON_TAGS);
    doReturn(new PageImpl<>(Arrays.asList(env1))).when(environmentService).list(any(), any());

    Assertions
        .assertThatThrownBy(()
                                -> environmentInfraFilterHelper.processEnvInfraFiltering(
                                    ACC_ID, ORG_ID, PROJ_ID, environmentsYaml, null, ServiceDefinitionType.KUBERNETES))
        .hasMessageContaining("No Environments are eligible for deployment due to applied filters for tags - ");
  }

  @Test
  @Owner(developers = VAIBHAV_SI)
  @Category(UnitTests.class)
  public void shouldProcessEnvInfraFilteringForEnvFilterWithNoInfraFilterFound() {
    FilterYaml envFilter =
        FilterYaml.builder().entities(new HashSet<>(Arrays.asList(Entity.environments))).type(FilterType.all).build();
    EnvironmentsYaml environmentsYaml =
        EnvironmentsYaml.builder().filters(ParameterField.createValueField(Arrays.asList(envFilter))).build();
    Environment env1 = Environment.builder().identifier("env1").build();

    doReturn(true).when(featureFlagHelperService).isEnabled(ACC_ID, FeatureName.CDS_FILTER_INFRA_CLUSTERS_ON_TAGS);
    doReturn(new PageImpl<>(Arrays.asList(env1))).when(environmentService).list(any(), any());

    Assertions
        .assertThatThrownBy(()
                                -> environmentInfraFilterHelper.processEnvInfraFiltering(
                                    ACC_ID, ORG_ID, PROJ_ID, environmentsYaml, null, ServiceDefinitionType.KUBERNETES))
        .hasMessageContaining("No Infrastructures found after applying filtering");
  }

  @Test
  @Owner(developers = VAIBHAV_SI)
  @Category(UnitTests.class)
  public void shouldProcessEnvInfraFilteringForEnvFilterWithNoInfrasFoundInEnv() {
    FilterYaml envFilter =
        FilterYaml.builder().entities(new HashSet<>(Arrays.asList(Entity.environments))).type(FilterType.all).build();
    Environment env1 = Environment.builder().identifier("env1").build();
    FilterYaml infraFilter = FilterYaml.builder()
                                 .entities(new HashSet<>(Arrays.asList(Entity.infrastructures)))
                                 .type(FilterType.all)
                                 .build();
    EnvironmentsYaml environmentsYaml =
        EnvironmentsYaml.builder()
            .filters(ParameterField.createValueField(Arrays.asList(envFilter, infraFilter)))
            .build();

    doReturn(true).when(featureFlagHelperService).isEnabled(ACC_ID, FeatureName.CDS_FILTER_INFRA_CLUSTERS_ON_TAGS);
    doReturn(new PageImpl<>(Arrays.asList(env1))).when(environmentService).list(any(), any());
    doReturn(Collections.emptyList())
        .when(infrastructureEntityService)
        .getAllInfrastructureFromEnvRefAndDeploymentType(
            ACC_ID, ORG_ID, PROJ_ID, "env1", ServiceDefinitionType.KUBERNETES);

    Assertions
        .assertThatThrownBy(()
                                -> environmentInfraFilterHelper.processEnvInfraFiltering(
                                    ACC_ID, ORG_ID, PROJ_ID, environmentsYaml, null, ServiceDefinitionType.KUBERNETES))
        .hasMessageContaining("No Infrastructures found after applying filtering");
  }

  @Test
  @Owner(developers = VAIBHAV_SI)
  @Category(UnitTests.class)
  public void shouldProcessEnvInfraFilteringForEnvFilterWithSuccess() {
    FilterYaml envFilter =
        FilterYaml.builder().entities(new HashSet<>(Arrays.asList(Entity.environments))).type(FilterType.all).build();
    Environment env1 = Environment.builder().identifier("env1").build();
    FilterYaml infraFilter = FilterYaml.builder()
                                 .entities(new HashSet<>(Arrays.asList(Entity.infrastructures)))
                                 .type(FilterType.all)
                                 .build();
    InfrastructureEntity infra1 = InfrastructureEntity.builder().identifier("infra1").build();
    EnvironmentsYaml environmentsYaml =
        EnvironmentsYaml.builder()
            .filters(ParameterField.createValueField(Arrays.asList(envFilter, infraFilter)))
            .build();

    doReturn(true).when(featureFlagHelperService).isEnabled(ACC_ID, FeatureName.CDS_FILTER_INFRA_CLUSTERS_ON_TAGS);
    doReturn(new PageImpl<>(Arrays.asList(env1))).when(environmentService).list(any(), any());
    doReturn(Arrays.asList(infra1))
        .when(infrastructureEntityService)
        .getAllInfrastructureFromEnvRefAndDeploymentType(
            ACC_ID, ORG_ID, PROJ_ID, "env1", ServiceDefinitionType.KUBERNETES);

    environmentInfraFilterHelper.processEnvInfraFiltering(
        ACC_ID, ORG_ID, PROJ_ID, environmentsYaml, null, ServiceDefinitionType.KUBERNETES);

    assertThat(environmentsYaml.getValues()).isNotNull();
    assertThat(environmentsYaml.getValues().getValue()).isNotNull();
    assertThat(environmentsYaml.getValues().getValue().size()).isEqualTo(1);
    EnvironmentYamlV2 environmentYamlV2 = environmentsYaml.getValues().getValue().get(0);
    assertThat(environmentYamlV2.getEnvironmentRef().getValue()).isEqualTo("env1");
    assertThat(environmentYamlV2.getInfrastructureDefinitions().getValue()).isNotNull();
    assertThat(environmentYamlV2.getInfrastructureDefinitions().getValue().size()).isEqualTo(1);
    assertThat(environmentYamlV2.getInfrastructureDefinitions().getValue().get(0).getIdentifier().getValue())
        .isEqualTo("infra1");
  }

  @Test
  @Owner(developers = VAIBHAV_SI)
  @Category(UnitTests.class)
  public void shouldProcessEnvInfraFilteringForEnvFilterForMultipleInfra() {
    FilterYaml envFilter =
        FilterYaml.builder().entities(new HashSet<>(Arrays.asList(Entity.environments))).type(FilterType.all).build();
    Environment env1 = Environment.builder().identifier("env1").build();
    Environment env2 = Environment.builder().identifier("env2").build();
    FilterYaml infraFilter = FilterYaml.builder()
                                 .entities(new HashSet<>(Arrays.asList(Entity.infrastructures)))
                                 .type(FilterType.all)
                                 .build();
    InfrastructureEntity infra1 = InfrastructureEntity.builder().identifier("infra1").build();
    InfrastructureEntity infra2 = InfrastructureEntity.builder().identifier("infra2").build();
    EnvironmentsYaml environmentsYaml =
        EnvironmentsYaml.builder()
            .filters(ParameterField.createValueField(Arrays.asList(envFilter, infraFilter)))
            .build();

    doReturn(true).when(featureFlagHelperService).isEnabled(ACC_ID, FeatureName.CDS_FILTER_INFRA_CLUSTERS_ON_TAGS);
    doReturn(new PageImpl<>(Arrays.asList(env1, env2))).when(environmentService).list(any(), any());
    doReturn(Arrays.asList(infra1, infra2))
        .when(infrastructureEntityService)
        .getAllInfrastructureFromEnvRefAndDeploymentType(
            ACC_ID, ORG_ID, PROJ_ID, "env1", ServiceDefinitionType.KUBERNETES);
    doReturn(Arrays.asList(infra1, infra2))
        .when(infrastructureEntityService)
        .getAllInfrastructureFromEnvRefAndDeploymentType(
            ACC_ID, ORG_ID, PROJ_ID, "env2", ServiceDefinitionType.KUBERNETES);

    environmentInfraFilterHelper.processEnvInfraFiltering(
        ACC_ID, ORG_ID, PROJ_ID, environmentsYaml, null, ServiceDefinitionType.KUBERNETES);

    assertThat(environmentsYaml.getValues()).isNotNull();
    assertThat(environmentsYaml.getValues().getValue()).isNotNull();
    assertThat(environmentsYaml.getValues().getValue().size()).isEqualTo(2);
    EnvironmentYamlV2 environmentYamlV2 = environmentsYaml.getValues().getValue().get(0);
    assertThat(environmentYamlV2.getInfrastructureDefinitions().getValue()).isNotNull();
    assertThat(environmentYamlV2.getInfrastructureDefinitions().getValue().size()).isEqualTo(2);
  }

  @Test
  @Owner(developers = VAIBHAV_SI)
  @Category(UnitTests.class)
  public void shouldProcessEnvInfraFilteringForInfraFilterWithSingleEnv() {
    FilterYaml infraFilter = FilterYaml.builder()
                                 .entities(new HashSet<>(Arrays.asList(Entity.infrastructures)))
                                 .type(FilterType.all)
                                 .build();
    InfrastructureEntity infra1 = InfrastructureEntity.builder().identifier("infra1").build();
    InfrastructureEntity infra2 = InfrastructureEntity.builder().identifier("infra2").build();
    EnvironmentYamlV2 envYaml = EnvironmentYamlV2.builder()
                                    .filters(ParameterField.createValueField(Arrays.asList(infraFilter)))
                                    .environmentRef(ParameterField.createValueField("env1"))
                                    .build();
    EnvironmentsYaml environmentsYaml =
        EnvironmentsYaml.builder().values(ParameterField.createValueField(Arrays.asList(envYaml))).build();
    Environment env1 = Environment.builder().identifier("env1").build();

    doReturn(true).when(featureFlagHelperService).isEnabled(ACC_ID, FeatureName.CDS_FILTER_INFRA_CLUSTERS_ON_TAGS);
    doReturn(new PageImpl<>(Arrays.asList(env1))).when(environmentService).list(any(), any());
    doReturn(Arrays.asList(infra1, infra2))
        .when(infrastructureEntityService)
        .getAllInfrastructureFromEnvRefAndDeploymentType(
            ACC_ID, ORG_ID, PROJ_ID, "env1", ServiceDefinitionType.KUBERNETES);

    environmentInfraFilterHelper.processEnvInfraFiltering(
        ACC_ID, ORG_ID, PROJ_ID, environmentsYaml, null, ServiceDefinitionType.KUBERNETES);

    EnvironmentYamlV2 environmentYamlV2 = environmentsYaml.getValues().getValue().get(0);
    assertThat(environmentYamlV2.getInfrastructureDefinitions().getValue()).isNotNull();
    assertThat(environmentYamlV2.getInfrastructureDefinitions().getValue().size()).isEqualTo(2);
  }

  @Test
  @Owner(developers = VAIBHAV_SI)
  @Category(UnitTests.class)
  public void shouldProcessEnvInfraFilteringForInfraFilterWithSingleEnvAndSomeFixedInfras() {
    FilterYaml infraFilter = FilterYaml.builder()
                                 .entities(new HashSet<>(Arrays.asList(Entity.infrastructures)))
                                 .type(FilterType.all)
                                 .build();
    InfrastructureEntity infra1 = InfrastructureEntity.builder().identifier("infra1").build();
    InfrastructureEntity infra2 = InfrastructureEntity.builder().identifier("infra2").build();
    EnvironmentYamlV2 envYaml = EnvironmentYamlV2.builder()
                                    .filters(ParameterField.createValueField(Arrays.asList(infraFilter)))
                                    .environmentRef(ParameterField.createValueField("env1"))
                                    .build();
    EnvironmentYamlV2 envYaml2 = EnvironmentYamlV2.builder()
                                     .environmentRef(ParameterField.createValueField("env2"))
                                     .infrastructureDefinitions(ParameterField.createValueField(
                                         Arrays.asList(InfraStructureDefinitionYaml.builder()
                                                           .identifier(ParameterField.createValueField("fixedInfra"))
                                                           .build())))
                                     .build();
    EnvironmentsYaml environmentsYaml =
        EnvironmentsYaml.builder().values(ParameterField.createValueField(Arrays.asList(envYaml, envYaml2))).build();
    Environment env1 = Environment.builder().identifier("env1").build();

    doReturn(true).when(featureFlagHelperService).isEnabled(ACC_ID, FeatureName.CDS_FILTER_INFRA_CLUSTERS_ON_TAGS);
    doReturn(new PageImpl<>(Arrays.asList(env1))).when(environmentService).list(any(), any());
    doReturn(Arrays.asList(infra1, infra2))
        .when(infrastructureEntityService)
        .getAllInfrastructureFromEnvRefAndDeploymentType(
            ACC_ID, ORG_ID, PROJ_ID, "env1", ServiceDefinitionType.KUBERNETES);

    environmentInfraFilterHelper.processEnvInfraFiltering(
        ACC_ID, ORG_ID, PROJ_ID, environmentsYaml, null, ServiceDefinitionType.KUBERNETES);

    assertThat(environmentsYaml.getValues().getValue().size()).isEqualTo(2);
    EnvironmentYamlV2 filteredEnvYaml1 =
        environmentsYaml.getValues()
            .getValue()
            .stream()
            .filter(environmentYamlV2 -> environmentYamlV2.getEnvironmentRef().getValue().equals("env1"))
            .findFirst()
            .get();
    assertThat(filteredEnvYaml1.getInfrastructureDefinitions().getValue()).isNotNull();
    assertThat(filteredEnvYaml1.getInfrastructureDefinitions().getValue().size()).isEqualTo(2);

    EnvironmentYamlV2 filteredEnvYaml2 =
        environmentsYaml.getValues()
            .getValue()
            .stream()
            .filter(environmentYamlV2 -> environmentYamlV2.getEnvironmentRef().getValue().equals("env2"))
            .findFirst()
            .get();
    assertThat(filteredEnvYaml2.getInfrastructureDefinitions().getValue().size()).isEqualTo(1);
    assertThat(filteredEnvYaml2.getInfrastructureDefinitions().getValue().get(0).getIdentifier().getValue())
        .isEqualTo("fixedInfra");
  }

  @Test
  @Owner(developers = VAIBHAV_SI)
  @Category(UnitTests.class)
  public void shouldProcessEnvInfraFilteringForEnvGroupForEnvFilter() {
    FilterYaml envFilter =
        FilterYaml.builder().entities(new HashSet<>(Arrays.asList(Entity.environments))).type(FilterType.all).build();
    Environment env1 = Environment.builder().identifier("env1").build();
    Environment env2 = Environment.builder().identifier("env2").build();
    FilterYaml infraFilter = FilterYaml.builder()
                                 .entities(new HashSet<>(Arrays.asList(Entity.infrastructures)))
                                 .type(FilterType.all)
                                 .build();
    InfrastructureEntity infra1 = InfrastructureEntity.builder().identifier("infra1").build();
    InfrastructureEntity infra2 = InfrastructureEntity.builder().identifier("infra2").build();
    EnvironmentGroupYaml envGroupYaml =
        EnvironmentGroupYaml.builder()
            .envGroupRef(ParameterField.createValueField("EG_1"))
            .filters(ParameterField.createValueField(Arrays.asList(envFilter, infraFilter)))
            .build();
    EnvironmentGroupEntity envGroupEntity =
        EnvironmentGroupEntity.builder().envIdentifiers(Arrays.asList("env1", "env2")).build();

    doReturn(true).when(featureFlagHelperService).isEnabled(ACC_ID, FeatureName.CDS_FILTER_INFRA_CLUSTERS_ON_TAGS);
    doReturn(Optional.of(envGroupEntity)).when(environmentGroupService).get(ACC_ID, ORG_ID, PROJ_ID, "EG_1", false);
    doReturn(Arrays.asList(env1, env2))
        .when(environmentService)
        .fetchesNonDeletedEnvironmentFromListOfIdentifiers(ACC_ID, ORG_ID, PROJ_ID, Arrays.asList("env1", "env2"));
    doReturn(Arrays.asList(infra1, infra2))
        .when(infrastructureEntityService)
        .getAllInfrastructureFromEnvRefAndDeploymentType(
            ACC_ID, ORG_ID, PROJ_ID, "env1", ServiceDefinitionType.KUBERNETES);
    doReturn(Arrays.asList(infra1, infra2))
        .when(infrastructureEntityService)
        .getAllInfrastructureFromEnvRefAndDeploymentType(
            ACC_ID, ORG_ID, PROJ_ID, "env2", ServiceDefinitionType.KUBERNETES);

    environmentInfraFilterHelper.processEnvInfraFiltering(
        ACC_ID, ORG_ID, PROJ_ID, null, envGroupYaml, ServiceDefinitionType.KUBERNETES);

    assertThat(envGroupYaml.getEnvironments()).isNotNull();
    assertThat(envGroupYaml.getEnvironments().getValue()).isNotNull();
    assertThat(envGroupYaml.getEnvironments().getValue().size()).isEqualTo(2);
    EnvironmentYamlV2 environmentYamlV2 = envGroupYaml.getEnvironments().getValue().get(0);
    assertThat(environmentYamlV2.getInfrastructureDefinitions().getValue()).isNotNull();
    assertThat(environmentYamlV2.getInfrastructureDefinitions().getValue().size()).isEqualTo(2);
  }
}
