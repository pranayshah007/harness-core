/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.graphql.datafetcher.execution;

import static io.harness.rule.OwnerRule.AADITI;
import static io.harness.rule.OwnerRule.MILOS;
import static io.harness.rule.OwnerRule.POOJA;

import static software.wings.beans.Variable.VariableBuilder.aVariable;
import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;
import static software.wings.utils.WingsTestConstants.APP_ID;
import static software.wings.utils.WingsTestConstants.ARTIFACT_ID;
import static software.wings.utils.WingsTestConstants.ARTIFACT_SOURCE_NAME;
import static software.wings.utils.WingsTestConstants.ARTIFACT_STREAM_ID;
import static software.wings.utils.WingsTestConstants.BUILD_NO;
import static software.wings.utils.WingsTestConstants.SERVICE_ID;
import static software.wings.utils.WingsTestConstants.SERVICE_NAME;
import static software.wings.utils.WingsTestConstants.SETTING_ID;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import io.harness.category.element.UnitTests;
import io.harness.exception.GeneralException;
import io.harness.exception.InvalidRequestException;
import io.harness.rule.Owner;

import software.wings.beans.EntityType;
import software.wings.beans.Environment;
import software.wings.beans.Service;
import software.wings.beans.Variable;
import software.wings.beans.artifact.NexusArtifactStream;
import software.wings.graphql.datafetcher.AbstractDataFetcherTestBase;
import software.wings.graphql.schema.mutation.execution.input.QLArtifactInputType;
import software.wings.graphql.schema.mutation.execution.input.QLArtifactValueInput;
import software.wings.graphql.schema.mutation.execution.input.QLParameterValueInput;
import software.wings.graphql.schema.mutation.execution.input.QLParameterizedArtifactSourceInput;
import software.wings.graphql.schema.mutation.execution.input.QLServiceInput;
import software.wings.graphql.schema.mutation.execution.input.QLVariableInput;
import software.wings.graphql.schema.mutation.execution.input.QLVariableValue;
import software.wings.graphql.schema.mutation.execution.input.QLVariableValueType;
import software.wings.infra.InfrastructureDefinition;
import software.wings.persistence.artifact.Artifact;
import software.wings.service.ArtifactStreamHelper;
import software.wings.service.impl.artifact.ArtifactCollectionServiceAsyncImpl;
import software.wings.service.intfc.ArtifactService;
import software.wings.service.intfc.ArtifactStreamService;
import software.wings.service.intfc.EnvironmentService;
import software.wings.service.intfc.InfrastructureDefinitionService;
import software.wings.service.intfc.ServiceResourceService;
import software.wings.utils.RepositoryFormat;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

public class ExecutionControllerTest extends AbstractDataFetcherTestBase {
  @Mock ServiceResourceService serviceResourceService;
  @Mock InfrastructureDefinitionService infrastructureDefinitionService;
  @Mock ArtifactStreamService artifactStreamService;
  @Mock ArtifactStreamHelper artifactStreamHelper;
  @Mock ArtifactService artifactService;
  @Mock ArtifactCollectionServiceAsyncImpl artifactCollectionServiceAsync;
  @Mock EnvironmentService environmentService;
  @Inject @InjectMocks private ExecutionController executionController;

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void getArtifactsForParameterizedSource() {
    NexusArtifactStream nexusArtifactStream = NexusArtifactStream.builder()
                                                  .accountId(ACCOUNT_ID)
                                                  .appId(APP_ID)
                                                  .settingId(SETTING_ID)
                                                  .jobname("${repo}")
                                                  .packageName("${package}")
                                                  .autoPopulate(false)
                                                  .serviceId(SERVICE_ID)
                                                  .name("testNexus")
                                                  .uuid(ARTIFACT_STREAM_ID)
                                                  .sourceName(ARTIFACT_SOURCE_NAME)
                                                  .repositoryFormat(RepositoryFormat.npm.name())
                                                  .build();
    nexusArtifactStream.setArtifactStreamParameterized(true);
    List<QLServiceInput> serviceInputs = asList(
        QLServiceInput.builder()
            .name(SERVICE_NAME)
            .artifactValueInput(
                QLArtifactValueInput.builder()
                    .parameterizedArtifactSource(
                        QLParameterizedArtifactSourceInput.builder()
                            .buildNumber(BUILD_NO)
                            .artifactSourceName(ARTIFACT_SOURCE_NAME)
                            .parameterValueInputs(
                                asList(QLParameterValueInput.builder().name("repo").value("npm-internal").build(),
                                    QLParameterValueInput.builder().name("package").value("npm-app1").build()))
                            .build())
                    .valueType(QLArtifactInputType.PARAMETERIZED_ARTIFACT_SOURCE)
                    .build())
            .build());
    List<Artifact> artifacts = new ArrayList<>();
    when(serviceResourceService.get(APP_ID, SERVICE_ID))
        .thenReturn(Service.builder().uuid(SERVICE_ID).accountId(ACCOUNT_ID).appId(APP_ID).name(SERVICE_NAME).build());
    when(artifactStreamService.getArtifactStreamByName(APP_ID, SERVICE_ID, ARTIFACT_SOURCE_NAME))
        .thenReturn(nexusArtifactStream);
    when(artifactService.getArtifactByBuildNumberAndSourceName(any(), anyString(), anyBoolean(), anyString()))
        .thenReturn(null);
    when(artifactCollectionServiceAsync.collectNewArtifacts(anyString(), any(), anyString(), any()))
        .thenReturn(Artifact.Builder.anArtifact().withUuid(ARTIFACT_ID).build());
    executionController.getArtifactsFromServiceInputs(
        serviceInputs, APP_ID, asList(SERVICE_ID), artifacts, new ArrayList<>());
    assertThat(artifacts.size()).isEqualTo(1);
    assertThat(artifacts.get(0)).extracting(Artifact::getUuid).isEqualTo(ARTIFACT_ID);
  }

  @Test
  @Owner(developers = POOJA)
  @Category(UnitTests.class)
  public void validateEnvVariableTest() {
    Variable variable = aVariable().name("Env").entityType(EntityType.ENVIRONMENT).build();
    assertThat(executionController.validateVariableValue("appId", "env_value", variable, "env_value")).isTrue();
  }

  @Test
  @Owner(developers = MILOS)
  @Category(UnitTests.class)
  public void validateInfraVariableWithIdTypeTest() {
    Environment environment = new Environment();
    when(environmentService.get(any(), any())).thenReturn(environment);

    List<QLVariableInput> variableInputs =
        asList(QLVariableInput.builder()
                   .name("infra")
                   .variableValue(QLVariableValue.builder().value("env_id").type(QLVariableValueType.ID).build())
                   .build());
    assertThat(executionController.getEnvId("infra", "appId", variableInputs)).isEqualTo("env_id");
  }

  @Test
  @Owner(developers = MILOS)
  @Category(UnitTests.class)
  public void validateInfraVariableWithNameTypeTest() {
    Environment environment = new Environment();
    environment.setUuid("env_id");
    when(environmentService.getEnvironmentByName(any(), any())).thenReturn(environment);

    List<QLVariableInput> variableInputs =
        asList(QLVariableInput.builder()
                   .name("infra")
                   .variableValue(QLVariableValue.builder().value("env_name").type(QLVariableValueType.NAME).build())
                   .build());
    assertThat(executionController.getEnvId("infra", "appId", variableInputs)).isEqualTo("env_id");
  }

  @Test
  @Owner(developers = MILOS)
  @Category(UnitTests.class)
  public void validateInfraVariableWithWrongTypeTest() {
    List<QLVariableInput> variableInputs = asList(
        QLVariableInput.builder()
            .name("infra")
            .variableValue(QLVariableValue.builder().value("env_name").type(QLVariableValueType.EXPRESSION).build())
            .build());
    assertThatThrownBy(() -> executionController.getEnvId("infra", "appId", variableInputs))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Value Type EXPRESSION Not supported");
  }

  @Test
  @Owner(developers = POOJA)
  @Category(UnitTests.class)
  public void validateEmptyValueThrowsExcpetion() {
    Variable variable = aVariable().name("Env").entityType(EntityType.ENVIRONMENT).build();
    assertThatThrownBy(() -> executionController.validateVariableValue("appId", "", variable, "env_value"))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Please provide a non empty value for Env");
  }

  @Test
  @Owner(developers = POOJA)
  @Category(UnitTests.class)
  public void validateSrvVariableTest() {
    Variable variable = aVariable().name("Srv").entityType(EntityType.SERVICE).build();
    when(serviceResourceService.exist("appId", "srv_value")).thenReturn(false);
    when(serviceResourceService.exist("appId", "srv_value2")).thenReturn(true);
    assertThatThrownBy(() -> executionController.validateVariableValue("appId", "srv_value", variable, "env_value"))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Service [srv_value] doesn't exist in specified application appId");
    assertThat(executionController.validateVariableValue("appId", "srv_value2", variable, "env_value")).isTrue();
  }

  @Test
  @Owner(developers = POOJA)
  @Category(UnitTests.class)
  public void validateInfraVariableTest() {
    Variable variable = aVariable().name("Infra").entityType(EntityType.INFRASTRUCTURE_DEFINITION).build();
    when(infrastructureDefinitionService.get("appId", "infra_value")).thenReturn(null);
    assertThatThrownBy(() -> executionController.validateVariableValue("appId", "infra_value", variable, "env_value"))
        .isInstanceOf(GeneralException.class)
        .hasMessage("Infrastructure Definition  [infra_value] doesn't exist in specified application appId");

    when(infrastructureDefinitionService.get("appId", "infra_value2"))
        .thenReturn(InfrastructureDefinition.builder().envId("env2").build());

    assertThatThrownBy(() -> executionController.validateVariableValue("appId", "infra_value2", variable, "env_value"))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage(
            "Infrastructure Definition  [infra_value2] doesn't exist in specified application and environment ");

    when(infrastructureDefinitionService.get("appId", "infra_value3"))
        .thenReturn(InfrastructureDefinition.builder().envId("env_value").build());
    assertThat(executionController.validateVariableValue("appId", "infra_value3", variable, "env_value")).isTrue();
  }
}
