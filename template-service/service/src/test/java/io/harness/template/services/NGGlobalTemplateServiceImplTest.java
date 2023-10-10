/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.template.services;

import static io.harness.rule.OwnerRule.SHIVAM;

import static org.assertj.core.api.Assertions.assertThat;
import static org.joor.Reflect.on;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.harness.NGCommonEntityConstants;
import io.harness.TemplateServiceConfiguration;
import io.harness.TemplateServiceTestBase;
import io.harness.accesscontrol.clients.AccessControlClient;
import io.harness.account.AccountClient;
import io.harness.category.element.UnitTests;
import io.harness.encryption.Scope;
import io.harness.enforcement.client.services.EnforcementClientService;
import io.harness.engine.GovernanceService;
import io.harness.entitysetupusageclient.remote.EntitySetupUsageClient;
import io.harness.exception.InvalidRequestException;
import io.harness.gitaware.helper.GitAwareEntityHelper;
import io.harness.gitsync.persistance.GitSyncSdkService;
import io.harness.gitx.GitXSettingsHelper;
import io.harness.ng.core.dto.OrganizationResponse;
import io.harness.ng.core.dto.ProjectResponse;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.core.template.TemplateEntityType;
import io.harness.ng.core.template.refresh.NgManagerRefreshRequestDTO;
import io.harness.ng.core.template.refresh.v2.InputsValidationResponse;
import io.harness.ngsettings.SettingIdentifiers;
import io.harness.ngsettings.SettingValueType;
import io.harness.ngsettings.client.remote.NGSettingsClient;
import io.harness.ngsettings.dto.SettingValueResponseDTO;
import io.harness.organization.remote.OrganizationClient;
import io.harness.project.remote.ProjectClient;
import io.harness.reconcile.remote.NgManagerReconcileClient;
import io.harness.repositories.NGTemplateRepository;
import io.harness.rest.RestResponse;
import io.harness.rule.Owner;
import io.harness.springdata.TransactionHelper;
import io.harness.template.entity.TemplateEntity;
import io.harness.template.helpers.InputsValidator;
import io.harness.template.helpers.TemplateInputsValidator;
import io.harness.template.helpers.TemplateMergeServiceHelper;
import io.harness.template.helpers.TemplateReferenceHelper;
import io.harness.template.utils.NGTemplateFeatureFlagHelperService;
import io.harness.utils.PmsFeatureFlagService;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import com.google.common.io.Resources;
import com.google.inject.Inject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import retrofit2.Call;
import retrofit2.Response;

public class NGGlobalTemplateServiceImplTest extends TemplateServiceTestBase {
  @Mock EnforcementClientService enforcementClientService;
  @Spy @InjectMocks private NGTemplateServiceHelper templateServiceHelper;
  @Mock private GitSyncSdkService gitSyncSdkService;
  @Inject private NGTemplateRepository templateRepository;
  @Inject private TransactionHelper transactionHelper;
  @Mock private ProjectClient projectClient;
  @Mock private OrganizationClient organizationClient;
  @Mock private TemplateReferenceHelper templateReferenceHelper;
  @Mock private EntitySetupUsageClient entitySetupUsageClient;
  @Mock GitXSettingsHelper gitXSettingsHelper;
  @Mock TemplateRbacHelper templateRbacHelper;

  @InjectMocks NGTemplateServiceImpl templateService;
  @InjectMocks NGGlobalTemplateServiceImpl ngGlobalTemplateService;

  @Mock private NGTemplateFeatureFlagHelperService ngTemplateFeatureFlagHelperService;

  @Mock private AccountClient accountClient;
  @Mock private NGSettingsClient settingsClient;

  @Mock TemplateAsyncSetupUsageService templateAsyncSetupUsageService;

  @Mock private Call<ResponseDTO<SettingValueResponseDTO>> request;
  @Mock NGTemplateSchemaServiceImpl templateSchemaService;
  @Mock AccessControlClient accessControlClient;
  @Mock TemplateMergeServiceHelper templateMergeServiceHelper;
  @Inject TemplateMergeServiceHelper injectedTemplateMergeServiceHelper;

  @Mock TemplateGitXService templateGitXService;
  @Mock GitAwareEntityHelper gitAwareEntityHelper;
  @Mock NgManagerReconcileClient ngManagerReconcileClient;
  @InjectMocks InputsValidator inputsValidator;
  @InjectMocks TemplateInputsValidator templateInputsValidator;
  @InjectMocks TemplateMergeServiceImpl templateMergeService;
  @Mock private GovernanceService governanceService;
  @Mock private PmsFeatureFlagService pmsFeatureFlagService;
  @Mock TemplateServiceConfiguration templateServiceConfiguration;

  private final String ACCOUNT_ID = RandomStringUtils.randomAlphanumeric(6);
  private final String ORG_IDENTIFIER = "orgId";
  private final String PROJ_IDENTIFIER = "projId";
  private final String TEMPLATE_IDENTIFIER = "template1";
  private final String TEMPLATE_VERSION_LABEL = "version1";
  private final String TEMPLATE_CHILD_TYPE = "ShellScript";

  private String yaml;
  TemplateEntity entity;

  private String readFile(String filename) {
    ClassLoader classLoader = getClass().getClassLoader();
    try {
      return Resources.toString(Objects.requireNonNull(classLoader.getResource(filename)), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new InvalidRequestException("Could not read resource file: " + filename);
    }
  }

  @Before
  public void setUp() throws IOException {
    String filename = "template.yaml";
    yaml = readFile(filename);
    on(inputsValidator).set("templateMergeServiceHelper", injectedTemplateMergeServiceHelper);
    on(templateInputsValidator).set("inputsValidator", inputsValidator);
    on(templateMergeService).set("templateMergeServiceHelper", injectedTemplateMergeServiceHelper);
    on(templateMergeService).set("templateInputsValidator", templateInputsValidator);
    on(templateServiceHelper).set("templateRepository", templateRepository);
    on(templateService).set("templateRepository", templateRepository);
    on(templateService).set("templateRepository", templateRepository);
    on(templateService).set("templateGitXService", templateGitXService);
    on(templateService).set("transactionHelper", transactionHelper);
    on(templateService).set("templateServiceHelper", templateServiceHelper);
    on(templateService).set("enforcementClientService", enforcementClientService);
    on(templateService).set("projectClient", projectClient);
    on(templateService).set("organizationClient", organizationClient);
    on(templateService).set("templateReferenceHelper", templateReferenceHelper);
    on(templateService).set("templateMergeService", templateMergeService);
    doNothing().when(enforcementClientService).checkAvailability(any(), any());
    doNothing().when(gitXSettingsHelper).enforceGitExperienceIfApplicable(any(), any(), any());
    entity = TemplateEntity.builder()
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

    Call<ResponseDTO<Optional<ProjectResponse>>> projectCall = mock(Call.class);
    doNothing().when(templateSchemaService).validateYamlSchemaInternal(entity);
    when(projectClient.getProject(anyString(), anyString(), anyString())).thenReturn(projectCall);
    when(projectCall.execute())
        .thenReturn(Response.success(ResponseDTO.newResponse(Optional.of(ProjectResponse.builder().build()))));

    Call<ResponseDTO<Optional<OrganizationResponse>>> organizationCall = mock(Call.class);
    when(organizationClient.getOrganization(anyString(), anyString())).thenReturn(organizationCall);
    when(organizationCall.execute())
        .thenReturn(Response.success(ResponseDTO.newResponse(Optional.of(OrganizationResponse.builder().build()))));

    Call<RestResponse<Boolean>> ffCall = mock(Call.class);
    when(ffCall.execute()).thenReturn(Response.success(new RestResponse<>(false)));

    // default behaviour of validation
    Call<ResponseDTO<InputsValidationResponse>> ngManagerReconcileCall = mock(Call.class);
    doReturn(ngManagerReconcileCall)
        .when(ngManagerReconcileClient)
        .validateYaml(anyString(), anyString(), anyString(), any(NgManagerRefreshRequestDTO.class));
    doReturn(ngManagerReconcileCall)
        .when(ngManagerReconcileClient)
        .validateYaml(anyString(), anyString(), eq(null), any(NgManagerRefreshRequestDTO.class));
    doReturn(ngManagerReconcileCall)
        .when(ngManagerReconcileClient)
        .validateYaml(anyString(), eq(null), eq(null), any(NgManagerRefreshRequestDTO.class));

    doReturn(Response.success(ResponseDTO.newResponse(InputsValidationResponse.builder().isValid(true).build())))
        .when(ngManagerReconcileCall)
        .execute();
    doReturn(true).when(accessControlClient).hasAccess(any(), any(), any());

    when(settingsClient.getSetting(SettingIdentifiers.ENABLE_FORCE_DELETE, ACCOUNT_ID, null, null)).thenReturn(request);
    SettingValueResponseDTO settingValueResponseDTO =
        SettingValueResponseDTO.builder().value("true").valueType(SettingValueType.BOOLEAN).build();
    when(request.execute()).thenReturn(Response.success(ResponseDTO.newResponse(settingValueResponseDTO)));
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void testImportTemplate() throws JsonProcessingException {
    String templateYaml = "template:\n"
        + "  name: ddd\n"
        + "  identifier: ddd\n"
        + "  versionLabel: ddd\n"
        + "  type: Step\n"
        + "  tags: {}\n"
        + "  spec:\n"
        + "    timeout: 10s\n"
        + "    type: AsgCanaryDeploy\n"
        + "    spec:\n"
        + "      instanceSelection:\n"
        + "        type: Count\n"
        + "        spec:\n"
        + "          count: 1\n";
    ObjectMapper objectMapper = new YAMLMapper();
    String importYaml = ngGlobalTemplateService.importTemplateFromGlobalTemplateMarketPlace(
        ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, templateYaml);
    Map<String, Object> importYamlObject = objectMapper.readValue(importYaml, new TypeReference<>() {});
    Map<String, Object> templateNode = (Map<String, Object>) importYamlObject.get(NGCommonEntityConstants.TEMPLATE);
    assertThat(templateNode.get(NGCommonEntityConstants.ORG_KEY)).isEqualTo(ORG_IDENTIFIER);
    assertThat(templateNode.get(NGCommonEntityConstants.PROJECT_KEY)).isEqualTo(PROJ_IDENTIFIER);

    templateYaml = "template:\n"
        + "  name: ddd\n"
        + "  identifier: ddd\n"
        + "  versionLabel: ddd\n"
        + "  type: Step\n"
        + "  tags: {}\n"
        + "  spec:\n"
        + "    timeout: 10s\n"
        + "    type: AsgCanaryDeploy\n"
        + "    spec:\n"
        + "      instanceSelection:\n"
        + "        type: Count\n"
        + "        spec:\n"
        + "          count: 1\n";
    objectMapper = new YAMLMapper();
    importYaml = ngGlobalTemplateService.importTemplateFromGlobalTemplateMarketPlace(
        ACCOUNT_ID, ORG_IDENTIFIER, null, templateYaml);
    importYamlObject = objectMapper.readValue(importYaml, new TypeReference<>() {});
    templateNode = (Map<String, Object>) importYamlObject.get(NGCommonEntityConstants.TEMPLATE);
    assertThat(templateNode.get(NGCommonEntityConstants.ORG_KEY)).isEqualTo(ORG_IDENTIFIER);
    assertThat(templateNode.get(NGCommonEntityConstants.PROJECT_KEY)).isNull();

    templateYaml = "template:\n"
        + "  name: ddd\n"
        + "  identifier: ddd\n"
        + "  versionLabel: ddd\n"
        + "  type: Step\n"
        + "  tags: {}\n"
        + "  spec:\n"
        + "    timeout: 10s\n"
        + "    type: AsgCanaryDeploy\n"
        + "    spec:\n"
        + "      instanceSelection:\n"
        + "        type: Count\n"
        + "        spec:\n"
        + "          count: 1\n";
    objectMapper = new YAMLMapper();
    importYaml =
        ngGlobalTemplateService.importTemplateFromGlobalTemplateMarketPlace(ACCOUNT_ID, null, null, templateYaml);
    importYamlObject = objectMapper.readValue(importYaml, new TypeReference<>() {});
    templateNode = (Map<String, Object>) importYamlObject.get(NGCommonEntityConstants.TEMPLATE);
    assertThat(templateNode.get(NGCommonEntityConstants.ORG_KEY)).isNull();
    assertThat(templateNode.get(NGCommonEntityConstants.PROJECT_KEY)).isNull();
  }
}
