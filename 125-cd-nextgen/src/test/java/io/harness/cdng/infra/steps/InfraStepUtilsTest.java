/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.infra.steps;

import static io.harness.pms.yaml.ParameterField.createValueField;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.cdng.environment.yaml.EnvironmentYaml;
import io.harness.cdng.infra.InfraStepUtils;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.common.beans.NGTag;
import io.harness.ng.core.environment.beans.Environment;
import io.harness.ng.core.environment.beans.EnvironmentType;
import io.harness.ng.core.environment.services.EnvironmentService;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.plan.execution.SetupAbstractionKeys;
import io.harness.pms.yaml.ParameterField;
import io.harness.repositories.UpsertOptions;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;

import com.google.common.collect.ImmutableMap;
import com.google.common.io.Resources;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class InfraStepUtilsTest extends CategoryTest {
  @Mock EnvironmentService environmentService;
  private static final Ambiance ambiance = Ambiance.newBuilder().putAllSetupAbstractions(setupAbstractions()).build();

  AutoCloseable mocks;
  @Before
  public void before() {
    mocks = MockitoAnnotations.openMocks(this);
    environment = Environment.builder()
                      .accountId(ACCOUNT_ID)
                      .orgIdentifier(ORG_ID)
                      .projectIdentifier(PROJECT_ID)
                      .identifier(IDENTIFIER)
                      .description(ENV_DESCRIPTION)
                      .tag(NGTag.builder().key("a").value("b").build())
                      .name(IDENTIFIER)
                      .build();
  }

  @After
  public void closeMocks() throws Exception {
    mocks.close();
  }
  private static final String IDENTIFIER = "ENV_NAME";
  private static final String ENV_DESCRIPTION = "ENV_DESCRIPTION";
  private static final String ACCOUNT_ID = "ACCOUNT_ID";
  private static final String ORG_ID = "ORG_ID";
  private static final String PROJECT_ID = "PROJECT_ID";
  private static final ParameterField<String> description = createValueField(ENV_DESCRIPTION);
  private static final ParameterField<String> envRef = createValueField(IDENTIFIER);
  private static Environment environment;
  private static EnvironmentYaml environmentYaml = EnvironmentYaml.builder()
                                                       .name("updated_name")
                                                       .type(EnvironmentType.Production)
                                                       .identifier(IDENTIFIER)
                                                       .description(createValueField("updated_description"))
                                                       .tags(Collections.singletonMap("updated_key", "updated_val"))
                                                       .build();

  @Test
  @Owner(developers = OwnerRule.TATHAGAT)
  @Category(UnitTests.class)
  public void testProcessEnvironment_EnvExistWithYaml_InputEnvYaml() {
    String yaml = readFile("environment/envV2-for-pipeline-update-test.yaml");
    String expectedEnvYaml = readFile("environment/expected-envV2-for-pipeline-update-test.yaml");

    environment.setYaml(yaml);
    environment.setType(EnvironmentType.PreProduction);
    environment.setDescription(ENV_DESCRIPTION);
    environment.setTags(Collections.singletonList(NGTag.builder().key("a").value("b").build()));

    doReturn(Optional.of(environment))
        .when(environmentService)
        .get(anyString(), anyString(), anyString(), anyString(), anyBoolean());

    ArgumentCaptor<Environment> environmentCaptor = ArgumentCaptor.forClass(Environment.class);

    InfraStepUtils.processEnvironment(environmentService, ambiance, environmentYaml, null);
    verify(environmentService, times(1)).upsert(environmentCaptor.capture(), any(UpsertOptions.class));
    Environment outputEnvironment = environmentCaptor.getValue();

    assertUpsertedEnvironmentCreatedByInputYaml(expectedEnvYaml, outputEnvironment);
  }

  @Test
  @Owner(developers = OwnerRule.TATHAGAT)
  @Category(UnitTests.class)
  public void testProcessEnvironment_EnvNotExist_InputEnvYaml() {
    String expectedEnvYaml = readFile("environment/expected-env-yaml-created-from-pipeline.yaml");

    doReturn(Optional.empty())
        .when(environmentService)
        .get(anyString(), anyString(), anyString(), anyString(), anyBoolean());

    ArgumentCaptor<Environment> environmentCaptor = ArgumentCaptor.forClass(Environment.class);

    InfraStepUtils.processEnvironment(environmentService, ambiance, environmentYaml, null);
    verify(environmentService, times(1)).upsert(environmentCaptor.capture(), any(UpsertOptions.class));
    Environment outputEnvironment = environmentCaptor.getValue();

    assertUpsertedEnvironmentCreatedByInputYaml(expectedEnvYaml, outputEnvironment);
  }

  @Test
  @Owner(developers = OwnerRule.TATHAGAT)
  @Category(UnitTests.class)
  public void testProcessEnvironment_EnvExistWithoutYaml_InputEnvYaml() {
    String expectedEnvYaml = readFile("environment/expected-env-yaml-created-from-pipeline.yaml");

    environment.setType(EnvironmentType.PreProduction);
    environment.setDescription(ENV_DESCRIPTION);
    environment.setTags(Collections.singletonList(NGTag.builder().key("a").value("b").build()));

    doReturn(Optional.of(environment))
        .when(environmentService)
        .get(anyString(), anyString(), anyString(), anyString(), anyBoolean());
    ArgumentCaptor<Environment> environmentCaptor = ArgumentCaptor.forClass(Environment.class);

    InfraStepUtils.processEnvironment(environmentService, ambiance, environmentYaml, null);
    verify(environmentService, times(1)).upsert(environmentCaptor.capture(), any(UpsertOptions.class));
    Environment outputEnvironment = environmentCaptor.getValue();

    assertUpsertedEnvironmentCreatedByInputYaml(expectedEnvYaml, outputEnvironment);
  }

  @Test
  @Owner(developers = OwnerRule.TATHAGAT)
  @Category(UnitTests.class)
  public void testProcessEnvironment_EnvNotExist_InputEnvRef() {
    doReturn(Optional.empty())
        .when(environmentService)
        .get(anyString(), anyString(), anyString(), anyString(), anyBoolean());
    ArgumentCaptor<Environment> environmentCaptor = ArgumentCaptor.forClass(Environment.class);
    assertThatThrownBy(() -> InfraStepUtils.processEnvironment(environmentService, ambiance, null, envRef))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Env with identifier " + envRef.getValue() + " does not exist");
  }

  @Test
  @Owner(developers = OwnerRule.TATHAGAT)
  @Category(UnitTests.class)
  public void testProcessEnvironment_EnvExistWithoutYaml_InputEnvRef() {
    String expectedEnvYaml = readFile("environment/expected-env-yaml-created-from-pipeline.yaml");

    environment.setName("updated_name");
    environment.setType(EnvironmentType.Production);
    environment.setDescription("updated_description");
    environment.setTags(Collections.singletonList(NGTag.builder().key("updated_key").value("updated_val").build()));

    doReturn(Optional.of(environment))
        .when(environmentService)
        .get(anyString(), anyString(), anyString(), anyString(), anyBoolean());

    ArgumentCaptor<Environment> environmentCaptor = ArgumentCaptor.forClass(Environment.class);

    InfraStepUtils.processEnvironment(environmentService, ambiance, null, envRef);
    verify(environmentService, times(1)).upsert(environmentCaptor.capture(), any(UpsertOptions.class));
    Environment outputEnvironment = environmentCaptor.getValue();

    assertUpsertedEnvironmentCreatedByInputYaml(expectedEnvYaml, outputEnvironment);
  }

  private static Map<String, String> setupAbstractions() {
    return ImmutableMap.<String, String>builder()
        .put(SetupAbstractionKeys.accountId, ACCOUNT_ID)
        .put(SetupAbstractionKeys.orgIdentifier, ORG_ID)
        .put(SetupAbstractionKeys.projectIdentifier, PROJECT_ID)
        .build();
  }

  private String readFile(String filename) {
    ClassLoader classLoader = getClass().getClassLoader();
    try {
      return Resources.toString(Objects.requireNonNull(classLoader.getResource(filename)), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new InvalidRequestException("Could not read resource file: " + filename);
    }
  }

  private void unsetFieldsInEnvironment() {
    environment.setYaml(null);
    environment.setDescription(null);
    environment.setTags(null);
    environment.setType(null);
  }

  private void assertUpsertedEnvironmentCreatedByInputYaml(String expectedEnvYaml, Environment outputEnvironment) {
    assertThat(outputEnvironment).isNotNull();
    assertThat(outputEnvironment.getYaml()).isNotBlank();
    assertThat(outputEnvironment.getYaml()).isEqualTo(expectedEnvYaml);
    assertThat(outputEnvironment.getTags()).isNotEmpty();
    assertThat(outputEnvironment.getTags())
        .containsExactlyInAnyOrder(NGTag.builder().key("updated_key").value("updated_val").build());
    assertThat(outputEnvironment.getDescription()).isEqualTo("updated_description");
    assertThat(outputEnvironment.getName()).isEqualTo("updated_name");
    assertThat(outputEnvironment.getType()).isEqualTo(EnvironmentType.Production);
  }
}
