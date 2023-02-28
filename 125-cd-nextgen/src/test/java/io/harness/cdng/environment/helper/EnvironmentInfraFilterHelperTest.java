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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

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
import io.harness.cdng.gitops.entity.Cluster;
import io.harness.cdng.gitops.service.ClusterService;
import io.harness.cdng.gitops.steps.EnvClusterRefs;
import io.harness.cdng.infra.yaml.InfraStructureDefinitionYaml;
import io.harness.cdng.service.beans.ServiceDefinitionType;
import io.harness.gitops.remote.GitopsResourceClient;
import io.harness.ng.beans.PageResponse;
import io.harness.ng.core.environment.beans.Environment;
import io.harness.ng.core.environment.beans.EnvironmentType;
import io.harness.ng.core.environment.services.EnvironmentService;
import io.harness.ng.core.infrastructure.entity.InfrastructureEntity;
import io.harness.ng.core.infrastructure.services.InfrastructureEntityService;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;
import io.harness.utils.NGFeatureFlagHelperService;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
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
import retrofit2.Call;
import retrofit2.Response;

@OwnedBy(HarnessTeam.GITOPS)
public class EnvironmentInfraFilterHelperTest extends CategoryTest {
  public static final String ACC_ID = "ACC_ID";
  public static final String ORG_ID = "ORG_ID";
  public static final String PROJ_ID = "PROJ_ID";
  @Mock NGFeatureFlagHelperService featureFlagHelperService;
  @Mock EnvironmentService environmentService;
  @Mock InfrastructureEntityService infrastructureEntityService;
  @Mock EnvironmentGroupService environmentGroupService;
  @Mock ClusterService clusterService;
  @Mock GitopsResourceClient gitopsResourceClient;

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

  @Test
  @Owner(developers = VAIBHAV_SI)
  @Category(UnitTests.class)
  public void shouldFilterEnvsAndClustersForEnvFilterWithNoEnvironments() {
    FilterYaml envFilter =
        FilterYaml.builder().entities(new HashSet<>(Arrays.asList(Entity.environments))).type(FilterType.all).build();
    EnvironmentsYaml environmentsYaml =
        EnvironmentsYaml.builder().filters(ParameterField.createValueField(Arrays.asList(envFilter))).build();

    doReturn(true).when(featureFlagHelperService).isEnabled(ACC_ID, FeatureName.CDS_FILTER_INFRA_CLUSTERS_ON_TAGS);
    doReturn(new PageImpl<>(Collections.emptyList())).when(environmentService).list(any(), any());

    Assertions
        .assertThatThrownBy(()
                                -> environmentInfraFilterHelper.filterEnvsAndClusters(
                                    environmentsYaml, Collections.emptyList(), ACC_ID, ORG_ID, PROJ_ID))
        .hasMessageContaining("No environments exists in the project");
  }

  @Test
  @Owner(developers = VAIBHAV_SI)
  @Category(UnitTests.class)
  public void shouldFilterEnvsAndClustersForEnvFilterWithNoFilteredEnvironments() {
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
                                -> environmentInfraFilterHelper.filterEnvsAndClusters(
                                    environmentsYaml, Collections.emptyList(), ACC_ID, ORG_ID, PROJ_ID))
        .hasMessageContaining("No Environments are eligible for deployment due to applied filters for tags - ");
  }

  @Test
  @Owner(developers = VAIBHAV_SI)
  @Category(UnitTests.class)
  public void shouldFilterEnvsAndClustersForEnvFilterWithNoClustersFoundInEnv() {
    FilterYaml envFilter =
        FilterYaml.builder().entities(new HashSet<>(Arrays.asList(Entity.environments))).type(FilterType.all).build();
    Environment env1 = Environment.builder().identifier("env1").build();
    FilterYaml clusterFilter =
        FilterYaml.builder().entities(new HashSet<>(Arrays.asList(Entity.gitOpsClusters))).type(FilterType.all).build();
    EnvironmentsYaml environmentsYaml =
        EnvironmentsYaml.builder()
            .filters(ParameterField.createValueField(Arrays.asList(envFilter, clusterFilter)))
            .build();

    doReturn(true).when(featureFlagHelperService).isEnabled(ACC_ID, FeatureName.CDS_FILTER_INFRA_CLUSTERS_ON_TAGS);
    doReturn(new PageImpl<>(Arrays.asList(env1))).when(environmentService).list(any(), any());
    doReturn(new PageImpl<>(Collections.emptyList()))
        .when(clusterService)
        .listAcrossEnv(0, 1000, ACC_ID, ORG_ID, PROJ_ID, Arrays.asList("env1"));

    Assertions
        .assertThatThrownBy(()
                                -> environmentInfraFilterHelper.filterEnvsAndClusters(
                                    environmentsYaml, Collections.emptyList(), ACC_ID, ORG_ID, PROJ_ID))
        .hasMessageContaining("No clusters found in the filtered Environments");
  }

  @Test
  @Owner(developers = VAIBHAV_SI)
  @Category(UnitTests.class)
  public void shouldFilterEnvsAndClustersForEnvFilterWithNoClusterFilterFound() throws IOException {
    FilterYaml envFilter =
        FilterYaml.builder().entities(new HashSet<>(Arrays.asList(Entity.environments))).type(FilterType.all).build();
    EnvironmentsYaml environmentsYaml =
        EnvironmentsYaml.builder().filters(ParameterField.createValueField(Arrays.asList(envFilter))).build();
    Environment env1 = Environment.builder().identifier("env1").build();
    Cluster c1 = Cluster.builder().clusterRef("c1").build();
    io.harness.gitops.models.Cluster gitopsCluster1 = new io.harness.gitops.models.Cluster("c1", "c1");

    doReturn(true).when(featureFlagHelperService).isEnabled(ACC_ID, FeatureName.CDS_FILTER_INFRA_CLUSTERS_ON_TAGS);
    doReturn(new PageImpl<>(Arrays.asList(env1))).when(environmentService).list(any(), any());
    doReturn(new PageImpl<>(Arrays.asList(c1)))
        .when(clusterService)
        .listAcrossEnv(0, 1000, ACC_ID, ORG_ID, PROJ_ID, Arrays.asList("env1"));
    Call call = mock(Call.class);
    doReturn(call).when(gitopsResourceClient).listClusters(any());
    doReturn(Response.success(PageResponse.builder().content(Arrays.asList(gitopsCluster1)).build()))
        .when(call)
        .execute();

    Assertions
        .assertThatThrownBy(()
                                -> environmentInfraFilterHelper.filterEnvsAndClusters(
                                    environmentsYaml, Collections.emptyList(), ACC_ID, ORG_ID, PROJ_ID))
        .hasMessageContaining("No Clusters found after applying filtering.");
  }

  @Test
  @Owner(developers = VAIBHAV_SI)
  @Category(UnitTests.class)
  public void shouldfilterEnvsAndClustersForEnvFilterWithSuccess() throws IOException {
    FilterYaml envFilter =
        FilterYaml.builder().entities(new HashSet<>(Arrays.asList(Entity.environments))).type(FilterType.all).build();
    FilterYaml clusterFilter =
        FilterYaml.builder().entities(new HashSet<>(Arrays.asList(Entity.gitOpsClusters))).type(FilterType.all).build();
    EnvironmentsYaml environmentsYaml =
        EnvironmentsYaml.builder()
            .filters(ParameterField.createValueField(Arrays.asList(envFilter, clusterFilter)))
            .build();
    Environment env1 = Environment.builder().identifier("env1").type(EnvironmentType.Production).build();
    Cluster c1 = Cluster.builder().clusterRef("c1").envRef("env1").build();
    io.harness.gitops.models.Cluster gitopsCluster1 = new io.harness.gitops.models.Cluster("c1", "c1");

    doReturn(true).when(featureFlagHelperService).isEnabled(ACC_ID, FeatureName.CDS_FILTER_INFRA_CLUSTERS_ON_TAGS);
    doReturn(new PageImpl<>(Arrays.asList(env1))).when(environmentService).list(any(), any());
    doReturn(new PageImpl<>(Arrays.asList(c1)))
        .when(clusterService)
        .listAcrossEnv(0, 1000, ACC_ID, ORG_ID, PROJ_ID, Arrays.asList("env1"));
    Call call = mock(Call.class);
    doReturn(call).when(gitopsResourceClient).listClusters(any());
    doReturn(Response.success(PageResponse.builder().content(Arrays.asList(gitopsCluster1)).build()))
        .when(call)
        .execute();

    List<EnvClusterRefs> envClusterRefs = environmentInfraFilterHelper.filterEnvsAndClusters(
        environmentsYaml, Collections.emptyList(), ACC_ID, ORG_ID, PROJ_ID);

    assertThat(envClusterRefs).hasSize(1);
    assertThat(envClusterRefs.get(0).getEnvRef()).isEqualTo("env1");
    assertThat(envClusterRefs.get(0).getClusterRefs()).hasSize(1);
    assertThat(envClusterRefs.get(0).getClusterRefs()).contains("c1");
  }

  @Test
  @Owner(developers = VAIBHAV_SI)
  @Category(UnitTests.class)
  public void shouldfilterEnvsAndClustersForEnvFilterWithSuccessForMultipleInfra() throws IOException {
    FilterYaml envFilter =
        FilterYaml.builder().entities(new HashSet<>(Arrays.asList(Entity.environments))).type(FilterType.all).build();
    FilterYaml clusterFilter =
        FilterYaml.builder().entities(new HashSet<>(Arrays.asList(Entity.gitOpsClusters))).type(FilterType.all).build();
    EnvironmentsYaml environmentsYaml =
        EnvironmentsYaml.builder()
            .filters(ParameterField.createValueField(Arrays.asList(envFilter, clusterFilter)))
            .build();
    Environment env1 = Environment.builder().identifier("env1").type(EnvironmentType.Production).build();
    Environment env2 = Environment.builder().identifier("env2").type(EnvironmentType.Production).build();
    Cluster c1 = Cluster.builder().clusterRef("c1").envRef("env1").build();
    Cluster c2 = Cluster.builder().clusterRef("c2").envRef("env1").build();
    Cluster c3 = Cluster.builder().clusterRef("c3").envRef("env2").build();
    io.harness.gitops.models.Cluster gitopsCluster1 = new io.harness.gitops.models.Cluster("c1", "c1");
    io.harness.gitops.models.Cluster gitopsCluster2 = new io.harness.gitops.models.Cluster("c2", "c2");
    io.harness.gitops.models.Cluster gitopsCluster3 = new io.harness.gitops.models.Cluster("c3", "c3");

    doReturn(true).when(featureFlagHelperService).isEnabled(ACC_ID, FeatureName.CDS_FILTER_INFRA_CLUSTERS_ON_TAGS);
    doReturn(new PageImpl<>(Arrays.asList(env1, env2))).when(environmentService).list(any(), any());
    doReturn(new PageImpl<>(Arrays.asList(c1, c2, c3)))
        .when(clusterService)
        .listAcrossEnv(anyInt(), anyInt(), any(), any(), any(), any());
    Call call = mock(Call.class);
    doReturn(call).when(gitopsResourceClient).listClusters(any());
    doReturn(Response.success(
                 PageResponse.builder().content(Arrays.asList(gitopsCluster1, gitopsCluster2, gitopsCluster3)).build()))
        .when(call)
        .execute();

    List<EnvClusterRefs> envClusterRefs = environmentInfraFilterHelper.filterEnvsAndClusters(
        environmentsYaml, Collections.emptyList(), ACC_ID, ORG_ID, PROJ_ID);

    assertThat(envClusterRefs).hasSize(2);
    EnvClusterRefs filteredEnv1 =
        envClusterRefs.stream().filter(envClusterRefs1 -> envClusterRefs1.getEnvRef().equals("env1")).findFirst().get();
    EnvClusterRefs filteredEnv2 =
        envClusterRefs.stream().filter(envClusterRefs1 -> envClusterRefs1.getEnvRef().equals("env2")).findFirst().get();
    assertThat(filteredEnv1.getClusterRefs()).hasSize(2);
    assertThat(filteredEnv1.getClusterRefs()).contains("c1", "c2");
    assertThat(filteredEnv2.getEnvRef()).isEqualTo("env2");
    assertThat(filteredEnv2.getClusterRefs()).hasSize(1);
    assertThat(filteredEnv2.getClusterRefs()).contains("c3");
  }

  @Test
  @Owner(developers = VAIBHAV_SI)
  @Category(UnitTests.class)
  public void shouldThrowErrorWhenFFDisabledAndFiltersPresent() {
    FilterYaml envFilter =
        FilterYaml.builder().entities(new HashSet<>(Arrays.asList(Entity.environments))).type(FilterType.all).build();
    EnvironmentsYaml environmentsYaml =
        EnvironmentsYaml.builder().filters(ParameterField.createValueField(Arrays.asList(envFilter))).build();

    doReturn(false).when(featureFlagHelperService).isEnabled(ACC_ID, FeatureName.CDS_FILTER_INFRA_CLUSTERS_ON_TAGS);

    Assertions
        .assertThatThrownBy(()
                                -> environmentInfraFilterHelper.processEnvInfraFiltering(
                                    ACC_ID, ORG_ID, PROJ_ID, environmentsYaml, null, ServiceDefinitionType.KUBERNETES))
        .hasMessageContaining(
            "Pipeline contains filters but Feature Flag: [CDS_FILTER_INFRA_CLUSTERS_ON_TAGS] is disabled. Please enable the FF or remove Filters.");

    EnvironmentGroupYaml envGroupYaml = EnvironmentGroupYaml.builder()
                                            .envGroupRef(ParameterField.createValueField("EG_1"))
                                            .filters(ParameterField.createValueField(Arrays.asList(envFilter)))
                                            .build();

    Assertions
        .assertThatThrownBy(()
                                -> environmentInfraFilterHelper.processEnvInfraFiltering(
                                    ACC_ID, ORG_ID, PROJ_ID, null, envGroupYaml, ServiceDefinitionType.KUBERNETES))
        .hasMessageContaining(
            "Pipeline contains filters but Feature Flag: [CDS_FILTER_INFRA_CLUSTERS_ON_TAGS] is disabled. Please enable the FF or remove Filters.");
  }
}
