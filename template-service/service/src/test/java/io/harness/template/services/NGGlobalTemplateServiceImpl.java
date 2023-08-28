/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.template.services;

import static io.harness.rule.OwnerRule.ARCHIT;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
import io.harness.exception.ngexception.NGTemplateException;
import io.harness.git.model.ChangeType;
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
import io.harness.telemetry.TelemetryReporter;
import io.harness.template.entity.TemplateEntity;
import io.harness.template.entity.TemplateEntity.TemplateEntityKeys;
import io.harness.template.helpers.InputsValidator;
import io.harness.template.helpers.TemplateInputsValidator;
import io.harness.template.helpers.TemplateMergeServiceHelper;
import io.harness.template.helpers.TemplateReferenceHelper;
import io.harness.template.utils.NGTemplateFeatureFlagHelperService;
import io.harness.utils.PmsFeatureFlagService;

import com.google.common.io.Resources;
import com.google.inject.Inject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
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
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.query.Criteria;
import retrofit2.Call;
import retrofit2.Response;

public class NGGlobalTemplateServiceImpl extends TemplateServiceTestBase {
  @Mock TelemetryReporter telemetryReporter;
  @Mock ExecutorService executorService;
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
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void testServiceLayerForProjectScopeTemplates() {
    TemplateEntity createdEntity = templateService.create(entity, false, "", false);
    assertThat(createdEntity).isNotNull();
    assertThat(createdEntity.getAccountId()).isEqualTo(ACCOUNT_ID);
    assertThat(createdEntity.getOrgIdentifier()).isEqualTo(ORG_IDENTIFIER);
    assertThat(createdEntity.getProjectIdentifier()).isEqualTo(PROJ_IDENTIFIER);
    assertThat(createdEntity.getIdentifier()).isEqualTo(TEMPLATE_IDENTIFIER);
    assertThat(createdEntity.getVersion()).isZero();

    Optional<TemplateEntity> optionalTemplateEntity = templateService.get(
        ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, TEMPLATE_IDENTIFIER, TEMPLATE_VERSION_LABEL, false, false);
    assertThat(optionalTemplateEntity).isPresent();
    assertThat(optionalTemplateEntity.get().getAccountId()).isEqualTo(ACCOUNT_ID);
    assertThat(optionalTemplateEntity.get().getOrgIdentifier()).isEqualTo(ORG_IDENTIFIER);
    assertThat(optionalTemplateEntity.get().getProjectIdentifier()).isEqualTo(PROJ_IDENTIFIER);
    assertThat(optionalTemplateEntity.get().getIdentifier()).isEqualTo(TEMPLATE_IDENTIFIER);
    assertThat(optionalTemplateEntity.get().getVersionLabel()).isEqualTo(TEMPLATE_VERSION_LABEL);
    assertThat(optionalTemplateEntity.get().getVersion()).isZero();

    String description = "Updated Description";
    TemplateEntity updateTemplate = entity.withDescription(description);
    TemplateEntity updatedTemplateEntity =
        templateService.updateTemplateEntity(updateTemplate, ChangeType.MODIFY, false, "");
    assertThat(updatedTemplateEntity).isNotNull();
    assertThat(updatedTemplateEntity.getAccountId()).isEqualTo(ACCOUNT_ID);
    assertThat(updatedTemplateEntity.getOrgIdentifier()).isEqualTo(ORG_IDENTIFIER);
    assertThat(updatedTemplateEntity.getProjectIdentifier()).isEqualTo(PROJ_IDENTIFIER);
    assertThat(updatedTemplateEntity.getIdentifier()).isEqualTo(TEMPLATE_IDENTIFIER);
    assertThat(updatedTemplateEntity.getVersionLabel()).isEqualTo(TEMPLATE_VERSION_LABEL);
    assertThat(updatedTemplateEntity.getVersion()).isEqualTo(1L);
    assertThat(updatedTemplateEntity.getDescription()).isEqualTo(description);

    TemplateEntity incorrectTemplate = entity.withVersionLabel("incorrect version");
    assertThatThrownBy(() -> templateService.updateTemplateEntity(incorrectTemplate, ChangeType.MODIFY, false, ""))
        .isInstanceOf(InvalidRequestException.class);

    // Test template list
    Criteria criteria =
        templateServiceHelper.formCriteria(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, null, null, false, "", false);
    Pageable pageRequest = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, TemplateEntityKeys.lastUpdatedAt));
    Page<TemplateEntity> templateEntities =
        templateService.list(criteria, pageRequest, ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, false);
    assertThat(templateEntities.getContent()).isNotNull();
    assertThat(templateEntities.getContent().size()).isEqualTo(1);
    assertThat(templateEntities.getContent().get(0).getIdentifier()).isEqualTo(TEMPLATE_IDENTIFIER);

    // Add 1 more entry to template db
    TemplateEntity version2 = entity.withVersionLabel("version2");
    templateService.create(version2, false, "", false);

    templateEntities = templateService.list(criteria, pageRequest, ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, false);
    assertThat(templateEntities.getContent()).isNotNull();
    assertThat(templateEntities.getContent().size()).isEqualTo(2);
    assertThat(templateEntities.getContent().get(0).getIdentifier()).isEqualTo(TEMPLATE_IDENTIFIER);
    assertThat(templateEntities.getContent().get(0).getVersionLabel()).isEqualTo(TEMPLATE_VERSION_LABEL);
    assertThat(templateEntities.getContent().get(1).getVersionLabel()).isEqualTo("version2");
    // test for lastUpdatedBy
    assertThat(templateEntities.getContent().get(0).isLastUpdatedTemplate()).isFalse();
    assertThat(templateEntities.getContent().get(1).isLastUpdatedTemplate()).isTrue();

    // Template list with search term
    criteria = templateServiceHelper.formCriteria(
        ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, null, null, false, "version2", false);
    templateEntities = templateService.list(criteria, pageRequest, ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, false);
    assertThat(templateEntities.getContent()).isNotNull();
    assertThat(templateEntities.getContent().size()).isEqualTo(1);
    assertThat(templateEntities.getContent().get(0).getIdentifier()).isEqualTo(TEMPLATE_IDENTIFIER);
    assertThat(templateEntities.getContent().get(0).getVersionLabel()).isEqualTo("version2");

    // Update stable template
    TemplateEntity updateStableTemplateVersion = templateService.updateStableTemplateVersion(
        ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, TEMPLATE_IDENTIFIER, "version2", "");
    assertThat(updateStableTemplateVersion).isNotNull();
    assertThat(updateStableTemplateVersion.getAccountId()).isEqualTo(ACCOUNT_ID);
    assertThat(updateStableTemplateVersion.getOrgIdentifier()).isEqualTo(ORG_IDENTIFIER);
    assertThat(updateStableTemplateVersion.getProjectIdentifier()).isEqualTo(PROJ_IDENTIFIER);
    assertThat(updateStableTemplateVersion.getIdentifier()).isEqualTo(TEMPLATE_IDENTIFIER);
    assertThat(updateStableTemplateVersion.getVersionLabel()).isEqualTo("version2");
    assertThat(updateStableTemplateVersion.getVersion()).isEqualTo(1L);
    assertThat(updateStableTemplateVersion.isStableTemplate()).isTrue();

    // Add 1 more entry to template db
    TemplateEntity version3 = entity.withVersionLabel("version3");
    templateService.create(version3, false, "", false);

    // Testing updating stable template to check the lastUpdatedBy flag
    updateStableTemplateVersion = templateService.updateStableTemplateVersion(
        ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, TEMPLATE_IDENTIFIER, "version2", "");
    assertThat(updateStableTemplateVersion.isLastUpdatedTemplate()).isTrue();

    Call<ResponseDTO<Boolean>> request = mock(Call.class);
    try {
      when(request.execute()).thenReturn(Response.success(ResponseDTO.newResponse(false)));
    } catch (IOException ex) {
    }
    when(entitySetupUsageClient.isEntityReferenced(any(), any(), any())).thenReturn(request);

    // delete template stable template
    assertThatThrownBy(()
                           -> templateService.delete(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, TEMPLATE_IDENTIFIER,
                               "version2", 1L, "", false))
        .isInstanceOf(InvalidRequestException.class);

    boolean markEntityInvalid = templateService.markEntityInvalid(
        ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, TEMPLATE_IDENTIFIER, TEMPLATE_VERSION_LABEL, "INVALID_YAML");
    assertThat(markEntityInvalid).isTrue();
    assertThatThrownBy(()
                           -> templateService.getMetadataOrThrowExceptionIfInvalid(ACCOUNT_ID, ORG_IDENTIFIER,
                               PROJ_IDENTIFIER, TEMPLATE_IDENTIFIER, TEMPLATE_VERSION_LABEL, false))
        .isInstanceOf(NGTemplateException.class);

    boolean delete = templateService.delete(
        ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, TEMPLATE_IDENTIFIER, TEMPLATE_VERSION_LABEL, null, "", false);
    assertThat(delete).isTrue();
  }
}
