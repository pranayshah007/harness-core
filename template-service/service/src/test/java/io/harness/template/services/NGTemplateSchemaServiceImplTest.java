/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.template.services;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.rule.OwnerRule.ABHINAV_MITTAL;
import static io.harness.rule.OwnerRule.UTKARSH_CHOUBEY;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.joor.Reflect.on;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.TemplateServiceTestBase;
import io.harness.account.AccountClient;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.category.element.UnitTests;
import io.harness.encryption.Scope;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.core.template.TemplateEntityType;
import io.harness.pms.yaml.YamlSchemaResponse;
import io.harness.remote.client.NGRestUtils;
import io.harness.rule.Owner;
import io.harness.template.entity.GlobalTemplateEntity;
import io.harness.template.entity.TemplateEntity;
import io.harness.yaml.individualschema.TemplateSchemaParserFactory;
import io.harness.yaml.individualschema.TemplateSchemaParserV0;
import io.harness.yaml.schema.client.YamlSchemaClient;
import io.harness.yaml.validator.InvalidYamlException;
import io.harness.yaml.validator.YamlSchemaValidator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.io.Resources;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import retrofit2.Call;

@OwnedBy(CDC)
public class NGTemplateSchemaServiceImplTest extends TemplateServiceTestBase {
  @Spy @InjectMocks NGTemplateSchemaServiceImpl ngTemplateSchemaService;

  @Mock Map<String, YamlSchemaClient> yamlSchemaClientMapper;
  @Mock AccountClient accountClient;

  @Mock YamlSchemaValidator yamlSchemaValidator;

  @Mock TemplateSchemaParserV0 templateSchemaParserV0;
  @Mock TemplateSchemaParserFactory templateSchemaParserFactory;
  private final String ACCOUNT_ID = RandomStringUtils.randomAlphanumeric(6);
  private final String ORG_IDENTIFIER = "orgId";
  private final String PROJ_IDENTIFIER = "projId";
  private final String TEMPLATE_IDENTIFIER = "template1";
  private final String TEMPLATE_VERSION_LABEL = "version1";
  private final String TEMPLATE_CHILD_TYPE = "ShellScript";

  private String yaml;

  TemplateEntity pipelineTemplateEntity;
  TemplateEntity stepTemplateEntity;

  private String readFile(String filename) {
    ClassLoader classLoader = getClass().getClassLoader();
    try {
      return Resources.toString(Objects.requireNonNull(classLoader.getResource(filename)), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new InvalidRequestException("Could not read resource file: " + filename);
    }
  }

  private JsonNode readJsonFile(String filename) {
    ClassLoader classLoader = getClass().getClassLoader();
    ObjectMapper objectMapper = new ObjectMapper();
    try {
      return objectMapper.readTree(
          Resources.toString(Objects.requireNonNull(classLoader.getResource(filename)), StandardCharsets.UTF_8));
    } catch (IOException e) {
      throw new InvalidRequestException("Could not read resource file: " + filename);
    }
  }

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.openMocks(this);
    String filename = "template.yaml";
    yaml = readFile(filename);
    when(templateSchemaParserFactory.getTemplateSchemaParser("v0")).thenReturn(templateSchemaParserV0);
    when(templateSchemaParserV0.getIndividualSchema(any()))
        .thenReturn((ObjectNode) readJsonFile("template-schema.json"));
    on(ngTemplateSchemaService).set("yamlSchemaValidator", yamlSchemaValidator);
    pipelineTemplateEntity = TemplateEntity.builder()
                                 .accountId(ACCOUNT_ID)
                                 .orgIdentifier(ORG_IDENTIFIER)
                                 .projectIdentifier(PROJ_IDENTIFIER)
                                 .identifier(TEMPLATE_IDENTIFIER)
                                 .name(TEMPLATE_IDENTIFIER)
                                 .versionLabel(TEMPLATE_VERSION_LABEL)
                                 .yaml(yaml)
                                 .templateEntityType(TemplateEntityType.PIPELINE_TEMPLATE)
                                 .childType(TEMPLATE_CHILD_TYPE)
                                 .fullyQualifiedIdentifier("account_id/orgId/projId/template1/version1/")
                                 .templateScope(Scope.PROJECT)
                                 .build();

    stepTemplateEntity = TemplateEntity.builder()
                             .accountId(ACCOUNT_ID)
                             .orgIdentifier(ORG_IDENTIFIER)
                             .projectIdentifier(PROJ_IDENTIFIER)
                             .identifier(TEMPLATE_IDENTIFIER)
                             .name(TEMPLATE_IDENTIFIER)
                             .versionLabel(TEMPLATE_VERSION_LABEL)
                             .yaml(yaml)
                             .templateEntityType(TemplateEntityType.STEP_TEMPLATE)
                             .childType(TEMPLATE_CHILD_TYPE)
                             .fullyQualifiedIdentifier("account_id/orgId/projId/template1/version1/")
                             .templateScope(Scope.PROJECT)
                             .build();
  }

  @Test
  @Owner(developers = ABHINAV_MITTAL)
  @Category(UnitTests.class)
  public void testValidateStepSchemaBy() throws Exception {
    doReturn(false)
        .when(ngTemplateSchemaService)
        .isFeatureFlagEnabled(FeatureName.DISABLE_TEMPLATE_SCHEMA_VALIDATION, ACCOUNT_ID, accountClient);
    when(yamlSchemaValidator.validate(anyString(), anyString())).thenReturn(Collections.emptySet());
    Call<ResponseDTO<YamlSchemaResponse>> requestCall = mock(Call.class);
    try (MockedStatic<NGRestUtils> mockStatic = mockStatic(NGRestUtils.class)) {
      YamlSchemaResponse yamlSchemaResponse =
          YamlSchemaResponse.builder().schema(null).schemaErrorResponse(null).build();
      mockStatic.when(() -> NGRestUtils.getResponse(requestCall)).thenReturn(yamlSchemaResponse);
      ngTemplateSchemaService.validateYamlSchemaInternal(stepTemplateEntity);
      verify(yamlSchemaValidator, times(1)).validate(anyString(), anyString());
    }
  }

  @Test
  @Owner(developers = ABHINAV_MITTAL)
  @Category(UnitTests.class)
  public void testValidatePipelineSchema() throws IOException {
    doReturn(false)
        .when(ngTemplateSchemaService)
        .isFeatureFlagEnabled(FeatureName.DISABLE_TEMPLATE_SCHEMA_VALIDATION, ACCOUNT_ID, accountClient);
    when(yamlSchemaValidator.validate(anyString(), anyString())).thenReturn(Collections.emptySet());
    Call<ResponseDTO<YamlSchemaResponse>> requestCall = mock(Call.class);
    try (MockedStatic<NGRestUtils> mockStatic = mockStatic(NGRestUtils.class)) {
      YamlSchemaResponse yamlSchemaResponse =
          YamlSchemaResponse.builder().schema(null).schemaErrorResponse(null).build();
      mockStatic.when(() -> NGRestUtils.getResponse(requestCall)).thenReturn(yamlSchemaResponse);
      ngTemplateSchemaService.validateYamlSchemaInternal(stepTemplateEntity);
      verify(yamlSchemaValidator, times(1)).validate(anyString(), anyString());
    }
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testValidPipelineTemplateSchema() throws IOException {
    doReturn(false)
        .when(ngTemplateSchemaService)
        .isFeatureFlagEnabled(FeatureName.DISABLE_TEMPLATE_SCHEMA_VALIDATION, ACCOUNT_ID, accountClient);
    when(yamlSchemaValidator.validate(anyString(), anyString())).thenReturn(Collections.emptySet());
    Call<ResponseDTO<YamlSchemaResponse>> requestCall = mock(Call.class);
    try (MockedStatic<NGRestUtils> mockStatic = mockStatic(NGRestUtils.class)) {
      YamlSchemaResponse yamlSchemaResponse =
          YamlSchemaResponse.builder().schema(null).schemaErrorResponse(null).build();
      mockStatic.when(() -> NGRestUtils.getResponse(requestCall)).thenReturn(yamlSchemaResponse);
      ngTemplateSchemaService.validateYamlSchemaInternal(pipelineTemplateEntity);
      verify(yamlSchemaValidator, times(1)).validate(anyString(), anyString());
    }
  }

  @Test
  @Owner(developers = ABHINAV_MITTAL)
  @Category(UnitTests.class)
  public void testInvalidValidatePipelineSchema() throws IOException {
    doReturn(false)
        .when(ngTemplateSchemaService)
        .isFeatureFlagEnabled(FeatureName.DISABLE_TEMPLATE_SCHEMA_VALIDATION, ACCOUNT_ID, accountClient);
    when(yamlSchemaValidator.validate(anyString(), anyString())).thenThrow(new InvalidYamlException("msg", null, null));
    Call<ResponseDTO<YamlSchemaResponse>> requestCall = mock(Call.class);
    try (MockedStatic<NGRestUtils> mockStatic = mockStatic(NGRestUtils.class)) {
      YamlSchemaResponse yamlSchemaResponse =
          YamlSchemaResponse.builder().schema(null).schemaErrorResponse(null).build();
      mockStatic.when(() -> NGRestUtils.getResponse(requestCall)).thenReturn(yamlSchemaResponse);
      assertThatThrownBy(() -> ngTemplateSchemaService.validateYamlSchemaInternal(stepTemplateEntity))
          .isInstanceOf(InvalidYamlException.class);
    }
  }

  @Test(expected = Test.None.class)
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testInvalidValidatePipelineSchemaWithSchemaValidationDisabled() throws IOException {
    doReturn(true)
        .when(ngTemplateSchemaService)
        .isFeatureFlagEnabled(FeatureName.DISABLE_TEMPLATE_SCHEMA_VALIDATION, ACCOUNT_ID, accountClient);
    when(yamlSchemaValidator.validate(anyString(), anyString())).thenThrow(new InvalidYamlException("msg", null, null));
    Call<ResponseDTO<YamlSchemaResponse>> requestCall = mock(Call.class);
    try (MockedStatic<NGRestUtils> mockStatic = mockStatic(NGRestUtils.class)) {
      YamlSchemaResponse yamlSchemaResponse =
          YamlSchemaResponse.builder().schema(null).schemaErrorResponse(null).build();
      mockStatic.when(() -> NGRestUtils.getResponse(requestCall)).thenReturn(yamlSchemaResponse);
      ngTemplateSchemaService.validateYamlSchemaInternal(stepTemplateEntity);
    }
  }

  @Test()
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testUnsupportedTemplateSchemaType() throws IOException {
    doReturn(false)
        .when(ngTemplateSchemaService)
        .isFeatureFlagEnabled(FeatureName.DISABLE_TEMPLATE_SCHEMA_VALIDATION, "acc", accountClient);
    when(yamlSchemaValidator.validate(anyString(), anyString())).thenThrow(new InvalidYamlException("msg", null, null));
    Call<ResponseDTO<YamlSchemaResponse>> requestCall = mock(Call.class);
    try (MockedStatic<NGRestUtils> mockStatic = mockStatic(NGRestUtils.class)) {
      YamlSchemaResponse yamlSchemaResponse =
          YamlSchemaResponse.builder().schema(null).schemaErrorResponse(null).build();
      mockStatic.when(() -> NGRestUtils.getResponse(requestCall)).thenReturn(yamlSchemaResponse);
      TemplateEntity templateEntity = TemplateEntity.builder()
                                          .templateEntityType(TemplateEntityType.SECRET_MANAGER_TEMPLATE)
                                          .accountId("acc")
                                          .build();
      ngTemplateSchemaService.validateYamlSchemaInternal(templateEntity);
    }
  }

  @Test()
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testArtifactSourceTemplateSchemaType() throws IOException {
    doReturn(false)
        .when(ngTemplateSchemaService)
        .isFeatureFlagEnabled(FeatureName.DISABLE_TEMPLATE_SCHEMA_VALIDATION, "acc", accountClient);
    when(yamlSchemaValidator.validate(anyString(), anyString())).thenReturn(Collections.emptySet());
    Call<ResponseDTO<YamlSchemaResponse>> requestCall2 = mock(Call.class);
    YamlSchemaClient mockClient = mock(YamlSchemaClient.class);
    when(yamlSchemaClientMapper.get("cd")).thenReturn(mockClient);
    doReturn(requestCall2).when(mockClient).getEntityYaml(any(), any(), any(), any(), any());
    try (MockedStatic<NGRestUtils> mockStatic = mockStatic(NGRestUtils.class)) {
      ObjectMapper mapper = new ObjectMapper();
      JsonNode jsonNode = mapper.createObjectNode();
      mockStatic.when(() -> NGRestUtils.getResponse(requestCall2)).thenReturn(jsonNode);
      TemplateEntity templateEntity = TemplateEntity.builder()
                                          .templateEntityType(TemplateEntityType.ARTIFACT_SOURCE_TEMPLATE)
                                          .accountId("acc")
                                          .build();
      ngTemplateSchemaService.validateYamlSchemaInternal(templateEntity);
    }
  }

  @Test()
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testCustomDeploymentTemplateSchemaType() throws IOException {
    doReturn(false)
        .when(ngTemplateSchemaService)
        .isFeatureFlagEnabled(FeatureName.DISABLE_TEMPLATE_SCHEMA_VALIDATION, "acc", accountClient);
    when(yamlSchemaValidator.validate(anyString(), anyString())).thenReturn(Collections.emptySet());
    Call<ResponseDTO<YamlSchemaResponse>> requestCall2 = mock(Call.class);
    YamlSchemaClient mockClient = mock(YamlSchemaClient.class);
    when(yamlSchemaClientMapper.get("cd")).thenReturn(mockClient);
    doReturn(requestCall2).when(mockClient).getEntityYaml(any(), any(), any(), any(), any());
    try (MockedStatic<NGRestUtils> mockStatic = mockStatic(NGRestUtils.class)) {
      ObjectMapper mapper = new ObjectMapper();
      JsonNode jsonNode = mapper.createObjectNode();
      mockStatic.when(() -> NGRestUtils.getResponse(requestCall2)).thenReturn(jsonNode);
      TemplateEntity templateEntity = TemplateEntity.builder()
                                          .templateEntityType(TemplateEntityType.CUSTOM_DEPLOYMENT_TEMPLATE)
                                          .accountId("acc")
                                          .build();
      ngTemplateSchemaService.validateYamlSchemaInternal(templateEntity);
    }
  }

  @Test()
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testCustomDeploymentGlobalTemplateSchemaType() throws IOException {
    doReturn(false)
        .when(ngTemplateSchemaService)
        .isFeatureFlagEnabled(FeatureName.DISABLE_TEMPLATE_SCHEMA_VALIDATION, "acc", accountClient);
    when(yamlSchemaValidator.validate(anyString(), anyString())).thenReturn(Collections.emptySet());
    Call<ResponseDTO<YamlSchemaResponse>> requestCall2 = mock(Call.class);
    YamlSchemaClient mockClient = mock(YamlSchemaClient.class);
    when(yamlSchemaClientMapper.get("cd")).thenReturn(mockClient);
    doReturn(requestCall2).when(mockClient).getEntityYaml(any(), any(), any(), any(), any());
    try (MockedStatic<NGRestUtils> mockStatic = mockStatic(NGRestUtils.class)) {
      ObjectMapper mapper = new ObjectMapper();
      JsonNode jsonNode = mapper.createObjectNode();
      mockStatic.when(() -> NGRestUtils.getResponse(requestCall2)).thenReturn(jsonNode);
      GlobalTemplateEntity templateEntity = GlobalTemplateEntity.builder()
                                                .templateEntityType(TemplateEntityType.CUSTOM_DEPLOYMENT_TEMPLATE)
                                                .accountId("acc")
                                                .build();
      ngTemplateSchemaService.validateYamlSchemaInternal(templateEntity);
    }
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testValidateInvalidYamlExceptionIsThrownWhenAnyExceptionIsEncountered() throws Exception {
    doReturn(false)
        .when(ngTemplateSchemaService)
        .isFeatureFlagEnabled(FeatureName.DISABLE_TEMPLATE_SCHEMA_VALIDATION, ACCOUNT_ID, accountClient);
    when(yamlSchemaValidator.validate(anyString(), anyString())).thenThrow(new NullPointerException("msg"));
    TemplateEntity templateEntity = TemplateEntity.builder()
                                        .accountId(ACCOUNT_ID)
                                        .orgIdentifier(ORG_IDENTIFIER)
                                        .projectIdentifier(PROJ_IDENTIFIER)
                                        .identifier(TEMPLATE_IDENTIFIER)
                                        .name(TEMPLATE_IDENTIFIER)
                                        .versionLabel(TEMPLATE_VERSION_LABEL)
                                        .yaml(yaml)
                                        .templateEntityType(TemplateEntityType.STEPGROUP_TEMPLATE)
                                        .childType(TEMPLATE_CHILD_TYPE)
                                        .fullyQualifiedIdentifier("account_id/orgId/projId/template1/version1/")
                                        .templateScope(Scope.PROJECT)
                                        .build();
    Call<ResponseDTO<YamlSchemaResponse>> requestCall = mock(Call.class);
    try (MockedStatic<NGRestUtils> mockStatic = mockStatic(NGRestUtils.class)) {
      YamlSchemaResponse yamlSchemaResponse =
          YamlSchemaResponse.builder().schema(null).schemaErrorResponse(null).build();
      mockStatic.when(() -> NGRestUtils.getResponse(requestCall)).thenReturn(yamlSchemaResponse);
      assertThatThrownBy(() -> ngTemplateSchemaService.validateYamlSchemaInternal(templateEntity))
          .isInstanceOf(InvalidYamlException.class);
    }
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testValidateWithIndividualStaticSchema() throws Exception {
    doReturn(false)
        .when(ngTemplateSchemaService)
        .isFeatureFlagEnabled(FeatureName.DISABLE_TEMPLATE_SCHEMA_VALIDATION, ACCOUNT_ID, accountClient);
    when(yamlSchemaValidator.validate(anyString(), anyString())).thenReturn(Collections.emptySet());
    TemplateEntity templateEntity = TemplateEntity.builder()
                                        .accountId(ACCOUNT_ID)
                                        .orgIdentifier(ORG_IDENTIFIER)
                                        .projectIdentifier(PROJ_IDENTIFIER)
                                        .identifier(TEMPLATE_IDENTIFIER)
                                        .name(TEMPLATE_IDENTIFIER)
                                        .versionLabel(TEMPLATE_VERSION_LABEL)
                                        .yaml(yaml)
                                        .templateEntityType(TemplateEntityType.STEPGROUP_TEMPLATE)
                                        .childType(TEMPLATE_CHILD_TYPE)
                                        .fullyQualifiedIdentifier("account_id/orgId/projId/template1/version1/")
                                        .templateScope(Scope.PROJECT)
                                        .build();

    Call<ResponseDTO<YamlSchemaResponse>> requestCall = mock(Call.class);
    try (MockedStatic<NGRestUtils> mockStatic = mockStatic(NGRestUtils.class)) {
      YamlSchemaResponse yamlSchemaResponse =
          YamlSchemaResponse.builder().schema(null).schemaErrorResponse(null).build();
      mockStatic.when(() -> NGRestUtils.getResponse(requestCall)).thenReturn(yamlSchemaResponse);
      ngTemplateSchemaService.validateYamlSchemaInternal(templateEntity);
    }
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testGetStaticSchemaForAllEntities() {
    ObjectNode schema = ngTemplateSchemaService.getIndividualStaticSchema("stage", "Deployment", "v0");
    assertThat(schema).isNotNull();
  }
}
