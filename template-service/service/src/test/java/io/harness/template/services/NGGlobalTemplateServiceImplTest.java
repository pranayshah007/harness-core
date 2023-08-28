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
import io.harness.gitsync.scm.beans.ScmGetBatchFilesResponse;
import io.harness.gitsync.scm.beans.ScmGetFileResponse;
import io.harness.gitsync.scm.beans.ScmGitMetaData;
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
import io.harness.repositories.NGGlobalTemplateRepository;
import io.harness.repositories.NGTemplateRepository;
import io.harness.rest.RestResponse;
import io.harness.rule.Owner;
import io.harness.springdata.TransactionHelper;
import io.harness.telemetry.TelemetryReporter;
import io.harness.template.entity.GlobalTemplateEntity;
import io.harness.template.helpers.InputsValidator;
import io.harness.template.helpers.TemplateInputsValidator;
import io.harness.template.helpers.TemplateMergeServiceHelper;
import io.harness.template.helpers.TemplateReferenceHelper;
import io.harness.template.resources.beans.TemplateWrapperResponseDTO;
import io.harness.template.utils.NGTemplateFeatureFlagHelperService;
import io.harness.utils.PmsFeatureFlagService;

import com.google.common.io.Resources;
import com.google.inject.Inject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.springframework.data.domain.Page;
import retrofit2.Call;
import retrofit2.Response;

public class NGGlobalTemplateServiceImplTest extends TemplateServiceTestBase {
  @InjectMocks NGGlobalTemplateServiceImpl ngGlobalTemplateService;
  @Mock TelemetryReporter telemetryReporter;
  @Mock ExecutorService executorService;
  @Mock EnforcementClientService enforcementClientService;
  @Spy @InjectMocks private NGTemplateServiceHelper templateServiceHelper;
  @Mock private NGGlobalTemplateServiceHelper ngGlobalTemplateServiceHelper;
  @Mock private GitSyncSdkService gitSyncSdkService;
  @Inject private NGTemplateRepository templateRepository;
  @Inject private TransactionHelper transactionHelper;
  @Mock private ProjectClient projectClient;
  @Mock private OrganizationClient organizationClient;
  @Mock private TemplateReferenceHelper templateReferenceHelper;
  @Mock private EntitySetupUsageClient entitySetupUsageClient;
  @Mock GitXSettingsHelper gitXSettingsHelper;
  @Mock TemplateRbacHelper templateRbacHelper;

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

  private final String ACCOUNT_ID = RandomStringUtils.randomAlphanumeric(6);
  private final String ORG_IDENTIFIER = "orgId";
  private final String PROJ_IDENTIFIER = "projId";
  private final String TEMPLATE_IDENTIFIER = "template1";
  private final String TEMPLATE_VERSION_LABEL = "version1";
  private final String TEMPLATE_CHILD_TYPE = "ShellScript";

  private String yaml;
  GlobalTemplateEntity entity;

  @Mock private NGGlobalTemplateRepository ngGlobalTemplateRepository;

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
    on(ngGlobalTemplateService).set("templateGitXService", templateGitXService);
    on(ngGlobalTemplateService).set("transactionHelper", transactionHelper);
    on(ngGlobalTemplateService).set("enforcementClientService", enforcementClientService);
    on(ngGlobalTemplateService).set("projectClient", projectClient);
    on(ngGlobalTemplateService).set("organizationClient", organizationClient);
    on(ngGlobalTemplateService).set("templateReferenceHelper", templateReferenceHelper);
    on(ngGlobalTemplateService).set("templateMergeService", templateMergeService);
    doNothing().when(enforcementClientService).checkAvailability(any(), any());
    doNothing().when(gitXSettingsHelper).enforceGitExperienceIfApplicable(any(), any(), any());
    entity = GlobalTemplateEntity.builder()
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
    doReturn(false)
        .when(ngGlobalTemplateRepository)
        .globalTemplateExistByIdentifierAndVersionLabel(anyString(), anyString());
    doReturn(Page.empty()).when(ngGlobalTemplateServiceHelper).listTemplate(anyString(), any(), any());
    doReturn(entity).when(ngGlobalTemplateRepository).save(any(), anyString());
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void testCreateGlobalTemplate() {
    Map<String, ScmGetFileResponse> batchFilesResponse = new HashMap<>();
    String template = "template:\n"
        + "  name: HTTP Check\n"
        + "  identifier: HTTP_Check\n"
        + "  versionLabel: \"1.0\"\n"
        + "  type: Step\n"
        + "  description: \"This is http template for health check \"\n"
        + "  tags:\n"
        + "    owner: Harness\n"
        + "  spec:\n"
        + "    type: Http\n"
        + "    timeout: 10s\n"
        + "    spec:\n"
        + "      url: <+input>\n"
        + "      method: GET\n"
        + "      headers:\n"
        + "        - key: content-type\n"
        + "          value: application/json\n"
        + "      outputVariables: []\n"
        + "      requestBody: \"\"\n"
        + "      assertion: <+http.ResponseCode>==200";
    String ReadMe = "# Health Check Step Template\n"
        + "\n"
        + "## Introduction\n"
        + "\n"
        + "- Harness has an HTTP Step that can be used to perform Health Checks on Applicaton Endpoints\n"
        + "- HTTP Steps can be created and managed as Step Templates.\n"
        + "- You can take this step template, add it to your account level Template Library, and then link it in your pipeline.\n";
    String WEBHOOK_EVENT = readFile("webhookEvent.json");
    ScmGetFileResponse scmGetBatchFilesResponse =
        ScmGetFileResponse.builder()
            .fileContent(template)
            .gitMetaData(
                ScmGitMetaData.builder().filePath("continuous-delivery/health-check/v1/health-check.yaml").build())
            .build();
    ScmGetFileResponse scmGetBatchFilesReadMeResponse =
        ScmGetFileResponse.builder()
            .fileContent(ReadMe)
            .gitMetaData(ScmGitMetaData.builder().filePath("continuous-delivery/health-check/v1/README.md").build())
            .build();
    batchFilesResponse.put("continuous-delivery/health-check/v1/health-check.yaml", scmGetBatchFilesResponse);
    batchFilesResponse.put("continuous-delivery/health-check/v1/README.md", scmGetBatchFilesReadMeResponse);
    doReturn(ScmGetBatchFilesResponse.builder().batchFilesResponse(batchFilesResponse).build())
        .when(gitAwareEntityHelper)
        .fetchEntitiesFromRemoteIncludingReadMeFile(anyString(), any());
    List<TemplateWrapperResponseDTO> templateWrapperResponseDTOS = ngGlobalTemplateService.createUpdateGlobalTemplate(
        ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, false, "Comments", true, "connectorRef", WEBHOOK_EVENT);
    assertThat(templateWrapperResponseDTOS).isNotNull();
    assertThat(templateWrapperResponseDTOS.get(0).getTemplateResponseDTO().getIdentifier())
        .isEqualTo(TEMPLATE_IDENTIFIER);
    assertThat(templateWrapperResponseDTOS.get(0).getTemplateResponseDTO().getVersionLabel()).isEqualTo("version1");
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void testUpdateGlobalTemplate() {
    Map<String, ScmGetFileResponse> batchFilesResponse = new HashMap<>();
    String template = "template:\n"
        + "  name: HTTP Check\n"
        + "  identifier: HTTP_Check\n"
        + "  versionLabel: \"1.0\"\n"
        + "  type: Step\n"
        + "  description: \"This is http template for health check \"\n"
        + "  tags:\n"
        + "    owner: Harness\n"
        + "  spec:\n"
        + "    type: Http\n"
        + "    timeout: 10s\n"
        + "    spec:\n"
        + "      url: <+input>\n"
        + "      method: GET\n"
        + "      headers:\n"
        + "        - key: content-type\n"
        + "          value: application/json\n"
        + "      outputVariables: []\n"
        + "      requestBody: \"\"\n"
        + "      assertion: <+http.ResponseCode>==200";
    String ReadMe = "# Health Check Step Template\n"
        + "\n"
        + "## Introduction\n"
        + "\n"
        + "- Harness has an HTTP Step that can be used to perform Health Checks on Applicaton Endpoints\n"
        + "- HTTP Steps can be created and managed as Step Templates.\n"
        + "- You can take this step template, add it to your account level Template Library, and then link it in your pipeline.\n";
    String WEBHOOK_EVENT = readFile("webhookUpdateEvent.json");
    doReturn(Optional.of(entity))
        .when(ngGlobalTemplateServiceHelper)
        .getGlobalTemplateWithVersionLabel(
            ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, TEMPLATE_IDENTIFIER, "1.0", false, false, false, false);
    //    doReturn(entity).when(ngGlobalTemplateServiceHelper).makeTemplateUpdateCall(any(GlobalTemplateEntity.class),
    //    any(GlobalTemplateEntity.class), ChangeType.MODIFY, anyString(),
    //            any(), false);
    ScmGetFileResponse scmGetBatchFilesResponse =
        ScmGetFileResponse.builder()
            .fileContent(template)
            .gitMetaData(
                ScmGitMetaData.builder().filePath("continuous-delivery/health-check/v1/health-check.yaml").build())
            .build();
    ScmGetFileResponse scmGetBatchFilesReadMeResponse =
        ScmGetFileResponse.builder()
            .fileContent(ReadMe)
            .gitMetaData(ScmGitMetaData.builder().filePath("continuous-delivery/health-check/v1/README.md").build())
            .build();
    batchFilesResponse.put("continuous-delivery/health-check/v1/health-check.yaml", scmGetBatchFilesResponse);
    batchFilesResponse.put("continuous-delivery/health-check/v1/README.md", scmGetBatchFilesReadMeResponse);
    doReturn(ScmGetBatchFilesResponse.builder().batchFilesResponse(batchFilesResponse).build())
        .when(gitAwareEntityHelper)
        .fetchEntitiesFromRemoteIncludingReadMeFile(anyString(), any());
    List<TemplateWrapperResponseDTO> templateWrapperResponseDTOS = ngGlobalTemplateService.createUpdateGlobalTemplate(
        ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, false, "Comments", true, "connectorRef", WEBHOOK_EVENT);
    assertThat(templateWrapperResponseDTOS).isNotNull();
    assertThat(templateWrapperResponseDTOS.get(0).getTemplateResponseDTO().getIdentifier())
        .isEqualTo(TEMPLATE_IDENTIFIER);
    assertThat(templateWrapperResponseDTOS.get(0).getTemplateResponseDTO().getVersionLabel()).isEqualTo("version1");
  }
}
