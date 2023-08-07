/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.environment.helper;

import static io.harness.rule.OwnerRule.HINGER;
import static io.harness.rule.OwnerRule.VAIBHAV_SI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
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

import java.io.IOException;
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
        FilterYaml.builder().entities(new HashSet<>(List.of(Entity.environments))).type(FilterType.all).build();
    EnvironmentsYaml environmentsYaml =
        EnvironmentsYaml.builder().filters(ParameterField.createValueField(List.of(envFilter))).build();

    doReturn(new PageImpl<>(Collections.emptyList())).when(environmentService).list(any(), any());

    Assertions
        .assertThatThrownBy(()
                                -> environmentInfraFilterHelper.processEnvInfraFiltering(
                                    ACC_ID, ORG_ID, PROJ_ID, environmentsYaml, null, ServiceDefinitionType.KUBERNETES))
        .hasMessageContaining("No environments found at Project/Org/Account Levels");
  }

  @Test
  @Owner(developers = VAIBHAV_SI)
  @Category(UnitTests.class)
  public void shouldNotProcessEnvInfraFilteringForEnvFilterWithNoFilteredEnvironments() {
    FilterYaml envFilter = FilterYaml.builder()
                               .entities(new HashSet<>(List.of(Entity.environments)))
                               .type(FilterType.tags)
                               .spec(TagsFilter.builder()
                                         .matchType(ParameterField.createValueField(MatchType.all.name()))
                                         .tags(ParameterField.createValueField(null))
                                         .build())
                               .build();
    EnvironmentsYaml environmentsYaml =
        EnvironmentsYaml.builder().filters(ParameterField.createValueField(List.of(envFilter))).build();
    Environment env1 = Environment.builder().build();

    doReturn(new PageImpl<>(List.of(env1))).when(environmentService).list(any(), any());

    Assertions
        .assertThatThrownBy(()
                                -> environmentInfraFilterHelper.processEnvInfraFiltering(
                                    ACC_ID, ORG_ID, PROJ_ID, environmentsYaml, null, ServiceDefinitionType.KUBERNETES))
        .hasMessageContaining(
            "Invalid filter tags value found [null]. Filter tags should be non-empty key-value pairs of string values.");
  }

  @Test
  @Owner(developers = VAIBHAV_SI)
  @Category(UnitTests.class)
  public void shouldProcessEnvInfraFilteringForEnvFilterWithNoInfraFilterFound() {
    FilterYaml envFilter =
        FilterYaml.builder().entities(new HashSet<>(List.of(Entity.environments))).type(FilterType.all).build();
    EnvironmentsYaml environmentsYaml =
        EnvironmentsYaml.builder().filters(ParameterField.createValueField(List.of(envFilter))).build();
    Environment env1 = getEnv("env1");

    doReturn(new PageImpl<>(List.of(env1))).when(environmentService).list(any(), any());

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
        FilterYaml.builder().entities(new HashSet<>(List.of(Entity.environments))).type(FilterType.all).build();
    Environment env1 = getEnv("env1");
    FilterYaml infraFilter =
        FilterYaml.builder().entities(new HashSet<>(List.of(Entity.infrastructures))).type(FilterType.all).build();
    EnvironmentsYaml environmentsYaml =
        EnvironmentsYaml.builder().filters(ParameterField.createValueField(List.of(envFilter, infraFilter))).build();

    doReturn(new PageImpl<>(List.of(env1))).when(environmentService).list(any(), any());
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
        FilterYaml.builder().entities(new HashSet<>(List.of(Entity.environments))).type(FilterType.all).build();
    Environment env1 = getEnv("env1");
    FilterYaml infraFilter =
        FilterYaml.builder().entities(new HashSet<>(List.of(Entity.infrastructures))).type(FilterType.all).build();
    InfrastructureEntity infra1 = InfrastructureEntity.builder().identifier("infra1").build();
    EnvironmentsYaml environmentsYaml =
        EnvironmentsYaml.builder().filters(ParameterField.createValueField(List.of(envFilter, infraFilter))).build();

    doReturn(new PageImpl<>(List.of(env1))).when(environmentService).list(any(), any());
    doReturn(List.of(infra1))
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
        FilterYaml.builder().entities(new HashSet<>(List.of(Entity.environments))).type(FilterType.all).build();
    Environment env1 = getEnv("env1");
    Environment env2 = getEnv("env2");
    FilterYaml infraFilter =
        FilterYaml.builder().entities(new HashSet<>(List.of(Entity.infrastructures))).type(FilterType.all).build();
    InfrastructureEntity infra1 = InfrastructureEntity.builder().identifier("infra1").build();
    InfrastructureEntity infra2 = InfrastructureEntity.builder().identifier("infra2").build();
    EnvironmentsYaml environmentsYaml =
        EnvironmentsYaml.builder().filters(ParameterField.createValueField(List.of(envFilter, infraFilter))).build();

    doReturn(new PageImpl<>(List.of(env1, env2))).when(environmentService).list(any(), any());
    doReturn(List.of(infra1, infra2))
        .when(infrastructureEntityService)
        .getAllInfrastructureFromEnvRefAndDeploymentType(
            ACC_ID, ORG_ID, PROJ_ID, "env1", ServiceDefinitionType.KUBERNETES);
    doReturn(List.of(infra1, infra2))
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
    FilterYaml infraFilter =
        FilterYaml.builder().entities(new HashSet<>(List.of(Entity.infrastructures))).type(FilterType.all).build();
    InfrastructureEntity infra1 = InfrastructureEntity.builder().identifier("infra1").build();
    InfrastructureEntity infra2 = InfrastructureEntity.builder().identifier("infra2").build();
    EnvironmentYamlV2 envYaml = EnvironmentYamlV2.builder()
                                    .filters(ParameterField.createValueField(List.of(infraFilter)))
                                    .environmentRef(ParameterField.createValueField("env1"))
                                    .build();
    EnvironmentsYaml environmentsYaml =
        EnvironmentsYaml.builder().values(ParameterField.createValueField(List.of(envYaml))).build();
    Environment env1 = getEnv("env1");

    doReturn(new PageImpl<>(List.of(env1))).when(environmentService).list(any(), any());
    doReturn(List.of(infra1, infra2))
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
    FilterYaml infraFilter =
        FilterYaml.builder().entities(new HashSet<>(List.of(Entity.infrastructures))).type(FilterType.all).build();
    InfrastructureEntity infra1 = InfrastructureEntity.builder().identifier("infra1").build();
    InfrastructureEntity infra2 = InfrastructureEntity.builder().identifier("infra2").build();
    EnvironmentYamlV2 envYaml = EnvironmentYamlV2.builder()
                                    .filters(ParameterField.createValueField(List.of(infraFilter)))
                                    .environmentRef(ParameterField.createValueField("env1"))
                                    .build();
    EnvironmentYamlV2 envYaml2 = EnvironmentYamlV2.builder()
                                     .environmentRef(ParameterField.createValueField("env2"))
                                     .infrastructureDefinitions(ParameterField.createValueField(
                                         List.of(InfraStructureDefinitionYaml.builder()
                                                     .identifier(ParameterField.createValueField("fixedInfra"))
                                                     .build())))
                                     .build();
    EnvironmentsYaml environmentsYaml =
        EnvironmentsYaml.builder().values(ParameterField.createValueField(List.of(envYaml, envYaml2))).build();
    Environment env1 = getEnv("env1");

    doReturn(new PageImpl<>(List.of(env1))).when(environmentService).list(any(), any());
    doReturn(List.of(infra1, infra2))
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
        FilterYaml.builder().entities(new HashSet<>(List.of(Entity.environments))).type(FilterType.all).build();
    Environment env1 = getEnv("env1");
    Environment env2 = getEnv("env2");
    FilterYaml infraFilter =
        FilterYaml.builder().entities(new HashSet<>(List.of(Entity.infrastructures))).type(FilterType.all).build();
    InfrastructureEntity infra1 = InfrastructureEntity.builder().identifier("infra1").build();
    InfrastructureEntity infra2 = InfrastructureEntity.builder().identifier("infra2").build();
    EnvironmentGroupYaml envGroupYaml = EnvironmentGroupYaml.builder()
                                            .envGroupRef(ParameterField.createValueField("EG_1"))
                                            .filters(ParameterField.createValueField(List.of(envFilter, infraFilter)))
                                            .build();
    EnvironmentGroupEntity envGroupEntity = getEnvGroup();

    doReturn(Optional.of(envGroupEntity)).when(environmentGroupService).get(ACC_ID, ORG_ID, PROJ_ID, "EG_1", false);
    doReturn(List.of(env1, env2))
        .when(environmentService)
        .fetchesNonDeletedEnvironmentFromListOfIdentifiers(ACC_ID, ORG_ID, PROJ_ID, List.of("env1", "env2"));
    doReturn(List.of(infra1, infra2))
        .when(infrastructureEntityService)
        .getAllInfrastructureFromEnvRefAndDeploymentType(
            ACC_ID, ORG_ID, PROJ_ID, "env1", ServiceDefinitionType.KUBERNETES);
    doReturn(List.of(infra1, infra2))
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

  private EnvironmentGroupEntity getEnvGroup() {
    return EnvironmentGroupEntity.builder()
        .accountId(ACC_ID)
        .orgIdentifier(ORG_ID)
        .projectIdentifier(PROJ_ID)
        .envIdentifiers(List.of("env1", "env2"))
        .build();
  }

  private EnvironmentGroupEntity getAccountLevelEnvGroup(List<String> envIdentifiers) {
    return EnvironmentGroupEntity.builder().accountId(ACC_ID).envIdentifiers(envIdentifiers).build();
  }

  @Test
  @Owner(developers = VAIBHAV_SI)
  @Category(UnitTests.class)
  public void shouldFilterEnvsAndClustersForEnvFilterWithNoEnvironments() {
    FilterYaml envFilter =
        FilterYaml.builder().entities(new HashSet<>(List.of(Entity.environments))).type(FilterType.all).build();
    EnvironmentsYaml environmentsYaml =
        EnvironmentsYaml.builder().filters(ParameterField.createValueField(List.of(envFilter))).build();

    doReturn(new PageImpl<>(Collections.emptyList())).when(environmentService).list(any(), any());

    Assertions
        .assertThatThrownBy(()
                                -> environmentInfraFilterHelper.filterEnvsAndClusters(
                                    environmentsYaml, Collections.emptyList(), ACC_ID, ORG_ID, PROJ_ID))
        .hasMessageContaining("No environments found at Project/Org/Account Levels");
  }

  @Test
  @Owner(developers = VAIBHAV_SI)
  @Category(UnitTests.class)
  public void shouldNotFilterEnvsAndClustersForEnvFilterWithNoFilteredEnvironments() {
    FilterYaml envFilter = FilterYaml.builder()
                               .entities(new HashSet<>(List.of(Entity.environments)))
                               .type(FilterType.tags)
                               .spec(TagsFilter.builder()
                                         .matchType(ParameterField.createValueField(MatchType.all.name()))
                                         .tags(ParameterField.createValueField(null))
                                         .build())
                               .build();
    EnvironmentsYaml environmentsYaml =
        EnvironmentsYaml.builder().filters(ParameterField.createValueField(List.of(envFilter))).build();
    Environment env1 = Environment.builder().build();

    doReturn(new PageImpl<>(List.of(env1))).when(environmentService).list(any(), any());

    Assertions
        .assertThatThrownBy(()
                                -> environmentInfraFilterHelper.filterEnvsAndClusters(
                                    environmentsYaml, Collections.emptyList(), ACC_ID, ORG_ID, PROJ_ID))
        .hasMessageContaining(
            "Invalid filter tags value found [null]. Filter tags should be non-empty key-value pairs of string values.");
  }

  @Test
  @Owner(developers = VAIBHAV_SI)
  @Category(UnitTests.class)
  public void shouldFilterEnvsAndClustersForEnvFilterWithNoClustersFoundInEnv() {
    FilterYaml envFilter =
        FilterYaml.builder().entities(new HashSet<>(List.of(Entity.environments))).type(FilterType.all).build();
    Environment env1 = Environment.builder()
                           .accountId(ACC_ID)
                           .orgIdentifier(ORG_ID)
                           .projectIdentifier(PROJ_ID)
                           .identifier("env1")
                           .build();
    FilterYaml clusterFilter =
        FilterYaml.builder().entities(new HashSet<>(List.of(Entity.gitOpsClusters))).type(FilterType.all).build();
    EnvironmentsYaml environmentsYaml =
        EnvironmentsYaml.builder().filters(ParameterField.createValueField(List.of(envFilter, clusterFilter))).build();

    doReturn(new PageImpl<>(List.of(env1))).when(environmentService).list(any(), any());
    doReturn(Collections.emptyList())
        .when(clusterService)
        .listAcrossEnv(0, 1000, ACC_ID, ORG_ID, PROJ_ID, List.of("env1"));

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
        FilterYaml.builder().entities(new HashSet<>(List.of(Entity.environments))).type(FilterType.all).build();
    EnvironmentsYaml environmentsYaml =
        EnvironmentsYaml.builder().filters(ParameterField.createValueField(List.of(envFilter))).build();
    Environment env1 = getEnv("env1");
    Cluster c1 = getCluster("c1", "env1");
    io.harness.gitops.models.Cluster gitopsCluster1 = new io.harness.gitops.models.Cluster("c1", "c1");

    doReturn(new PageImpl<>(List.of(env1))).when(environmentService).list(any(), any());
    doReturn(List.of(c1)).when(clusterService).listAcrossEnv(0, 1000, ACC_ID, ORG_ID, PROJ_ID, List.of("env1"));
    Call call = mock(Call.class);
    doReturn(call).when(gitopsResourceClient).listClusters(any());
    doReturn(Response.success(PageResponse.builder().content(List.of(gitopsCluster1)).build())).when(call).execute();

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
        FilterYaml.builder().entities(new HashSet<>(List.of(Entity.environments))).type(FilterType.all).build();
    FilterYaml clusterFilter =
        FilterYaml.builder().entities(new HashSet<>(List.of(Entity.gitOpsClusters))).type(FilterType.all).build();
    EnvironmentsYaml environmentsYaml =
        EnvironmentsYaml.builder().filters(ParameterField.createValueField(List.of(envFilter, clusterFilter))).build();
    Environment env1 = getEnv("env1");
    Cluster c1 = getCluster("c1", "env1");
    io.harness.gitops.models.Cluster gitopsCluster1 = new io.harness.gitops.models.Cluster("c1", "c1");

    doReturn(new PageImpl<>(List.of(env1))).when(environmentService).list(any(), any());
    doReturn(List.of(c1)).when(clusterService).listAcrossEnv(0, 1000, ACC_ID, ORG_ID, PROJ_ID, List.of("env1"));
    Call call = mock(Call.class);
    doReturn(call).when(gitopsResourceClient).listClusters(any());
    doReturn(Response.success(PageResponse.builder().content(List.of(gitopsCluster1)).build())).when(call).execute();

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
        FilterYaml.builder().entities(new HashSet<>(List.of(Entity.environments))).type(FilterType.all).build();
    FilterYaml clusterFilter =
        FilterYaml.builder().entities(new HashSet<>(List.of(Entity.gitOpsClusters))).type(FilterType.all).build();
    EnvironmentsYaml environmentsYaml =
        EnvironmentsYaml.builder().filters(ParameterField.createValueField(List.of(envFilter, clusterFilter))).build();
    Environment env1 = getEnv("env1");
    Environment env2 = getEnv("env2");
    Cluster c1 = getCluster("c1", "env1");
    Cluster c2 = getCluster("c2", "env1");
    Cluster c3 = getCluster("c3", "env2");
    io.harness.gitops.models.Cluster gitopsCluster1 = new io.harness.gitops.models.Cluster("c1", "c1");
    io.harness.gitops.models.Cluster gitopsCluster2 = new io.harness.gitops.models.Cluster("c2", "c2");
    io.harness.gitops.models.Cluster gitopsCluster3 = new io.harness.gitops.models.Cluster("c3", "c3");

    doReturn(new PageImpl<>(List.of(env1, env2))).when(environmentService).list(any(), any());
    doReturn(List.of(c1, c2, c3)).when(clusterService).listAcrossEnv(anyInt(), anyInt(), any(), any(), any(), any());
    Call call = mock(Call.class);
    doReturn(call).when(gitopsResourceClient).listClusters(any());
    doReturn(Response.success(
                 PageResponse.builder().content(List.of(gitopsCluster1, gitopsCluster2, gitopsCluster3)).build()))
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

  private Cluster getCluster(String clusterRef, String envRef) {
    return Cluster.builder()
        .accountId(ACC_ID)
        .orgIdentifier(ORG_ID)
        .projectIdentifier(PROJ_ID)
        .clusterRef(clusterRef)
        .envRef(envRef)
        .build();
  }

  private Cluster getAccountCluster(String clusterRef, String envRef) {
    return Cluster.builder().accountId(ACC_ID).clusterRef(clusterRef).envRef(envRef).build();
  }

  private Environment getEnv(String id) {
    return Environment.builder()
        .accountId(ACC_ID)
        .orgIdentifier(ORG_ID)
        .projectIdentifier(PROJ_ID)
        .identifier(id)
        .type(EnvironmentType.Production)
        .build();
  }

  private Environment getAccountEnv(String id) {
    return Environment.builder().accountId(ACC_ID).identifier(id).type(EnvironmentType.Production).build();
  }

  @Test
  @Owner(developers = VAIBHAV_SI)
  @Category(UnitTests.class)
  public void shouldProcessAccountLevelEnvInfraFilteringForEnvFilter() {
    FilterYaml envFilter =
        FilterYaml.builder().entities(new HashSet<>(List.of(Entity.environments))).type(FilterType.all).build();
    Environment env1 = getAccountEnv("env1");
    FilterYaml infraFilter =
        FilterYaml.builder().entities(new HashSet<>(List.of(Entity.infrastructures))).type(FilterType.all).build();
    InfrastructureEntity infra1 = InfrastructureEntity.builder().identifier("infra1").build();
    EnvironmentsYaml environmentsYaml =
        EnvironmentsYaml.builder().filters(ParameterField.createValueField(List.of(envFilter, infraFilter))).build();

    doReturn(new PageImpl<>(List.of(env1))).when(environmentService).list(any(), any());
    doReturn(List.of(infra1))
        .when(infrastructureEntityService)
        .getAllInfrastructureFromEnvRefAndDeploymentType(
            ACC_ID, ORG_ID, PROJ_ID, "account.env1", ServiceDefinitionType.KUBERNETES);

    environmentInfraFilterHelper.processEnvInfraFiltering(
        ACC_ID, ORG_ID, PROJ_ID, environmentsYaml, null, ServiceDefinitionType.KUBERNETES);

    assertThat(environmentsYaml.getValues()).isNotNull();
    assertThat(environmentsYaml.getValues().getValue()).isNotNull();
    assertThat(environmentsYaml.getValues().getValue().size()).isEqualTo(1);
    EnvironmentYamlV2 environmentYamlV2 = environmentsYaml.getValues().getValue().get(0);
    assertThat(environmentYamlV2.getEnvironmentRef().getValue()).isEqualTo("account.env1");
    assertThat(environmentYamlV2.getInfrastructureDefinitions().getValue()).isNotNull();
    assertThat(environmentYamlV2.getInfrastructureDefinitions().getValue().size()).isEqualTo(1);
    assertThat(environmentYamlV2.getInfrastructureDefinitions().getValue().get(0).getIdentifier().getValue())
        .isEqualTo("infra1");
  }

  @Test
  @Owner(developers = VAIBHAV_SI)
  @Category(UnitTests.class)
  public void shouldProcessAccountLevelEnvInfraFilteringForEnvFilterForMultipleInfra() {
    FilterYaml envFilter =
        FilterYaml.builder().entities(new HashSet<>(List.of(Entity.environments))).type(FilterType.all).build();
    Environment env1 = getAccountEnv("env1");
    Environment env2 = getAccountEnv("env2");
    FilterYaml infraFilter =
        FilterYaml.builder().entities(new HashSet<>(List.of(Entity.infrastructures))).type(FilterType.all).build();
    InfrastructureEntity infra1 = InfrastructureEntity.builder().identifier("infra1").build();
    InfrastructureEntity infra2 = InfrastructureEntity.builder().identifier("infra2").build();
    EnvironmentsYaml environmentsYaml =
        EnvironmentsYaml.builder().filters(ParameterField.createValueField(List.of(envFilter, infraFilter))).build();

    doReturn(new PageImpl<>(List.of(env1, env2))).when(environmentService).list(any(), any());
    doReturn(List.of(infra1, infra2))
        .when(infrastructureEntityService)
        .getAllInfrastructureFromEnvRefAndDeploymentType(
            ACC_ID, ORG_ID, PROJ_ID, "account.env1", ServiceDefinitionType.KUBERNETES);
    doReturn(List.of(infra1, infra2))
        .when(infrastructureEntityService)
        .getAllInfrastructureFromEnvRefAndDeploymentType(
            ACC_ID, ORG_ID, PROJ_ID, "account.env2", ServiceDefinitionType.KUBERNETES);

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
  public void shouldProcessAccountLevelEnvInfraFilteringForInfraFilterWithSingleEnv() {
    FilterYaml infraFilter =
        FilterYaml.builder().entities(new HashSet<>(List.of(Entity.infrastructures))).type(FilterType.all).build();
    InfrastructureEntity infra1 = InfrastructureEntity.builder().identifier("infra1").build();
    InfrastructureEntity infra2 = InfrastructureEntity.builder().identifier("infra2").build();
    EnvironmentYamlV2 envYaml = EnvironmentYamlV2.builder()
                                    .filters(ParameterField.createValueField(List.of(infraFilter)))
                                    .environmentRef(ParameterField.createValueField("account.env1"))
                                    .build();
    EnvironmentsYaml environmentsYaml =
        EnvironmentsYaml.builder().values(ParameterField.createValueField(List.of(envYaml))).build();
    Environment env1 = getAccountEnv("env1");

    doReturn(new PageImpl<>(List.of(env1))).when(environmentService).list(any(), any());
    doReturn(List.of(infra1, infra2))
        .when(infrastructureEntityService)
        .getAllInfrastructureFromEnvRefAndDeploymentType(
            ACC_ID, ORG_ID, PROJ_ID, "account.env1", ServiceDefinitionType.KUBERNETES);

    environmentInfraFilterHelper.processEnvInfraFiltering(
        ACC_ID, ORG_ID, PROJ_ID, environmentsYaml, null, ServiceDefinitionType.KUBERNETES);

    EnvironmentYamlV2 environmentYamlV2 = environmentsYaml.getValues().getValue().get(0);
    assertThat(environmentYamlV2.getInfrastructureDefinitions().getValue()).isNotNull();
    assertThat(environmentYamlV2.getInfrastructureDefinitions().getValue().size()).isEqualTo(2);
  }

  @Test
  @Owner(developers = VAIBHAV_SI)
  @Category(UnitTests.class)
  public void shouldProcessEnvInfraFilteringForAccountLevelEnvGroupForEnvFilter() {
    FilterYaml envFilter =
        FilterYaml.builder().entities(new HashSet<>(List.of(Entity.environments))).type(FilterType.all).build();
    Environment env1 = getAccountEnv("env1");
    Environment env2 = getAccountEnv("env2");
    FilterYaml infraFilter =
        FilterYaml.builder().entities(new HashSet<>(List.of(Entity.infrastructures))).type(FilterType.all).build();
    InfrastructureEntity infra1 = InfrastructureEntity.builder().identifier("infra1").build();
    InfrastructureEntity infra2 = InfrastructureEntity.builder().identifier("infra2").build();
    EnvironmentGroupYaml envGroupYaml = EnvironmentGroupYaml.builder()
                                            .envGroupRef(ParameterField.createValueField("account.EG_1"))
                                            .filters(ParameterField.createValueField(List.of(envFilter, infraFilter)))
                                            .build();
    EnvironmentGroupEntity envGroupEntity = getAccountLevelEnvGroup(List.of("env1", "env2"));

    doReturn(Optional.of(envGroupEntity))
        .when(environmentGroupService)
        .get(ACC_ID, ORG_ID, PROJ_ID, "account.EG_1", false);
    doReturn(List.of(env1, env2))
        .when(environmentService)
        .fetchesNonDeletedEnvironmentFromListOfIdentifiers(ACC_ID, null, null, List.of("env1", "env2"));
    doReturn(List.of(infra1, infra2))
        .when(infrastructureEntityService)
        .getAllInfrastructureFromEnvRefAndDeploymentType(
            ACC_ID, ORG_ID, PROJ_ID, "account.env1", ServiceDefinitionType.KUBERNETES);
    doReturn(List.of(infra1, infra2))
        .when(infrastructureEntityService)
        .getAllInfrastructureFromEnvRefAndDeploymentType(
            ACC_ID, ORG_ID, PROJ_ID, "account.env2", ServiceDefinitionType.KUBERNETES);

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
  public void shouldfilterEnvsAndClustersForAccountLevelEnvFilter() throws IOException {
    FilterYaml envFilter =
        FilterYaml.builder().entities(new HashSet<>(List.of(Entity.environments))).type(FilterType.all).build();
    FilterYaml clusterFilter =
        FilterYaml.builder().entities(new HashSet<>(List.of(Entity.gitOpsClusters))).type(FilterType.all).build();
    EnvironmentsYaml environmentsYaml =
        EnvironmentsYaml.builder().filters(ParameterField.createValueField(List.of(envFilter, clusterFilter))).build();
    Environment env1 = getAccountEnv("env1");
    Cluster c1 = getAccountCluster("account.c1", "env1");
    io.harness.gitops.models.Cluster gitopsCluster1 = new io.harness.gitops.models.Cluster("c1", "c1");
    gitopsCluster1.setAccountIdentifier(ACC_ID);

    doReturn(new PageImpl<>(List.of(env1))).when(environmentService).list(any(), any());
    doReturn(List.of(c1)).when(clusterService).listAcrossEnv(0, 1000, ACC_ID, ORG_ID, PROJ_ID, List.of("account.env1"));
    Call call = mock(Call.class);
    doReturn(call).when(gitopsResourceClient).listClusters(any());
    doReturn(Response.success(PageResponse.builder().content(List.of(gitopsCluster1)).build())).when(call).execute();

    List<EnvClusterRefs> envClusterRefs = environmentInfraFilterHelper.filterEnvsAndClusters(
        environmentsYaml, Collections.emptyList(), ACC_ID, ORG_ID, PROJ_ID);

    assertThat(envClusterRefs).hasSize(1);
    assertThat(envClusterRefs.get(0).getEnvRef()).isEqualTo("account.env1");
    assertThat(envClusterRefs.get(0).getClusterRefs()).hasSize(1);
    assertThat(envClusterRefs.get(0).getClusterRefs()).contains("account.c1");
  }

  @Test
  @Owner(developers = HINGER)
  @Category(UnitTests.class)
  public void shouldProcessEnvInfraFilteringForEnvironmentValuesAndFiltersProvided() {
    FilterYaml envFilter =
        FilterYaml.builder().entities(new HashSet<>(List.of(Entity.environments))).type(FilterType.all).build();
    Environment env1 = getEnv("env1");
    Environment env2 = getEnv("env2");
    FilterYaml infraFilter =
        FilterYaml.builder().entities(new HashSet<>(List.of(Entity.infrastructures))).type(FilterType.all).build();
    InfrastructureEntity infra1 = InfrastructureEntity.builder().identifier("infra1").build();
    InfrastructureEntity infra2 = InfrastructureEntity.builder().identifier("infra2").build();

    EnvironmentYamlV2 fixedEnvYamlProvidedInSetup =
        EnvironmentYamlV2.builder().environmentRef(ParameterField.createValueField("env1")).build();

    EnvironmentsYaml environmentsYaml =
        EnvironmentsYaml.builder()
            .values(ParameterField.createValueField(Collections.singletonList(fixedEnvYamlProvidedInSetup)))
            .filters(ParameterField.createValueField(List.of(envFilter, infraFilter)))
            .build();

    doReturn(new PageImpl<>(List.of(env1, env2))).when(environmentService).list(any(), any());
    doReturn(List.of(infra1, infra2))
        .when(infrastructureEntityService)
        .getAllInfrastructureFromEnvRefAndDeploymentType(
            ACC_ID, ORG_ID, PROJ_ID, "env1", ServiceDefinitionType.KUBERNETES);

    environmentInfraFilterHelper.processEnvInfraFiltering(
        ACC_ID, ORG_ID, PROJ_ID, environmentsYaml, null, ServiceDefinitionType.KUBERNETES);

    assertThat(environmentsYaml.getValues()).isNotNull();
    assertThat(environmentsYaml.getValues().getValue()).isNotNull();
    // 1 environment was provided in values but filters were provided for 2
    // happens when envRef is runtime
    assertThat(environmentsYaml.getValues().getValue().size()).isEqualTo(1);

    EnvironmentYamlV2 environmentYamlV2 = environmentsYaml.getValues().getValue().get(0);

    assertThat(environmentYamlV2.getEnvironmentRef().getValue()).isEqualTo("env1");
  }
}
