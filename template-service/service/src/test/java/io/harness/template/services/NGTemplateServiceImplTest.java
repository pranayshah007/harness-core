/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.template.services;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.eraro.ErrorCode.REVOKED_TOKEN;
import static io.harness.rule.OwnerRule.ADITHYA;
import static io.harness.rule.OwnerRule.ARCHIT;
import static io.harness.rule.OwnerRule.INDER;
import static io.harness.rule.OwnerRule.ROHITKARELIA;
import static io.harness.rule.OwnerRule.SHIVAM;
import static io.harness.rule.OwnerRule.TATHAGAT;
import static io.harness.rule.OwnerRule.UTKARSH_CHOUBEY;
import static io.harness.rule.OwnerRule.VIVEK_DIXIT;
import static io.harness.template.resources.beans.PermissionTypes.TEMPLATE_VIEW_PERMISSION;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.joor.Reflect.on;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.EntityType;
import io.harness.TemplateServiceTestBase;
import io.harness.accesscontrol.acl.api.Resource;
import io.harness.accesscontrol.acl.api.ResourceScope;
import io.harness.accesscontrol.clients.AccessControlClient;
import io.harness.account.AccountClient;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.category.element.UnitTests;
import io.harness.context.GlobalContext;
import io.harness.encryption.Scope;
import io.harness.enforcement.client.services.EnforcementClientService;
import io.harness.engine.GovernanceService;
import io.harness.entitysetupusageclient.remote.EntitySetupUsageClient;
import io.harness.eventsframework.schemas.entity.EntityDetailProtoDTO;
import io.harness.eventsframework.schemas.entity.ScopeProtoEnum;
import io.harness.eventsframework.schemas.entity.TemplateReferenceProtoDTO;
import io.harness.exception.DuplicateFieldException;
import io.harness.exception.EntityNotFoundException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.ReferencedEntityException;
import io.harness.exception.ScmException;
import io.harness.exception.UnexpectedException;
import io.harness.exception.ngexception.NGTemplateException;
import io.harness.exception.ngexception.TemplateAlreadyExistsException;
import io.harness.git.model.ChangeType;
import io.harness.gitaware.helper.GitAwareEntityHelper;
import io.harness.gitaware.helper.TemplateMoveConfigOperationDTO;
import io.harness.gitaware.helper.TemplateMoveConfigOperationType;
import io.harness.gitaware.helper.TemplateMoveConfigRequestDTO;
import io.harness.gitsync.beans.StoreType;
import io.harness.gitsync.interceptor.GitEntityInfo;
import io.harness.gitsync.interceptor.GitSyncBranchContext;
import io.harness.gitsync.persistance.GitSyncSdkService;
import io.harness.gitsync.sdk.EntityGitDetails;
import io.harness.gitx.GitXSettingsHelper;
import io.harness.governance.GovernanceMetadata;
import io.harness.manage.GlobalContextManager;
import io.harness.ng.beans.PageResponse;
import io.harness.ng.core.dto.OrganizationResponse;
import io.harness.ng.core.dto.ProjectResponse;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.core.entitysetupusage.dto.EntitySetupUsageDTO;
import io.harness.ng.core.template.TemplateEntityType;
import io.harness.ng.core.template.TemplateListType;
import io.harness.ng.core.template.TemplateMergeResponseDTO;
import io.harness.ng.core.template.TemplateReferenceSummary;
import io.harness.ng.core.template.TemplateResponseDTO;
import io.harness.ng.core.template.TemplateWithInputsResponseDTO;
import io.harness.ng.core.template.refresh.NgManagerRefreshRequestDTO;
import io.harness.ng.core.template.refresh.v2.InputsValidationResponse;
import io.harness.ngsettings.SettingIdentifiers;
import io.harness.ngsettings.SettingValueType;
import io.harness.ngsettings.client.remote.NGSettingsClient;
import io.harness.ngsettings.dto.SettingValueResponseDTO;
import io.harness.organization.remote.OrganizationClient;
import io.harness.pms.yaml.ParameterField;
import io.harness.project.remote.ProjectClient;
import io.harness.reconcile.remote.NgManagerReconcileClient;
import io.harness.remote.client.CGRestUtils;
import io.harness.remote.client.NGRestUtils;
import io.harness.repositories.NGTemplateRepository;
import io.harness.rest.RestResponse;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;
import io.harness.springdata.TransactionHelper;
import io.harness.template.entity.TemplateEntity;
import io.harness.template.entity.TemplateEntity.TemplateEntityKeys;
import io.harness.template.helpers.InputsValidator;
import io.harness.template.helpers.TemplateInputsValidator;
import io.harness.template.helpers.TemplateMergeServiceHelper;
import io.harness.template.helpers.TemplateReferenceHelper;
import io.harness.template.mappers.NGTemplateDtoMapper;
import io.harness.template.resources.NGTemplateResource;
import io.harness.template.resources.beans.FilterParamsDTO;
import io.harness.template.resources.beans.PageParamsDTO;
import io.harness.template.resources.beans.PermissionTypes;
import io.harness.template.resources.beans.TemplateFilterPropertiesDTO;
import io.harness.template.resources.beans.TemplateImportRequestDTO;
import io.harness.template.resources.beans.TemplateMoveConfigResponse;
import io.harness.template.resources.beans.UpdateGitDetailsParams;
import io.harness.template.resources.beans.yaml.NGTemplateConfig;
import io.harness.template.utils.NGTemplateFeatureFlagHelperService;
import io.harness.utils.PmsFeatureFlagService;
import io.harness.utils.YamlPipelineUtils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.collect.Sets;
import com.google.common.io.Resources;
import com.google.inject.Inject;
import com.google.protobuf.StringValue;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.Spy;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.query.Criteria;
import retrofit2.Call;
import retrofit2.Response;

@OwnedBy(CDC)
public class NGTemplateServiceImplTest extends TemplateServiceTestBase {
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

    doReturn(GovernanceMetadata.newBuilder()
                 .setDeny(false)
                 .setMessage(String.format(
                     "FF: [%s] is disabled for account: [%s]", FeatureName.CDS_OPA_TEMPLATE_GOVERNANCE, ACCOUNT_ID))
                 .build())
        .when(governanceService)
        .evaluateGovernancePoliciesForTemplate(any(), any(), any(), any(), any(), any());

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

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testCheckTemplateAccess() {
    List<TemplateReferenceSummary> templateReferenceSummaryList = new ArrayList<>();
    templateReferenceSummaryList.add(TemplateReferenceSummary.builder()
                                         .templateIdentifier("template1")
                                         .versionLabel("1")
                                         .scope(Scope.PROJECT)
                                         .fqn("pipeline.stages.qaStage.spec.execution.steps.shellScriptStep11")
                                         .stableTemplate(false)
                                         .moduleInfo(new HashSet<>())
                                         .build());
    templateReferenceSummaryList.add(TemplateReferenceSummary.builder()
                                         .templateIdentifier("template1")
                                         .versionLabel("1")
                                         .scope(Scope.ORG)
                                         .fqn("pipeline.stages.qaStage.spec.execution.steps.shellScriptStep12")
                                         .stableTemplate(true)
                                         .moduleInfo(new HashSet<>(List.of("cd")))
                                         .build());
    templateReferenceSummaryList.add(TemplateReferenceSummary.builder()
                                         .templateIdentifier("template2")
                                         .versionLabel("1")
                                         .scope(Scope.ACCOUNT)
                                         .fqn("pipeline.stages.qaStage.spec.execution.steps.approval")
                                         .stableTemplate(false)
                                         .moduleInfo(new HashSet<>(List.of("pms")))
                                         .build());
    TemplateMergeResponseDTO templateMergeResponseDTO = TemplateMergeResponseDTO.builder()
                                                            .mergedPipelineYaml(yaml)
                                                            .templateReferenceSummaries(templateReferenceSummaryList)
                                                            .build();
    templateService.checkLinkedTemplateAccess(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, templateMergeResponseDTO);
    verify(accessControlClient).checkForAccessOrThrow(eq(ResourceScope.of(ACCOUNT_ID, null, null)), any(), any());
    verify(accessControlClient)
        .checkForAccessOrThrow(eq(ResourceScope.of(ACCOUNT_ID, ORG_IDENTIFIER, null)), any(), any());
    verify(accessControlClient)
        .checkForAccessOrThrow(eq(ResourceScope.of(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER)), any(), any());
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testDeleteAlreadyReferencedTemplate() {
    TemplateEntity entity = TemplateEntity.builder()
                                .accountId(ACCOUNT_ID)
                                .orgIdentifier(ORG_IDENTIFIER)
                                .projectIdentifier(PROJ_IDENTIFIER)
                                .identifier(TEMPLATE_IDENTIFIER)
                                .versionLabel(TEMPLATE_VERSION_LABEL)
                                .version((long) 1.0)
                                .build();
    Call<ResponseDTO<Boolean>> request = mock(Call.class);
    try {
      when(request.execute()).thenReturn(Response.success(ResponseDTO.newResponse(true)));
    } catch (IOException ex) {
    }
    when(entitySetupUsageClient.isEntityReferenced(any(), any(), any())).thenReturn(request);
    try {
      templateService.deleteSingleTemplateHelper(
          ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, TEMPLATE_IDENTIFIER, entity, (long) 1.0, true, "", false);
    } catch (ReferencedEntityException e) {
      assertThat(e.getMessage())
          .isEqualTo("Could not delete the template template1 as it is referenced by other entities");
    }
    verify(entitySetupUsageClient, times(1)).isEntityReferenced(anyString(), anyString(), any(EntityType.class));
  }

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void testDeleteTemplateVersionScenarios() {
    TemplateEntity createdEntity = templateService.create(entity, false, "", false);
    assertThat(createdEntity.isStableTemplate()).isTrue();

    TemplateEntity entityVersion2 = templateService.create(entity.withVersionLabel("version2"), true, "", false);
    assertThat(entityVersion2.isStableTemplate()).isTrue();

    TemplateEntity entityVersion3 = templateService.create(entity.withVersionLabel("version3"), false, "", false);
    assertThat(entityVersion3.isStableTemplate()).isFalse();
    assertThat(entityVersion3.isLastUpdatedTemplate()).isTrue();

    TemplateEntity template2EntityVersion2 =
        templateService.create(entity.withVersionLabel("version2").withIdentifier("template2"), false, "", false);
    assertThat(template2EntityVersion2.isStableTemplate()).isTrue();

    TemplateEntity template2EntityVersion3 =
        templateService.create(entity.withVersionLabel("version3").withIdentifier("template2"), true, "", false);
    assertThat(template2EntityVersion3.isStableTemplate()).isTrue();
    assertThat(template2EntityVersion3.isLastUpdatedTemplate()).isTrue();

    Call<ResponseDTO<Boolean>> request = mock(Call.class);
    try {
      when(request.execute()).thenReturn(Response.success(ResponseDTO.newResponse(false)));
    } catch (IOException ex) {
    }
    when(entitySetupUsageClient.isEntityReferenced(any(), any(), any())).thenReturn(request);

    // Deleting a last updated version for a particular templateIdentifier.
    boolean delete = templateService.delete(
        ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, TEMPLATE_IDENTIFIER, "version3", null, "", false);
    assertThat(delete).isTrue();

    Criteria criteria =
        templateServiceHelper.formCriteria(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, null, null, false, "", false);
    criteria.and(TemplateEntityKeys.isLastUpdatedTemplate).is(true);
    Pageable pageRequest = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, TemplateEntityKeys.lastUpdatedAt));
    Page<TemplateEntity> templateEntities =
        templateService.list(criteria, pageRequest, ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, false);
    assertThat(templateEntities.getContent()).isNotNull();
    assertThat(templateEntities.getContent().size()).isEqualTo(2);

    // Deleting a non last update template version
    delete =
        templateService.delete(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, "template2", "version2", null, "", false);
    assertThat(delete).isTrue();
    templateEntities = templateService.list(criteria, pageRequest, ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, false);
    assertThat(templateEntities.getContent()).isNotNull();
    assertThat(templateEntities.getContent().size()).isEqualTo(2);

    // Deleting complete templateIdentifier
    delete = templateService.deleteTemplates(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, TEMPLATE_IDENTIFIER,
        Sets.newHashSet("version1", "version2"), "", false);
    assertThat(delete).isTrue();
    templateEntities = templateService.list(criteria, pageRequest, ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, false);
    assertThat(templateEntities.getContent()).isNotNull();
    assertThat(templateEntities.getContent().size()).isEqualTo(1);

    criteria = templateServiceHelper.formCriteria(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, null,
        TemplateFilterPropertiesDTO.builder()
            .childTypes(Collections.singletonList(TEMPLATE_CHILD_TYPE))
            .templateEntityTypes(Collections.singletonList(TemplateEntityType.STEP_TEMPLATE))
            .build(),
        false, "", false);
    criteria.and(TemplateEntityKeys.isStableTemplate).is(true);
    templateEntities = templateService.list(criteria, pageRequest, ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, false);
    assertThat(templateEntities.getContent()).isNotNull();
    assertThat(templateEntities.getContent().size()).isEqualTo(1);

    TemplateEntity createdEntity3 =
        templateService.create(entity.withVersionLabel("version1").withIdentifier("template3"), false, "", false);
    assertThat(createdEntity3.isStableTemplate()).isTrue();

    TemplateEntity entityVersion3_2 =
        templateService.create(entity.withVersionLabel("version2").withIdentifier("template3"), true, "", false);
    assertThat(entityVersion3_2.isStableTemplate()).isTrue();

    TemplateEntity entityVersion3_3 =
        templateService.create(entity.withVersionLabel("version3").withIdentifier("template3"), false, "", false);
    assertThat(entityVersion3_3.isStableTemplate()).isFalse();
    assertThat(entityVersion3_3.isLastUpdatedTemplate()).isTrue();

    Call<ResponseDTO<Boolean>> request1 = mock(Call.class);
    try {
      when(request1.execute()).thenReturn(Response.success(ResponseDTO.newResponse(true)));
    } catch (IOException ex) {
    }
    when(entitySetupUsageClient.isEntityReferenced(any(), any(), any())).thenReturn(request1);

    // test referenced entity exception is true
    assertThatThrownBy(()
                           -> templateService.delete(
                               ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, "template3", "version3", null, "", false))
        .isInstanceOf(ReferencedEntityException.class);

    MockedStatic<CGRestUtils> mockCGRestUtils = Mockito.mockStatic(CGRestUtils.class);
    mockCGRestUtils.when(() -> CGRestUtils.getResponse(any())).thenReturn(true);

    // test force delete
    delete =
        templateService.delete(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, "template3", "version3", null, "", true);
    assertThat(delete).isTrue();

    criteria =
        templateServiceHelper.formCriteria(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, null, null, false, "", false);
    criteria.and(TemplateEntityKeys.isLastUpdatedTemplate).is(true);
    pageRequest = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, TemplateEntityKeys.lastUpdatedAt));
    templateEntities = templateService.list(criteria, pageRequest, ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, false);
    assertThat(templateEntities.getContent()).isNotNull();
    assertThat(templateEntities.getContent().size()).isEqualTo(2);
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testForceDeleteWithSettingOffAndOtherDeleteCases() throws IOException {
    TemplateEntity createdEntity3 =
        templateService.create(entity.withVersionLabel("version12").withIdentifier("template12"), false, "", false);
    assertThat(createdEntity3.isStableTemplate()).isTrue();

    Call<ResponseDTO<Boolean>> request1 = mock(Call.class);
    try {
      when(request1.execute()).thenReturn(Response.success(ResponseDTO.newResponse(true)));
    } catch (IOException ex) {
    }
    when(entitySetupUsageClient.isEntityReferenced(any(), any(), any())).thenReturn(request1);

    MockedStatic<CGRestUtils> mockCGRestUtils = Mockito.mockStatic(CGRestUtils.class);
    mockCGRestUtils.when(() -> CGRestUtils.getResponse(any())).thenReturn(true);

    when(settingsClient.getSetting(SettingIdentifiers.ENABLE_FORCE_DELETE, ACCOUNT_ID, null, null)).thenReturn(request);
    SettingValueResponseDTO settingValueResponseDTO =
        SettingValueResponseDTO.builder().value("false").valueType(SettingValueType.BOOLEAN).build();
    when(request.execute()).thenReturn(Response.success(ResponseDTO.newResponse(settingValueResponseDTO)));

    // test force delete
    assertThatThrownBy(()
                           -> templateService.delete(
                               ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, "template12", "version12", null, "", true))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage(
            "Force Delete is not enabled for this account. Please go to Account Resources > Default Settings > Enable Force Delete of Harness Resources to enable this.");

    templateService.create(entity.withVersionLabel("version13").withIdentifier("template12"), false, "", false);
    assertThatThrownBy(()
                           -> templateService.delete(
                               ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, "template12", "version12", null, "", false))
        .hasMessage(
            "You cannot delete the stable version of the template. Please update another version as the stable version before deleting this version")
        .isInstanceOf(InvalidRequestException.class);

    templateService.create(entity.withVersionLabel("version14").withIdentifier("template12"), false, "", false);
    assertThatThrownBy(()
                           -> templateService.deleteTemplates(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, "template12",
                               new HashSet<>(Arrays.asList("version14", "version12")), "", false))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage(
            "You cannot delete the stable version of the template. Please update another version as the stable version before deleting this version");
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testDeleteInvalidTemplateWhichDoesNotExist() throws IOException {
    Call<ResponseDTO<Boolean>> request1 = mock(Call.class);
    try {
      when(request1.execute()).thenReturn(Response.success(ResponseDTO.newResponse(true)));
    } catch (IOException ex) {
    }
    when(entitySetupUsageClient.isEntityReferenced(any(), any(), any())).thenReturn(request1);

    assertThatThrownBy(()
                           -> templateService.delete(
                               ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, "template16", "version12", null, "", true))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage(String.format(
            "Template with identifier [template16] and versionLabel [version12] under Project[projId], Organization [orgId], Account [%s] does not exist.",
            ACCOUNT_ID));

    assertThatThrownBy(
        () -> templateService.delete(ACCOUNT_ID, ORG_IDENTIFIER, null, "template16", "version12", null, "", true))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage(String.format(
            "Template with identifier [template16] and versionLabel [version12] under Organization [orgId], Account [%s] does not exist.",
            ACCOUNT_ID));

    assertThatThrownBy(() -> templateService.delete(ACCOUNT_ID, null, null, "template16", "version12", null, "", true))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage(String.format(
            "Template with identifier [template16] and versionLabel [version12] under Account [%s] does not exist.",
            ACCOUNT_ID));
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testDeleteWithInvalidVersions() {
    Call<ResponseDTO<Boolean>> request1 = mock(Call.class);
    try {
      when(request1.execute()).thenReturn(Response.success(ResponseDTO.newResponse(true)));
    } catch (IOException ex) {
    }
    when(entitySetupUsageClient.isEntityReferenced(ACCOUNT_ID, "fqn1", EntityType.TEMPLATE)).thenReturn(request1);

    TemplateEntity entity1 =
        templateService.create(entity.withVersionLabel("versionx").withIdentifier("templatex"), false, "", false);
    assertThatThrownBy(()
                           -> templateService.delete(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, "templatex",
                               "versionx", (long) 2154, "", false))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage(
            "Template with identifier [templatex] and versionLabel [versionx], under Project[projId], Organization [orgId] is not on the correct version of DB.");

    TemplateEntity entity2 =
        templateService.create(entity.withVersionLabel("versionxy").withIdentifier("templatex"), false, "", false);
    assertThatThrownBy(()
                           -> templateService.delete(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, "templatex",
                               "versionxy", entity2.getVersion(), "", false))
        .isInstanceOf(UnexpectedException.class)
        .hasMessageContainingAll(
            "Error while checking references for template templatex with version label: versionxy :", "null");

    Call<ResponseDTO<Boolean>> request2 = mock(Call.class);
    String fqn2 = String.format("%s/orgId/projId/templateStable/versionxy/", ACCOUNT_ID);
    try {
      when(request2.execute()).thenReturn(Response.success(ResponseDTO.newResponse(false)));
    } catch (IOException ex) {
    }
    when(entitySetupUsageClient.isEntityReferenced(ACCOUNT_ID, fqn2, EntityType.TEMPLATE)).thenReturn(request2);

    Call<ResponseDTO<Boolean>> request3 = mock(Call.class);
    String fqn3 = String.format("%s/orgId/projId/templateStable/__STABLE__/", ACCOUNT_ID);
    try {
      when(request3.execute()).thenReturn(Response.success(ResponseDTO.newResponse(true)));
    } catch (IOException ex) {
    }
    when(entitySetupUsageClient.isEntityReferenced(ACCOUNT_ID, fqn3, EntityType.TEMPLATE)).thenReturn(request3);

    TemplateEntity entity3 =
        templateService.create(entity.withVersionLabel("versionxy").withIdentifier("templateStable"), true, "", false);
    assertThatThrownBy(()
                           -> templateService.delete(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, "templateStable",
                               "versionxy", entity3.getVersion(), "", false))
        .isInstanceOf(ReferencedEntityException.class)
        .hasMessage("Could not delete the template templateStable as it is referenced by other entities");
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testDeleteTemplateVersionLastUpdatedScenarios() {
    /*
      Consider 3 template versions ( version1, version2, version3 )
      version1 is lastUpdatedTemplate
      version2 is Stable template
      version3 has references
      consider case :- We delete version1 and version3 together, since version1 is lastStableTemplate
      this is first deleted, it sets the lastUpdatedTemplate to true, and when we try to delete version3 is gives
      ReferencedEntityException and it updates the lastStableTemplate to stable template i.e version2.
     */

    TemplateEntity createdEntity = templateService.create(entity, false, "", false);
    assertThat(createdEntity.isStableTemplate()).isTrue();

    TemplateEntity entityVersion2 = templateService.create(entity.withVersionLabel("version2"), true, "", false);
    assertThat(entityVersion2.isStableTemplate()).isTrue();

    TemplateEntity entityVersion3 = templateService.create(entity.withVersionLabel("version3"), false, "", false);
    assertThat(entityVersion3.isStableTemplate()).isFalse();
    assertThat(entityVersion3.isLastUpdatedTemplate()).isTrue();

    templateService.updateTemplateEntity(entity.withDescription("Updated"), ChangeType.MODIFY, false, "");

    createdEntity =
        templateService.get(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, TEMPLATE_IDENTIFIER, "version1", false, false)
            .get();
    assertThat(createdEntity.isLastUpdatedTemplate()).isTrue();

    entityVersion2 =
        templateService.get(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, TEMPLATE_IDENTIFIER, "version2", false, false)
            .get();
    assertThat(entityVersion2.isLastUpdatedTemplate()).isFalse();

    entityVersion3 =
        templateService.get(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, TEMPLATE_IDENTIFIER, "version3", false, false)
            .get();
    assertThat(entityVersion3.isLastUpdatedTemplate()).isFalse();

    Criteria criteria =
        templateServiceHelper.formCriteria(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, null, null, false, "", false);
    criteria.and(TemplateEntityKeys.isLastUpdatedTemplate).is(true);

    Pageable pageRequest = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, TemplateEntityKeys.lastUpdatedAt));

    String fqn1 = String.format("%s/orgId/projId/template1/version3/", ACCOUNT_ID);
    String fqn2 = String.format("%s/orgId/projId/template1/version1/", ACCOUNT_ID);

    Call<ResponseDTO<Boolean>> request1 = mock(Call.class);
    try {
      when(request1.execute()).thenReturn(Response.success(ResponseDTO.newResponse(true)));
    } catch (IOException ex) {
    }
    when(entitySetupUsageClient.isEntityReferenced(ACCOUNT_ID, fqn1, EntityType.TEMPLATE)).thenReturn(request1);

    Call<ResponseDTO<Boolean>> request2 = mock(Call.class);
    try {
      when(request2.execute()).thenReturn(Response.success(ResponseDTO.newResponse(false)));
    } catch (IOException ex) {
    }
    when(entitySetupUsageClient.isEntityReferenced(ACCOUNT_ID, fqn2, EntityType.TEMPLATE)).thenReturn(request2);

    // Deleting complete templateIdentifier
    assertThatThrownBy(()
                           -> templateService.deleteTemplates(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER,
                               TEMPLATE_IDENTIFIER, Sets.newHashSet("version1", "version3"), "", false))
        .isInstanceOf(ReferencedEntityException.class);

    entityVersion2 =
        templateService.get(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, TEMPLATE_IDENTIFIER, "version2", false, false)
            .get();
    assertThat(entityVersion2.isLastUpdatedTemplate()).isTrue();

    TemplateEntity entityVersion4 = templateService.create(entity.withVersionLabel("version4"), false, "", false);
    assertThat(entityVersion4.isStableTemplate()).isFalse();
    assertThat(entityVersion4.isLastUpdatedTemplate()).isTrue();

    Call<ResponseDTO<Boolean>> request3 = mock(Call.class);
    try {
      when(request3.execute()).thenReturn(Response.success(ResponseDTO.newResponse(false)));
    } catch (IOException ex) {
    }
    when(entitySetupUsageClient.isEntityReferenced(any(), any(), any())).thenReturn(request3);

    /*
      Trying to delete all templates at once including the stable template, only supported from BE
      Since the lastUpdatedTemplate is also deleted, it sets stable template as lastUpdated
      and Stable Template get deletes at the last
    */

    templateService.deleteTemplates(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, TEMPLATE_IDENTIFIER,
        Sets.newHashSet("version2", "version4", "version3"), "", false);

    Page<TemplateEntity> templateEntities =
        templateService.list(criteria, pageRequest, ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, false);
    assertThat(templateEntities.getContent()).isNotNull();
    assertThat(templateEntities.getContent().size()).isEqualTo(0);
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testCreateNewVersionOfTemplateFromCreateFlow() {
    TemplateEntity createdEntity = templateService.create(entity, false, "", false);
    assertThat(createdEntity.isStableTemplate()).isTrue();
    assertThatThrownBy(() -> templateService.create(entity.withVersionLabel("version2"), false, "", true))
        .isInstanceOf(TemplateAlreadyExistsException.class)
        .hasMessage(String.format(
            "The template with identifier template1 already exists in account %s, org orgId, project projId, if you want to create a new version version2 of this template then use save as new version option from the given template or if you want to create a new Template then use a different identifier.",
            createdEntity.getAccountId()));
    TemplateEntity createdEntity2 = templateService.create(entity.withVersionLabel("version2"), false, "", false);
    assertThat(createdEntity2.getVersionLabel()).isEqualTo("version2");
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testGetAllRbacFilteredTemplates() {
    doReturn(false)
        .when(accessControlClient)
        .hasAccess(ResourceScope.of(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER), Resource.of("TEMPLATE", null),
            TEMPLATE_VIEW_PERMISSION);
    when(templateRbacHelper.getPermittedTemplateList(any())).thenReturn(new ArrayList<>());
    TemplateEntity createdEntity = templateService.create(entity, false, "", false);
    TemplateEntity entityRbacAllowed = TemplateEntity.builder()
                                           .accountId(ACCOUNT_ID)
                                           .orgIdentifier(ORG_IDENTIFIER)
                                           .projectIdentifier(PROJ_IDENTIFIER)
                                           .identifier("DifferentIdentifier")
                                           .name(TEMPLATE_IDENTIFIER)
                                           .versionLabel("DifferentVersion")
                                           .yaml(yaml)
                                           .templateEntityType(TemplateEntityType.STEP_TEMPLATE)
                                           .childType(TEMPLATE_CHILD_TYPE)
                                           .fullyQualifiedIdentifier("account_id/orgId/projId/template1/version1/")
                                           .templateScope(Scope.PROJECT)
                                           .build();
    TemplateEntity entityVersion2 = templateService.create(entityRbacAllowed, false, "", false);
    FilterParamsDTO filterParamsDTO = NGTemplateDtoMapper.prepareFilterParamsDTO("", "", null,
        NGTemplateDtoMapper.toTemplateFilterProperties(
            TemplateFilterPropertiesDTO.builder()
                .templateIdentifiers(Collections.singletonList(TEMPLATE_IDENTIFIER))
                .build()),
        false, false);
    PageParamsDTO pageParamsDTO = NGTemplateDtoMapper.preparePageParamsDTO(0, 1000, new ArrayList<>());
    //    when(templateRbacHelper.getPermittedTemplateList(any())).thenReturn(Collections.singletonList(entityVersion2));
    List<TemplateEntity> rbacFilteredTemplates =
        templateService
            .listTemplateMetadata(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, filterParamsDTO, pageParamsDTO)
            .getContent();
    assertThat(rbacFilteredTemplates.size()).isEqualTo(0);
    //    assertThat(rbacFilteredTemplates.get(0).getIdentifier()).isEqualTo("DifferentIdentifier");
    // TODO :- Jira CDS-72711 Fix RBAC based template filtering
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testSetStableTemplateAsLastUpdatedTemplate() {
    TemplateEntity createdEntity = templateService.create(entity, false, "", false);
    assertThat(createdEntity.isStableTemplate()).isTrue();

    TemplateEntity entityVersion2 = templateService.create(entity.withVersionLabel("version2"), false, "", false);

    entityVersion2 =
        templateService.updateTemplateEntity(entityVersion2.withDescription("Updated"), ChangeType.MODIFY, true, "");

    TemplateEntity entityVersion3 = templateService.create(entity.withVersionLabel("version3"), false, "", false);
    assertThat(entityVersion3.isStableTemplate()).isFalse();
    assertThat(entityVersion3.isLastUpdatedTemplate()).isTrue();

    Call<ResponseDTO<Boolean>> request = mock(Call.class);
    try {
      when(request.execute()).thenReturn(Response.success(ResponseDTO.newResponse(false)));
    } catch (IOException ex) {
    }
    when(entitySetupUsageClient.isEntityReferenced(any(), any(), any())).thenReturn(request);

    // Deleting a last updated version for a particular templateIdentifier.
    boolean delete = templateService.delete(
        ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, TEMPLATE_IDENTIFIER, "version1", null, "", false);
    assertThat(delete).isTrue();

    Criteria criteria =
        templateServiceHelper.formCriteria(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, null, null, false, "", false);
    criteria.and(TemplateEntityKeys.isLastUpdatedTemplate).is(true);
    Pageable pageRequest = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, TemplateEntityKeys.lastUpdatedAt));
    Page<TemplateEntity> templateEntities =
        templateService.list(criteria, pageRequest, ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, false);
    assertThat(templateEntities.getContent()).isNotNull();
    assertThat(templateEntities.getContent().size()).isEqualTo(1);
    assertThat(entityVersion3.isLastUpdatedTemplate()).isTrue();
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testDeleteAllTemplatesInAProject() {
    TemplateEntity createdEntity = templateService.create(entity, false, "", false);
    assertThat(createdEntity.isStableTemplate()).isTrue();

    TemplateEntity entityVersion2 = templateService.create(entity.withVersionLabel("version2"), true, "", false);
    assertThat(entityVersion2.isStableTemplate()).isTrue();

    TemplateEntity template2EntityVersion2 =
        templateService.create(entity.withVersionLabel("version2").withIdentifier("template2"), false, "", false);
    assertThat(template2EntityVersion2.isStableTemplate()).isTrue();

    TemplateEntity template2EntityVersion3 =
        templateService.create(entity.withVersionLabel("version3").withIdentifier("template2"), true, "", false);
    assertThat(template2EntityVersion3.isStableTemplate()).isTrue();
    assertThat(template2EntityVersion3.isLastUpdatedTemplate()).isTrue();

    Criteria criteria =
        templateServiceHelper.formCriteria(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, null, null, false, "", false);
    Pageable pageRequest = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, TemplateEntityKeys.lastUpdatedAt));
    Page<TemplateEntity> templateEntities =
        templateService.list(criteria, pageRequest, ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, false);
    assertThat(templateEntities.getContent()).isNotNull();
    assertThat(templateEntities.getContent().size()).isEqualTo(4);

    // Deleting all templates in the project
    boolean delete = templateService.deleteAllTemplatesInAProject(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER);
    assertThat(delete).isTrue();

    templateEntities = templateService.list(criteria, pageRequest, ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, false);
    assertThat(templateEntities.getContent()).isNotNull();
    assertThat(templateEntities.getContent().size()).isZero();
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testDeleteAllOrgLevelTemplates() {
    entity = TemplateEntity.builder()
                 .accountId(ACCOUNT_ID)
                 .orgIdentifier(ORG_IDENTIFIER)
                 .identifier(TEMPLATE_IDENTIFIER)
                 .name(TEMPLATE_IDENTIFIER)
                 .versionLabel(TEMPLATE_VERSION_LABEL)
                 .yaml(yaml)
                 .templateEntityType(TemplateEntityType.STEP_TEMPLATE)
                 .childType(TEMPLATE_CHILD_TYPE)
                 .fullyQualifiedIdentifier("account_id/orgId/projId/template1/version1/")
                 .templateScope(Scope.PROJECT)
                 .build();
    TemplateEntity createdEntity = templateService.create(entity, false, "", false);
    assertThat(createdEntity.isStableTemplate()).isTrue();

    Criteria criteria = Criteria.where(TemplateEntityKeys.accountId)
                            .is(ACCOUNT_ID)
                            .and(TemplateEntityKeys.orgIdentifier)
                            .is(ORG_IDENTIFIER)
                            .and(TemplateEntityKeys.projectIdentifier)
                            .exists(false);
    Pageable pageRequest = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, TemplateEntityKeys.lastUpdatedAt));
    Page<TemplateEntity> templateEntities =
        templateService.list(criteria, pageRequest, ACCOUNT_ID, ORG_IDENTIFIER, null, false);
    assertThat(templateEntities.getContent()).isNotNull();
    assertThat(templateEntities.getContent().size()).isEqualTo(1);

    // Deleting all org level templates
    boolean delete = templateService.deleteAllOrgLevelTemplates(ACCOUNT_ID, ORG_IDENTIFIER);
    assertThat(delete).isTrue();

    templateEntities = templateService.list(criteria, pageRequest, ACCOUNT_ID, ORG_IDENTIFIER, null, false);
    assertThat(templateEntities.getContent()).isNotNull();
    assertThat(templateEntities.getContent().size()).isZero();
  }

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void testCreateAndUpdateWithStableTemplate() {
    TemplateEntity createdEntity = templateService.create(entity, false, "", false);
    assertThat(createdEntity.isStableTemplate()).isTrue();

    TemplateEntity entityVersion2 = templateService.create(entity.withVersionLabel("version2"), false, "", false);
    assertThat(entityVersion2.isStableTemplate()).isFalse();

    TemplateEntity entityVersion3 = templateService.create(entity.withVersionLabel("version3"), true, "", false);
    assertThat(entityVersion3.isStableTemplate()).isTrue();

    Criteria criteria =
        templateServiceHelper.formCriteria(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, null, null, false, "", false);
    Pageable pageRequest = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, TemplateEntityKeys.lastUpdatedAt));
    Page<TemplateEntity> templateEntities =
        templateService.list(criteria, pageRequest, ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, false);
    assertThat(templateEntities.getContent()).isNotNull();
    assertThat(templateEntities.getContent().size()).isEqualTo(3);
    assertThat(templateEntities.getContent().get(0).getIdentifier()).isEqualTo(TEMPLATE_IDENTIFIER);
    assertThat(templateEntities.getContent().get(0).getVersionLabel()).isEqualTo(TEMPLATE_VERSION_LABEL);
    assertThat(templateEntities.getContent().get(0).isStableTemplate()).isFalse();

    // Check update stable template
    TemplateEntity updatedEntity =
        templateService.updateTemplateEntity(entityVersion2.withDescription("Updated"), ChangeType.MODIFY, true, "");
    templateEntities = templateService.list(criteria, pageRequest, ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, false);
    assertThat(templateEntities.getContent()).isNotNull();
    assertThat(templateEntities.getContent().size()).isEqualTo(3);
    assertThat(templateEntities.getContent().get(1).getIdentifier()).isEqualTo(TEMPLATE_IDENTIFIER);
    assertThat(templateEntities.getContent().get(1).getVersionLabel()).isEqualTo("version2");
    assertThat(templateEntities.getContent().get(1).isStableTemplate()).isTrue();
    assertThat(templateEntities.getContent().get(2).getVersionLabel()).isEqualTo("version3");
    assertThat(templateEntities.getContent().get(2).isStableTemplate()).isFalse();
  }

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void testServiceLayerForOrgScopeTemplates() throws IOException {
    ClassLoader classLoader = this.getClass().getClassLoader();
    String filename = "template-orgScope.yaml";
    yaml = Resources.toString(Objects.requireNonNull(classLoader.getResource(filename)), StandardCharsets.UTF_8);

    entity = NGTemplateDtoMapper.toTemplateEntity(ACCOUNT_ID, yaml);

    TemplateEntity createdEntity = templateService.create(entity, false, "", false);
    assertThat(createdEntity).isNotNull();
    assertThat(createdEntity.getAccountId()).isEqualTo(ACCOUNT_ID);
    assertThat(createdEntity.getOrgIdentifier()).isEqualTo(ORG_IDENTIFIER);
    assertThat(createdEntity.getProjectIdentifier()).isNull();
    assertThat(createdEntity.getIdentifier()).isEqualTo(TEMPLATE_IDENTIFIER);
    assertThat(createdEntity.getVersion()).isZero();

    Optional<TemplateEntity> optionalTemplateEntity = templateService.get(
        ACCOUNT_ID, ORG_IDENTIFIER, null, TEMPLATE_IDENTIFIER, TEMPLATE_VERSION_LABEL, false, false);
    assertThat(optionalTemplateEntity).isPresent();
    assertThat(optionalTemplateEntity.get().getAccountId()).isEqualTo(ACCOUNT_ID);
    assertThat(optionalTemplateEntity.get().getOrgIdentifier()).isEqualTo(ORG_IDENTIFIER);
    assertThat(optionalTemplateEntity.get().getProjectIdentifier()).isNull();
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
    assertThat(updatedTemplateEntity.getProjectIdentifier()).isNull();
    assertThat(updatedTemplateEntity.getIdentifier()).isEqualTo(TEMPLATE_IDENTIFIER);
    assertThat(updatedTemplateEntity.getVersionLabel()).isEqualTo(TEMPLATE_VERSION_LABEL);
    assertThat(updatedTemplateEntity.getVersion()).isEqualTo(1L);
    assertThat(updatedTemplateEntity.getDescription()).isEqualTo(description);

    TemplateEntity incorrectTemplate = entity.withVersionLabel("incorrect version");
    assertThatThrownBy(() -> templateService.updateTemplateEntity(incorrectTemplate, ChangeType.MODIFY, false, ""))
        .isInstanceOf(InvalidRequestException.class);

    // Test template list
    Criteria criteria =
        templateServiceHelper.formCriteria(ACCOUNT_ID, ORG_IDENTIFIER, null, null, null, false, "", false);
    Pageable pageRequest = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, TemplateEntityKeys.lastUpdatedAt));
    Page<TemplateEntity> templateEntities =
        templateService.list(criteria, pageRequest, ACCOUNT_ID, ORG_IDENTIFIER, null, false);
    assertThat(templateEntities.getContent()).isNotNull();
    assertThat(templateEntities.getContent().size()).isEqualTo(1);
    assertThat(templateEntities.getContent().get(0).getIdentifier()).isEqualTo(TEMPLATE_IDENTIFIER);

    // Add 1 more entry to template db
    TemplateEntity version2 = entity.withVersionLabel("version2");
    templateService.create(version2, false, "", false);

    templateEntities = templateService.list(criteria, pageRequest, ACCOUNT_ID, ORG_IDENTIFIER, null, false);
    assertThat(templateEntities.getContent()).isNotNull();
    assertThat(templateEntities.getContent().size()).isEqualTo(2);
    assertThat(templateEntities.getContent().get(0).getIdentifier()).isEqualTo(TEMPLATE_IDENTIFIER);
    assertThat(templateEntities.getContent().get(0).getVersionLabel()).isEqualTo(TEMPLATE_VERSION_LABEL);
    assertThat(templateEntities.getContent().get(1).getVersionLabel()).isEqualTo("version2");

    // Template list with search term
    criteria =
        templateServiceHelper.formCriteria(ACCOUNT_ID, ORG_IDENTIFIER, null, null, null, false, "version2", false);
    templateEntities = templateService.list(criteria, pageRequest, ACCOUNT_ID, ORG_IDENTIFIER, null, false);
    assertThat(templateEntities.getContent()).isNotNull();
    assertThat(templateEntities.getContent().size()).isEqualTo(1);
    assertThat(templateEntities.getContent().get(0).getIdentifier()).isEqualTo(TEMPLATE_IDENTIFIER);
    assertThat(templateEntities.getContent().get(0).getVersionLabel()).isEqualTo("version2");

    // Update stable template
    TemplateEntity updateStableTemplateVersion = templateService.updateStableTemplateVersion(
        ACCOUNT_ID, ORG_IDENTIFIER, null, TEMPLATE_IDENTIFIER, "version2", "");
    assertThat(updateStableTemplateVersion).isNotNull();
    assertThat(updateStableTemplateVersion.getAccountId()).isEqualTo(ACCOUNT_ID);
    assertThat(updateStableTemplateVersion.getOrgIdentifier()).isEqualTo(ORG_IDENTIFIER);
    assertThat(updateStableTemplateVersion.getProjectIdentifier()).isNull();
    assertThat(updateStableTemplateVersion.getIdentifier()).isEqualTo(TEMPLATE_IDENTIFIER);
    assertThat(updateStableTemplateVersion.getVersionLabel()).isEqualTo("version2");
    assertThat(updateStableTemplateVersion.getVersion()).isEqualTo(1L);
    assertThat(updateStableTemplateVersion.isStableTemplate()).isTrue();

    Call<ResponseDTO<Boolean>> request = mock(Call.class);
    try {
      when(request.execute()).thenReturn(Response.success(ResponseDTO.newResponse(false)));
    } catch (IOException ex) {
    }
    when(entitySetupUsageClient.isEntityReferenced(any(), any(), any())).thenReturn(request);

    // delete template stable template
    assertThatThrownBy(
        () -> templateService.delete(ACCOUNT_ID, ORG_IDENTIFIER, null, TEMPLATE_IDENTIFIER, "version2", 1L, "", false))
        .isInstanceOf(InvalidRequestException.class);

    boolean delete = templateService.delete(
        ACCOUNT_ID, ORG_IDENTIFIER, null, TEMPLATE_IDENTIFIER, TEMPLATE_VERSION_LABEL, null, "", false);
    assertThat(delete).isTrue();
  }

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void testTemplateSettingsChangeScope() throws JsonProcessingException {
    // Test to update scope from project to org
    templateService.create(entity, false, "", false);
    // Add 1 more entry to template db
    TemplateEntity version2 = entity.withVersionLabel("version2");
    NGTemplateConfig config = NGTemplateDtoMapper.toDTO(version2.getYaml());
    config.getTemplateInfoConfig().setVersionLabel("version2");
    config.getTemplateInfoConfig().setDescription(ParameterField.createValueField(""));
    version2 = entity.withVersionLabel("version2").withYaml(YamlPipelineUtils.getYamlString(config));
    templateService.create(version2, false, "", false);

    TemplateEntity version3;
    config.getTemplateInfoConfig().setVersionLabel("version3");
    config.getTemplateInfoConfig().setDescription(ParameterField.createValueField(""));
    version3 = entity.withVersionLabel("version3").withYaml(YamlPipelineUtils.getYamlString(config));
    templateService.create(version3, false, "", false);

    // Adding different template identifier to just cover more test cases
    TemplateEntity differentIdentifierTemplate =
        TemplateEntity.builder()
            .accountId(ACCOUNT_ID)
            .orgIdentifier(ORG_IDENTIFIER)
            .projectIdentifier(PROJ_IDENTIFIER)
            .identifier("DifferentIdentifier")
            .name(TEMPLATE_IDENTIFIER)
            .versionLabel("DifferentVersion")
            .yaml(yaml)
            .templateEntityType(TemplateEntityType.STEP_TEMPLATE)
            .childType(TEMPLATE_CHILD_TYPE)
            .fullyQualifiedIdentifier("account_id/orgId/projId/template1/version1/")
            .templateScope(Scope.PROJECT)
            .build();
    templateService.create(differentIdentifierTemplate, false, "", false);

    Criteria criteria =
        templateServiceHelper.formCriteria(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, null, null, false, "", false);
    Pageable pageRequest = PageRequest.of(0, 100, Sort.by(Sort.Direction.DESC, TemplateEntityKeys.lastUpdatedAt));
    Page<TemplateEntity> templateEntities =
        templateService.list(criteria, pageRequest, ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, false);
    assertThat(templateEntities.getContent()).isNotNull();
    assertThat(templateEntities.getContent().size()).isEqualTo(4);
    assertThat(templateEntities.getContent().get(0).getVersionLabel()).isEqualTo(TEMPLATE_VERSION_LABEL);
    assertThat(templateEntities.getContent().get(1).getVersionLabel()).isEqualTo("version2");
    assertThat(templateEntities.getContent().get(2).getVersionLabel()).isEqualTo("version3");
    assertThat(templateEntities.getContent().get(0).isStableTemplate()).isTrue();
    assertThat(templateEntities.getContent().get(0).getTemplateScope()).isEqualTo(Scope.PROJECT);
    assertThat(templateEntities.getContent().get(1).getTemplateScope()).isEqualTo(Scope.PROJECT);
    assertThat(templateEntities.getContent().get(2).getTemplateScope()).isEqualTo(Scope.PROJECT);
    assertThat(templateEntities.getContent().get(2).isLastUpdatedTemplate()).isTrue();

    // Check for last update criteria
    criteria = templateServiceHelper.formCriteria(criteria, TemplateListType.LAST_UPDATED_TEMPLATE_TYPE);
    templateEntities = templateService.list(criteria, pageRequest, ACCOUNT_ID, ORG_IDENTIFIER, null, false);
    assertThat(templateEntities.getContent()).isNotNull();
    assertThat(templateEntities.getContent().get(0).getIdentifier()).isEqualTo(TEMPLATE_IDENTIFIER);
    assertThat(templateEntities.getContent().get(0).getVersionLabel()).isEqualTo("version3");
    assertThat(templateEntities.getContent().get(0).isLastUpdatedTemplate()).isTrue();
    assertThat(templateEntities.getContent().get(1).getIdentifier()).isEqualTo("DifferentIdentifier");
    assertThat(templateEntities.getContent().get(1).getVersionLabel()).isEqualTo("DifferentVersion");
    assertThat(templateEntities.getContent().get(1).isLastUpdatedTemplate()).isTrue();

    // Update version2 to check lastUpdatedBy
    version2 = version2.withDescription("Updated desciption");
    templateService.updateTemplateEntity(version2, ChangeType.MODIFY, false, "");
    templateEntities = templateService.list(criteria, pageRequest, ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, false);
    assertThat(templateEntities.getContent().get(1).isLastUpdatedTemplate()).isTrue();

    // Call template scope change from project to org
    templateService.updateTemplateSettings(
        ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, TEMPLATE_IDENTIFIER, Scope.PROJECT, Scope.ORG, "version3", false);

    criteria = templateServiceHelper.formCriteria(ACCOUNT_ID, ORG_IDENTIFIER, null, null, null, false, "", false);
    templateEntities = templateService.list(criteria, pageRequest, ACCOUNT_ID, ORG_IDENTIFIER, null, false);
    assertThat(templateEntities.getContent()).isNotNull();
    assertThat(templateEntities.getContent().size()).isEqualTo(3);
    assertThat(templateEntities.getContent().get(0).getVersionLabel()).isEqualTo(TEMPLATE_VERSION_LABEL);
    assertThat(templateEntities.getContent().get(1).getVersionLabel()).isEqualTo("version2");
    assertThat(templateEntities.getContent().get(2).getVersionLabel()).isEqualTo("version3");
    assertThat(templateEntities.getContent().get(0).isStableTemplate()).isFalse();
    assertThat(templateEntities.getContent().get(2).isStableTemplate()).isTrue();
    assertThat(templateEntities.getContent().get(0).getTemplateScope()).isEqualTo(Scope.ORG);
    assertThat(templateEntities.getContent().get(1).getTemplateScope()).isEqualTo(Scope.ORG);
    assertThat(templateEntities.getContent().get(2).getTemplateScope()).isEqualTo(Scope.ORG);
    assertThat(templateEntities.getContent().get(2).isLastUpdatedTemplate()).isTrue();

    // Test to check include all templates
    criteria =
        templateServiceHelper.formCriteria(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, null, null, false, "", true);
    templateEntities = templateService.list(criteria, pageRequest, ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, false);
    assertThat(templateEntities.getContent()).isNotNull();
    assertThat(templateEntities.getContent().size()).isEqualTo(4);

    // Test to update scope from org to project
    // Call template scope change from project to org
    templateService.updateTemplateSettings(
        ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, TEMPLATE_IDENTIFIER, Scope.ORG, Scope.PROJECT, "version3", false);

    criteria =
        templateServiceHelper.formCriteria(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, null, null, false, "", false);
    templateEntities = templateService.list(criteria, pageRequest, ACCOUNT_ID, ORG_IDENTIFIER, null, false);
    assertThat(templateEntities.getContent()).isNotNull();
    assertThat(templateEntities.getContent().size()).isEqualTo(4);
    assertThat(templateEntities.getContent().get(0).getVersionLabel()).isEqualTo(TEMPLATE_VERSION_LABEL);
    assertThat(templateEntities.getContent().get(1).getVersionLabel()).isEqualTo("version2");
    assertThat(templateEntities.getContent().get(2).getVersionLabel()).isEqualTo("version3");
    assertThat(templateEntities.getContent().get(0).isStableTemplate()).isFalse();
    assertThat(templateEntities.getContent().get(2).isStableTemplate()).isTrue();
    assertThat(templateEntities.getContent().get(0).getTemplateScope()).isEqualTo(Scope.PROJECT);
    assertThat(templateEntities.getContent().get(1).getTemplateScope()).isEqualTo(Scope.PROJECT);
    assertThat(templateEntities.getContent().get(2).getTemplateScope()).isEqualTo(Scope.PROJECT);

    // Only stable template update and no scope change
    templateService.updateTemplateSettings(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, TEMPLATE_IDENTIFIER,
        Scope.PROJECT, Scope.PROJECT, "version1", false);

    criteria =
        templateServiceHelper.formCriteria(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, null, null, false, "", false);
    templateEntities = templateService.list(criteria, pageRequest, ACCOUNT_ID, ORG_IDENTIFIER, null, false);
    assertThat(templateEntities.getContent()).isNotNull();
    assertThat(templateEntities.getContent().size()).isEqualTo(4);
    assertThat(templateEntities.getContent().get(0).getVersionLabel()).isEqualTo(TEMPLATE_VERSION_LABEL);
    assertThat(templateEntities.getContent().get(1).getVersionLabel()).isEqualTo("version2");
    assertThat(templateEntities.getContent().get(2).getVersionLabel()).isEqualTo("version3");
    assertThat(templateEntities.getContent().get(0).isStableTemplate()).isTrue();
    assertThat(templateEntities.getContent().get(2).isStableTemplate()).isFalse();
    assertThat(templateEntities.getContent().get(0).getTemplateScope()).isEqualTo(Scope.PROJECT);
    assertThat(templateEntities.getContent().get(1).getTemplateScope()).isEqualTo(Scope.PROJECT);
    assertThat(templateEntities.getContent().get(2).getTemplateScope()).isEqualTo(Scope.PROJECT);

    Call<ResponseDTO<Boolean>> request = mock(Call.class);
    try {
      when(request.execute()).thenReturn(Response.success(ResponseDTO.newResponse(false)));
    } catch (IOException ex) {
    }

    when(entitySetupUsageClient.isEntityReferenced(any(), any(), any())).thenReturn(request);
    // Test multiple template delete

    boolean deleteTemplates = templateService.deleteTemplates(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER,
        TEMPLATE_IDENTIFIER, new HashSet<>(Arrays.asList(TEMPLATE_VERSION_LABEL, "version2", "version3")), "", false);
    assertThat(deleteTemplates).isTrue();

    criteria =
        templateServiceHelper.formCriteria(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, null, null, false, "", false);
    templateEntities = templateService.list(criteria, pageRequest, ACCOUNT_ID, ORG_IDENTIFIER, null, false);
    assertThat(templateEntities.getContent()).isNotNull();
    assertThat(templateEntities.getContent().size()).isEqualTo(1);
    assertThat(templateEntities.getContent().get(0).getIdentifier()).isEqualTo("DifferentIdentifier");
  }

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void testGetActualComments() {
    // Testing comments if git sync is not enabled.
    String comments = templateService.getActualComments(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, "COMMENTS");
    assertThat(comments).isEqualTo("COMMENTS");

    // Testing comments if git sync is enabled
    when(gitSyncSdkService.isGitSyncEnabled(any(), any(), any())).thenReturn(true);
    GitEntityInfo branchInfo = GitEntityInfo.builder().storeType(StoreType.REMOTE).commitMsg("test").build();
    setupGitContext(branchInfo);
    String comment = templateService.getActualComments(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, "COMMENTS");
    assertThat(comment).isEqualTo("test");
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void shouldThrowExceptionWithEmptyOrganizationIdOnProjectLevelTemplate() {
    TemplateEntity templateEntity = TemplateEntity.builder()
                                        .accountId(ACCOUNT_ID)
                                        .projectIdentifier(PROJ_IDENTIFIER)
                                        .identifier(TEMPLATE_IDENTIFIER)
                                        .name(TEMPLATE_IDENTIFIER)
                                        .versionLabel(TEMPLATE_VERSION_LABEL)
                                        .yaml(yaml)
                                        .templateEntityType(TemplateEntityType.STEP_TEMPLATE)
                                        .fullyQualifiedIdentifier("account_id/projId/template1/version1/")
                                        .templateScope(Scope.PROJECT)
                                        .build();

    assertThatThrownBy(() -> templateService.create(templateEntity, false, "", false))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Project projId specified without the org Identifier");
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void shouldThrowExceptionOnTemplateTypeAndChildTypeChange() {
    TemplateEntity shellStepTemplate =
        entity.withTemplateEntityType(TemplateEntityType.STEP_TEMPLATE).withChildType("ShellScript");

    templateService.create(shellStepTemplate, false, "", false);

    TemplateEntity stageTemplate =
        entity.withVersionLabel("v2").withTemplateEntityType(TemplateEntityType.STAGE_TEMPLATE);
    assertThatThrownBy(() -> templateService.create(stageTemplate, false, "", false))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage(
            "Error while saving template [template1] of versionLabel [v2]: Template should have same template entity type Step as other template versions");

    TemplateEntity httpStepTemplate = shellStepTemplate.withVersionLabel("v3").withChildType("Http");
    assertThatThrownBy(() -> templateService.create(httpStepTemplate, false, "", false))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage(
            "Error while saving template [template1] of versionLabel [v3]: Template should have same child type ShellScript as other template versions");
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testGetTemplateWithInputs() {
    String originalTemplateYamlFileName = "template-yaml.yaml";
    String originalTemplateYaml = readFile(originalTemplateYamlFileName);

    String templateInputs = "type: \"ShellScript\"\n"
        + "spec:\n"
        + "  source:\n"
        + "    type: \"Inline\"\n"
        + "    spec:\n"
        + "      script: \"<+input>\"\n"
        + "timeout: \"<+input>\"\n";

    TemplateEntity templateEntity = TemplateEntity.builder()
                                        .accountId(ACCOUNT_ID)
                                        .orgIdentifier(ORG_IDENTIFIER)
                                        .projectIdentifier(PROJ_IDENTIFIER)
                                        .identifier("zxcv")
                                        .name("zxcv")
                                        .versionLabel("as")
                                        .yaml(originalTemplateYaml)
                                        .templateEntityType(TemplateEntityType.STEP_TEMPLATE)
                                        .childType(TEMPLATE_CHILD_TYPE)
                                        .fullyQualifiedIdentifier("account_id/orgId/projId/template1/version1/")
                                        .templateScope(Scope.PROJECT)
                                        .build();

    templateService.create(templateEntity, false, "", false);

    doReturn(Optional.of(templateEntity))
        .when(templateServiceHelper)
        .getTemplateOrThrowExceptionIfInvalid(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, "zxcv", "as", false, false);
    when(templateMergeServiceHelper.createTemplateInputsFromTemplate(yaml)).thenReturn(templateInputs);

    TemplateWithInputsResponseDTO templateWithInputsResponseDTO =
        templateService.getTemplateWithInputs(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, "zxcv", "as", false);
    assertThat(templateInputs).isEqualTo(templateWithInputsResponseDTO.getTemplateInputs());
    assertThat(originalTemplateYaml).isEqualTo(templateWithInputsResponseDTO.getTemplateResponseDTO().getYaml());

    assertThatThrownBy(()
                           -> templateService.getTemplateWithInputs(
                               ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, "zxcv", "random", false))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage(
            "Template with the given Identifier: zxcv and versionLabel: random does not exist or has been deleted");
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testGetTemplateWithInputsWithCaching() {
    String originalTemplateYamlFileName = "template-yaml.yaml";
    String originalTemplateYaml = readFile(originalTemplateYamlFileName);

    String templateInputs = "type: \"ShellScript\"\n"
        + "spec:\n"
        + "  source:\n"
        + "    type: \"Inline\"\n"
        + "    spec:\n"
        + "      script: \"<+input>\"\n"
        + "timeout: \"<+input>\"\n";

    TemplateEntity templateEntity = TemplateEntity.builder()
                                        .accountId(ACCOUNT_ID)
                                        .orgIdentifier(ORG_IDENTIFIER)
                                        .projectIdentifier(PROJ_IDENTIFIER)
                                        .identifier("zxcv")
                                        .name("zxcv")
                                        .versionLabel("as")
                                        .yaml(originalTemplateYaml)
                                        .templateEntityType(TemplateEntityType.STEP_TEMPLATE)
                                        .childType(TEMPLATE_CHILD_TYPE)
                                        .fullyQualifiedIdentifier("account_id/orgId/projId/template1/version1/")
                                        .templateScope(Scope.PROJECT)
                                        .build();

    templateService.create(templateEntity, false, "", false);

    doReturn(Optional.of(templateEntity))
        .when(templateServiceHelper)
        .getTemplateOrThrowExceptionIfInvalid(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, "zxcv", "as", false, true);
    when(templateMergeServiceHelper.createTemplateInputsFromTemplate(yaml)).thenReturn(templateInputs);

    TemplateWithInputsResponseDTO templateWithInputsResponseDTO =
        templateService.getTemplateWithInputs(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, "zxcv", "as", true);
    assertThat(templateInputs).isEqualTo(templateWithInputsResponseDTO.getTemplateInputs());
    assertThat(originalTemplateYaml).isEqualTo(templateWithInputsResponseDTO.getTemplateResponseDTO().getYaml());
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testValidateTemplateVersion() {
    String originalTemplateYamlFileName = "template-yaml.yaml";
    String originalTemplateYaml = readFile(originalTemplateYamlFileName);

    TemplateEntity templateEntity = TemplateEntity.builder()
                                        .accountId(ACCOUNT_ID)
                                        .orgIdentifier(ORG_IDENTIFIER)
                                        .projectIdentifier(PROJ_IDENTIFIER)
                                        .identifier("zxcv")
                                        .name("zxcv")
                                        .versionLabel("as")
                                        .yaml(originalTemplateYaml)
                                        .templateEntityType(TemplateEntityType.STEP_TEMPLATE)
                                        .childType(TEMPLATE_CHILD_TYPE)
                                        .fullyQualifiedIdentifier("account_id/orgId/projId/template1/version1/")
                                        .templateScope(Scope.PROJECT)
                                        .storeType(StoreType.REMOTE)
                                        .build();

    doReturn(Optional.of(templateEntity))
        .when(templateServiceHelper)
        .getTemplate(anyString(), anyString(), anyString(), anyString(), anyString(), anyBoolean(), anyBoolean(),
            anyBoolean(), anyBoolean());

    assertThatThrownBy(
        () -> templateService.get(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, "zxcv", "as", false, false, false))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("[Error while retrieving template with identifier [zxcv] and versionLabel [as]]: INVALID_REQUEST");
    /*
    TODO:- Fix error message here Jira CDS-72694
      "Template version from remote template file [%s] does not match with template version in request [%s]. Each
    template version maps to a unique file on Git. Create a new version through harness or import a new version if the
    file is already created on Git" Above message should have been thrown
     */
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void shouldThrowExceptionIfTemplateAlreadyExists() {
    TemplateEntity createdEntity = templateService.create(entity, false, "", false);
    assertThat(createdEntity).isNotNull();
    assertThat(createdEntity.getAccountId()).isEqualTo(ACCOUNT_ID);
    assertThat(createdEntity.getOrgIdentifier()).isEqualTo(ORG_IDENTIFIER);
    assertThat(createdEntity.getProjectIdentifier()).isEqualTo(PROJ_IDENTIFIER);
    assertThat(createdEntity.getIdentifier()).isEqualTo(TEMPLATE_IDENTIFIER);
    assertThat(createdEntity.getVersion()).isZero();

    assertThatThrownBy(() -> templateService.create(entity, false, "", false))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage(String.format(
            "The template with identifier %s and version label %s already exists in the account %s, org %s, project %s",
            entity.getIdentifier(), entity.getVersionLabel(), entity.getAccountId(), entity.getOrgIdentifier(),
            entity.getProjectIdentifier()));
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void shouldCreateUpdateForNestedTemplates() {
    String stepYaml = readFile("service/shell-step-template.yaml");
    TemplateEntity stepTemplate = entity.withYaml(stepYaml);
    TemplateEntity createdEntity = templateService.create(stepTemplate, false, "", false);
    assertSavedTemplateEntity(createdEntity, TEMPLATE_IDENTIFIER);
    assertThat(createdEntity.getVersion()).isZero();
    verify(accessControlClient, never())
        .checkForAccessOrThrow(ResourceScope.of(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER),
            Resource.of(NGTemplateResource.TEMPLATE, TEMPLATE_IDENTIFIER), PermissionTypes.TEMPLATE_ACCESS_PERMISSION);

    String stageTemplateIdentifier = "template2";
    String stageYamlWithMissingInputs = readFile("service/updated-stage-template-with-step-template.yaml");
    TemplateEntity stageTemplateWithMissingInputs =
        entity.withYaml(stageYamlWithMissingInputs).withIdentifier(stageTemplateIdentifier);

    String stageYaml = readFile("service/stage-template-with-step-template.yaml");
    TemplateEntity stageTemplate = entity.withYaml(stageYaml).withIdentifier(stageTemplateIdentifier);
    TemplateEntity createdStageTemplate = templateService.create(stageTemplate, false, "", false);
    assertSavedTemplateEntity(createdStageTemplate, stageTemplateIdentifier);
    verify(accessControlClient, times(1))
        .checkForAccessOrThrow(ResourceScope.of(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER),
            Resource.of(NGTemplateResource.TEMPLATE, TEMPLATE_IDENTIFIER), PermissionTypes.TEMPLATE_ACCESS_PERMISSION);
    assertThat(createdStageTemplate.getVersion()).isZero();

    String updatedStepYaml = readFile("service/updated-shell-step-template.yaml");
    TemplateEntity updatedStepTemplate = entity.withYaml(updatedStepYaml);
    TemplateEntity updatedStepEntity =
        templateService.updateTemplateEntity(updatedStepTemplate, ChangeType.MODIFY, false, "");
    assertSavedTemplateEntity(updatedStepEntity, TEMPLATE_IDENTIFIER);

    TemplateEntity updatedStageEntityWithMissingInputs =
        templateService.updateTemplateEntity(stageTemplateWithMissingInputs, ChangeType.MODIFY, false, "");
    assertSavedTemplateEntity(updatedStageEntityWithMissingInputs, stageTemplateIdentifier);
    verify(accessControlClient, times(2))
        .checkForAccessOrThrow(ResourceScope.of(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER),
            Resource.of(NGTemplateResource.TEMPLATE, TEMPLATE_IDENTIFIER), PermissionTypes.TEMPLATE_ACCESS_PERMISSION);
  }

  @Test()
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testMoveConfig() {
    NGTemplateServiceImpl ngTemplateService = spy(templateService);
    TemplateEntity templateEntity = TemplateEntity.builder()
                                        .accountId(ACCOUNT_ID)
                                        .orgIdentifier(ORG_IDENTIFIER)
                                        .projectIdentifier(PROJ_IDENTIFIER)
                                        .identifier(TEMPLATE_IDENTIFIER)
                                        .name(TEMPLATE_IDENTIFIER)
                                        .versionLabel(TEMPLATE_VERSION_LABEL)
                                        .templateScope(Scope.PROJECT)
                                        .templateEntityType(TemplateEntityType.STEP_TEMPLATE)
                                        .yaml(yaml)
                                        .childType("Deployment")
                                        .build();
    ngTemplateService.create(templateEntity, true, "", false);
    doReturn(templateEntity)
        .when(ngTemplateService)
        .moveTemplateEntity(any(), any(), any(), any(), any(), any(TemplateMoveConfigOperationDTO.class), any());
    TemplateMoveConfigRequestDTO moveConfigOperationDTO =
        TemplateMoveConfigRequestDTO.builder()
            .isNewBranch(false)
            .moveConfigOperationType(TemplateMoveConfigOperationType.INLINE_TO_REMOTE)
            .versionLabel(TEMPLATE_VERSION_LABEL)
            .build();
    TemplateMoveConfigResponse response = ngTemplateService.moveTemplateStoreTypeConfig(
        ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, TEMPLATE_IDENTIFIER, moveConfigOperationDTO);
    assertThat(response.getTemplateIdentifier()).isEqualTo(TEMPLATE_IDENTIFIER);
    assertThat(response.getVersionLabel()).isEqualTo(TEMPLATE_VERSION_LABEL);
  }

  @Test()
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testMoveTemplateEntity() {
    TemplateEntity templateEntity = TemplateEntity.builder()
                                        .accountId(ACCOUNT_ID)
                                        .orgIdentifier(ORG_IDENTIFIER)
                                        .projectIdentifier(PROJ_IDENTIFIER)
                                        .identifier(TEMPLATE_IDENTIFIER)
                                        .name(TEMPLATE_IDENTIFIER)
                                        .versionLabel(TEMPLATE_VERSION_LABEL)
                                        .templateScope(Scope.PROJECT)
                                        .storeType(StoreType.INLINE)
                                        .deleted(false)
                                        .templateEntityType(TemplateEntityType.STEP_TEMPLATE)
                                        .yaml(yaml)
                                        .childType("Deployment")
                                        .build();
    templateService.create(templateEntity, true, "", false);
    when(gitAwareEntityHelper.getRepoUrl(any(), any(), any())).thenReturn("repoUrl");
    TemplateMoveConfigOperationDTO moveConfigOperationDTO =
        TemplateMoveConfigOperationDTO.builder()
            .repoName("repo")
            .branch("branch")
            .moveConfigOperationType(TemplateMoveConfigOperationType.INLINE_TO_REMOTE)
            .connectorRef("connector")
            .baseBranch("baseBranch")
            .commitMessage("Commit message")
            .isNewBranch(false)
            .filePath("filepath")
            .build();
    TemplateEntity updatedTemplateEntity = templateService.moveTemplateEntity(ACCOUNT_ID, ORG_IDENTIFIER,
        PROJ_IDENTIFIER, TEMPLATE_IDENTIFIER, TEMPLATE_VERSION_LABEL, moveConfigOperationDTO, templateEntity);
    assertThat(updatedTemplateEntity.getStoreType()).isEqualTo(StoreType.REMOTE);
    assertThat(updatedTemplateEntity.getRepo()).isEqualTo("repo");
    assertThat(updatedTemplateEntity.getConnectorRef()).isEqualTo("connector");
    assertThat(updatedTemplateEntity.getFilePath()).isEqualTo("filepath");
    assertThat(updatedTemplateEntity.getFallBackBranch()).isEqualTo("branch");
    assertThat(updatedTemplateEntity.getIdentifier()).isEqualTo(TEMPLATE_IDENTIFIER);
    assertThat(updatedTemplateEntity.getVersionLabel()).isEqualTo(TEMPLATE_VERSION_LABEL);
  }

  @Test()
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testCreateRemoteTemplateForNonGitEntityTemplate() throws IOException {
    TemplateEntity templateEntity1 = TemplateEntity.builder()
                                         .accountId(ACCOUNT_ID)
                                         .identifier(TEMPLATE_IDENTIFIER)
                                         .name(TEMPLATE_IDENTIFIER)
                                         .versionLabel(TEMPLATE_VERSION_LABEL)
                                         .templateEntityType(TemplateEntityType.CUSTOM_DEPLOYMENT_TEMPLATE)
                                         .templateScope(Scope.PROJECT)
                                         .yaml(yaml)
                                         .build();
    GitEntityInfo branchInfo = GitEntityInfo.builder().storeType(StoreType.REMOTE).build();
    setupGitContext(branchInfo);
    assertThatThrownBy(() -> templateService.create(templateEntity1, false, "", false))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage(String.format(
            "Remote template entity cannot be created for template type [CustomDeployment] on git simplification enabled in Account [%s]",
            ACCOUNT_ID));

    assertThatThrownBy(
        () -> templateService.create(templateEntity1.withOrgIdentifier(ORG_IDENTIFIER), false, "", false))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage(String.format(
            "Remote template entity cannot be created for template type [CustomDeployment] on git simplification enabled in Organisation [orgId] in Account [%s]",
            ACCOUNT_ID));

    assertThatThrownBy(()
                           -> templateService.create(
                               templateEntity1.withOrgIdentifier(ORG_IDENTIFIER).withProjectIdentifier(PROJ_IDENTIFIER),
                               false, "", false))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage(String.format(
            "Remote template entity cannot be created for template type [CustomDeployment] on git simplification enabled for Project [projId] in Organisation [orgId] in Account [%s]",
            ACCOUNT_ID));
  }

  @Test()
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testUpdateAndChangeTemplateType() throws IOException {
    TemplateEntity templateEntity1 = TemplateEntity.builder()
                                         .accountId(ACCOUNT_ID)
                                         .identifier("remoteTemplate")
                                         .name("remoteTemplate")
                                         .versionLabel(TEMPLATE_VERSION_LABEL)
                                         .templateEntityType(TemplateEntityType.STEP_TEMPLATE)
                                         .childType("shellScript")
                                         .templateScope(Scope.PROJECT)
                                         .yaml(yaml)
                                         .storeType(StoreType.REMOTE)
                                         .build();
    TemplateEntity template = templateService.create(templateEntity1, false, "", false);
    assertThatThrownBy(()
                           -> templateService.updateTemplateEntity(
                               template.withTemplateEntityType(TemplateEntityType.SECRET_MANAGER_TEMPLATE),
                               ChangeType.MODIFY, false, ""))
        .isInstanceOf(InvalidRequestException.class);
    /*
    TODO - CDS-72687 , Propogate the error message and it should not overriden and update message here "Template with
    identifier [%s] and versionLabel [%s] under Project[%s], Organization [%s] cannot update the template type, type is
    [%s]."

    TODO with same ticket (when child type is null) and Add message "Template with identifier [%s] and versionLabel [%s]
    under Project[%s], Organization [%s] cannot update the internal template type, type is [%s]"
    */
    assertThatThrownBy(
        () -> templateService.updateTemplateEntity(template.withChildType(null), ChangeType.MODIFY, false, ""))
        .isInstanceOf(InvalidRequestException.class);
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testUpdateTemplateEntityWithTemplateResponse() {
    templateService.create(entity, false, "", false);

    Optional<TemplateEntity> optionalTemplateEntity = templateService.get(
        ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, TEMPLATE_IDENTIFIER, TEMPLATE_VERSION_LABEL, false, false);
    assertThat(optionalTemplateEntity).isPresent();

    String description = "Updated Description";
    TemplateEntity updateTemplate = entity.withDescription(description);
    GitEntityInfo branchInfo = GitEntityInfo.builder().build();
    setupGitContext(branchInfo);
    templateService.updateTemplateEntity(updateTemplate, ChangeType.MODIFY, false, "",
        TemplateResponseDTO.builder()
            .gitDetails(EntityGitDetails.builder()
                            .commitId("commitId")
                            .objectId("objId")
                            .filePath("path")
                            .branch("branch")
                            .build())
            .build());
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testYamlValidationInRemoteTemplateGet() {
    String yaml = readFile("template.yaml");
    TemplateEntity templateEntity = TemplateEntity.builder()
                                        .accountId(ACCOUNT_ID)
                                        .orgIdentifier(ORG_IDENTIFIER)
                                        .projectIdentifier(PROJ_IDENTIFIER)
                                        .identifier(TEMPLATE_IDENTIFIER)
                                        .name(TEMPLATE_IDENTIFIER)
                                        .versionLabel(TEMPLATE_VERSION_LABEL)
                                        .storeType(StoreType.REMOTE)
                                        .templateEntityType(TemplateEntityType.MONITORED_SERVICE_TEMPLATE)
                                        .templateScope(Scope.PROJECT)
                                        .yaml(yaml)
                                        .build();
    doReturn(Optional.of(templateEntity))
        .when(templateServiceHelper)
        .getTemplate(anyString(), anyString(), anyString(), anyString(), anyString(), anyBoolean(), anyBoolean(),
            anyBoolean(), anyBoolean());

    assertThatThrownBy(()
                           -> templateService.get(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, TEMPLATE_IDENTIFIER,
                               "wrong_version", false, false, false))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining(
            "Error while retrieving template with identifier [template1] and versionLabel [wrong_version]");
  }

  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  public void testNGManagerSendsInvalidYamlResponse() throws IOException {
    Call<ResponseDTO<InputsValidationResponse>> ngManagerReconcileCall = mock(Call.class);
    doReturn(ngManagerReconcileCall)
        .when(ngManagerReconcileClient)
        .validateYaml(anyString(), anyString(), anyString(), any(NgManagerRefreshRequestDTO.class));
    doReturn(Response.success(ResponseDTO.newResponse(InputsValidationResponse.builder().isValid(false).build())))
        .when(ngManagerReconcileCall)
        .execute();

    String stageTemplateIdentifier = "template2";
    String stageYaml = readFile("service/stage-template-regular.yaml");
    TemplateEntity stageTemplate = entity.withYaml(stageYaml).withIdentifier(stageTemplateIdentifier);
    // Template creation should be allowed as we have removed inputs validations
    templateService.create(stageTemplate, false, "", false);
  }

  private void setupGitContext(GitEntityInfo branchInfo) {
    if (!GlobalContextManager.isAvailable()) {
      GlobalContextManager.set(new GlobalContext());
    }
    GlobalContextManager.upsertGlobalContextRecord(GitSyncBranchContext.builder().gitBranchInfo(branchInfo).build());
  }

  private void assertSavedTemplateEntity(TemplateEntity createdEntity, String templateIdentifier) {
    assertThat(createdEntity).isNotNull();
    assertThat(createdEntity.getAccountId()).isEqualTo(ACCOUNT_ID);
    assertThat(createdEntity.getOrgIdentifier()).isEqualTo(ORG_IDENTIFIER);
    assertThat(createdEntity.getProjectIdentifier()).isEqualTo(PROJ_IDENTIFIER);
    assertThat(createdEntity.getIdentifier()).isEqualTo(templateIdentifier);
  }

  @Test
  @Owner(developers = VIVEK_DIXIT)
  @Category(UnitTests.class)
  public void testApplyGitXSettingsIfApplicable() {
    templateService.applyGitXSettingsIfApplicable(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER);
    InOrder inOrder = inOrder(gitXSettingsHelper);
    inOrder.verify(gitXSettingsHelper).enforceGitExperienceIfApplicable(any(), any(), any());
    inOrder.verify(gitXSettingsHelper).setConnectorRefForRemoteEntity(any(), any(), any());
    inOrder.verify(gitXSettingsHelper).setDefaultStoreTypeForEntities(any(), any(), any(), any());
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void testEvaluateGovernancePoliciesTemplateWithFlagOff() {
    doReturn(false).when(pmsFeatureFlagService).isEnabled(ACCOUNT_ID, FeatureName.CDS_OPA_TEMPLATE_GOVERNANCE);
    GovernanceMetadata flagOffMetadata =
        templateService.validateGovernanceRules(TemplateEntity.builder().accountId("acc").build());
    assertThat(flagOffMetadata.getDeny()).isFalse();
    assertThat(flagOffMetadata.getMessage())
        .isEqualTo("FF: [CDS_OPA_TEMPLATE_GOVERNANCE] is disabled for account: [acc]");
  }

  @Test
  @Owner(developers = ROHITKARELIA)
  @Category(UnitTests.class)
  public void testFailOnMoveConfigForNotSupportedTemplates() {
    NGTemplateServiceImpl ngTemplateService = spy(templateService);
    TemplateEntity templateEntity = TemplateEntity.builder()
                                        .accountId(ACCOUNT_ID)
                                        .orgIdentifier(ORG_IDENTIFIER)
                                        .projectIdentifier(PROJ_IDENTIFIER)
                                        .identifier("template-movetogit")
                                        .name("templatemovetogit")
                                        .versionLabel(TEMPLATE_VERSION_LABEL)
                                        .templateScope(Scope.PROJECT)
                                        .templateEntityType(TemplateEntityType.SECRET_MANAGER_TEMPLATE)
                                        .yaml(yaml)
                                        .build();
    ngTemplateService.create(templateEntity, true, "", false);
    doReturn(templateEntity)
        .when(ngTemplateService)
        .moveTemplateEntity(any(), any(), any(), any(), any(), any(TemplateMoveConfigOperationDTO.class), any());
    TemplateMoveConfigRequestDTO moveConfigOperationDTO =
        TemplateMoveConfigRequestDTO.builder()
            .isNewBranch(false)
            .moveConfigOperationType(TemplateMoveConfigOperationType.INLINE_TO_REMOTE)
            .versionLabel(TEMPLATE_VERSION_LABEL)
            .build();
    assertThatThrownBy(()
                           -> ngTemplateService.moveTemplateStoreTypeConfig(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER,
                               "template-movetogit", moveConfigOperationDTO))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage(
            "Template with the given Identifier: template-movetogit and versionLabel version1 cannot be moved to Git as it is not a Git Supported Template Type");
  }

  @Test
  @Owner(developers = ROHITKARELIA)
  @Category(UnitTests.class)
  public void testUpdateGitDetails() {
    UpdateGitDetailsParams updateGitDetailsParams = UpdateGitDetailsParams.builder().filePath("filepath").build();

    assertThatThrownBy(()
                           -> templateService.updateGitDetails(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER,
                               "template-movetogit", "version1", updateGitDetailsParams))
        .isInstanceOf(EntityNotFoundException.class)
        .hasMessage(String.format(
            "Template not found for template identifier [template-movetogit] and version label [version1] in account %s, org orgId, project projId",
            ACCOUNT_ID));
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testExceptionCasesOnCRUDScenerios() {
    NGTemplateServiceImpl ngTemplateService = spy(templateService);
    TemplateEntity templateEntity = TemplateEntity.builder()
                                        .accountId(ACCOUNT_ID)
                                        .orgIdentifier(ORG_IDENTIFIER)
                                        .projectIdentifier(PROJ_IDENTIFIER)
                                        .identifier("template-movetogit1")
                                        .name("templatemovetogit")
                                        .versionLabel(TEMPLATE_VERSION_LABEL)
                                        .templateScope(Scope.PROJECT)
                                        .templateEntityType(TemplateEntityType.SECRET_MANAGER_TEMPLATE)
                                        .yaml(yaml)
                                        .build();
    doThrow(new DuplicateKeyException("msg")).when(ngTemplateService).saveTemplate(any(), any());

    assertThatThrownBy(() -> ngTemplateService.create(templateEntity, true, "", false))
        .isInstanceOf(DuplicateFieldException.class)
        .hasMessage(
            "Template [template-movetogit1] of versionLabel [version1] under Project[projId], Organization [orgId] already exists");

    doThrow(new ScmException(REVOKED_TOKEN)).when(ngTemplateService).saveTemplate(any(), any());

    assertThatThrownBy(() -> ngTemplateService.create(templateEntity, true, "", false))
        .isInstanceOf(ScmException.class);

    doThrow(new ScmException(REVOKED_TOKEN)).when(ngTemplateService).getActualComments(any(), any(), any(), any());

    assertThatThrownBy(() -> ngTemplateService.updateTemplateEntity(templateEntity, ChangeType.MODIFY, false, ""))
        .isInstanceOf(ScmException.class);

    doThrow(new DuplicateKeyException("msg")).when(ngTemplateService).getActualComments(any(), any(), any(), any());

    assertThatThrownBy(() -> ngTemplateService.updateTemplateEntity(templateEntity, ChangeType.MODIFY, false, ""))
        .isInstanceOf(DuplicateFieldException.class)
        .hasMessage(
            "Template [template-movetogit1] of versionLabel [version1] under Project[projId], Organization [orgId] already exists");

    doThrow(new ScmException("Message", REVOKED_TOKEN))
        .when(templateServiceHelper)
        .getTemplate(anyString(), anyString(), anyString(), anyString(), anyString(), anyBoolean(), anyBoolean(),
            anyBoolean(), anyBoolean());

    assertThatThrownBy(()
                           -> ngTemplateService.get(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, "template-movetogit1",
                               "version1", false, false, false))
        .isInstanceOf(ScmException.class);
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testUpdateGitFilePath() {
    TemplateEntity templateEntity = TemplateEntity.builder()
                                        .accountId(ACCOUNT_ID)
                                        .orgIdentifier(ORG_IDENTIFIER)
                                        .projectIdentifier(PROJ_IDENTIFIER)
                                        .identifier("template-movetogit1")
                                        .name("templatemovetogit")
                                        .versionLabel(TEMPLATE_VERSION_LABEL)
                                        .templateScope(Scope.PROJECT)
                                        .templateEntityType(TemplateEntityType.SECRET_MANAGER_TEMPLATE)
                                        .yaml(yaml)
                                        .filePath("fsd")
                                        .build();
    templateService.updateGitFilePath(templateEntity, ".harness/inputset/test");
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testDeleteAllTemplatesInAProjectlForOldGitSync() {
    when(gitSyncSdkService.isGitSyncEnabled(any(), any(), any())).thenReturn(true);
    templateService.create(entity, true, "", false);
    templateService.deleteAllTemplatesInAProject(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER);
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testImportTemplateFromRemote() {
    when(templateGitXService.isNewGitXEnabled(any(), any(), any())).thenReturn(true);
    when(templateGitXService.checkForFileUniquenessAndGetRepoURL(any(), any(), any(), any(), anyBoolean()))
        .thenReturn("repoUrl");
    when(templateGitXService.importTemplateFromRemote(any(), any(), any())).thenReturn(yaml);
    doNothing().when(templateGitXService).performImportFlowYamlValidations(any(), any(), any(), any(), any());
    TemplateImportRequestDTO templateImportRequestDTO = TemplateImportRequestDTO.builder()
                                                            .templateName(TEMPLATE_IDENTIFIER)
                                                            .templateDescription("des")
                                                            .templateVersion(TEMPLATE_VERSION_LABEL)
                                                            .build();
    templateService.importTemplateFromRemote(
        ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, TEMPLATE_IDENTIFIER, templateImportRequestDTO, false);
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testGetListOfRepos() {
    templateService.getListOfRepos(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, true);
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testPopulateSetupUsageAsync() {
    TemplateEntity templateEntity = TemplateEntity.builder()
                                        .accountId(ACCOUNT_ID)
                                        .orgIdentifier(ORG_IDENTIFIER)
                                        .projectIdentifier(PROJ_IDENTIFIER)
                                        .identifier("template-movetogit1")
                                        .name("templatemovetogit")
                                        .versionLabel(TEMPLATE_VERSION_LABEL)
                                        .templateScope(Scope.PROJECT)
                                        .templateEntityType(TemplateEntityType.SECRET_MANAGER_TEMPLATE)
                                        .yaml(yaml)
                                        .storeType(StoreType.REMOTE)
                                        .build();
    doNothing().when(templateAsyncSetupUsageService).populateAsyncSetupUsage(any());
    templateService.populateSetupUsageAsync(templateEntity);
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testDeleteTemplateHelperThrowsException() {
    TemplateEntity templateEntity = TemplateEntity.builder()
                                        .accountId(ACCOUNT_ID)
                                        .orgIdentifier(ORG_IDENTIFIER)
                                        .projectIdentifier(PROJ_IDENTIFIER)
                                        .identifier("template-movetogit1")
                                        .name("templatemovetogit")
                                        .versionLabel(TEMPLATE_VERSION_LABEL)
                                        .templateScope(Scope.PROJECT)
                                        .templateEntityType(TemplateEntityType.SECRET_MANAGER_TEMPLATE)
                                        .yaml(yaml)
                                        .isStableTemplate(true)
                                        .storeType(StoreType.REMOTE)
                                        .build();
    assertThatThrownBy(()
                           -> templateService.deleteSingleTemplateHelper(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER,
                               "template-movetogit1", templateEntity, null, false, "", false))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage(
            "Template with identifier [template-movetogit1] and versionLabel [version1], under Project[projId], Organization [orgId] is a stable template, thus cannot delete it.");

    doThrow(new ScmException("Message", REVOKED_TOKEN))
        .when(templateServiceHelper)
        .deleteTemplate(anyString(), anyString(), anyString(), anyString(), any(TemplateEntity.class), anyString(),
            anyString(), anyBoolean());

    assertThatThrownBy(()
                           -> templateService.deleteSingleTemplateHelper(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER,
                               "template-movetogit1", templateEntity, null, true, "", true))
        .isInstanceOf(ScmException.class)
        .hasMessage("Message");

    doThrow(new InvalidRequestException("Message"))
        .when(templateServiceHelper)
        .deleteTemplate(anyString(), anyString(), anyString(), anyString(), any(TemplateEntity.class), anyString(),
            anyString(), anyBoolean());

    assertThatThrownBy(()
                           -> templateService.deleteSingleTemplateHelper(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER,
                               "template-movetogit1", templateEntity, null, true, "", true))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage(
            "Error while deleting template with identifier [template-movetogit1] and versionLabel [version1]: Message");
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testMarkEntityInvalidForDeletedTemplate() {
    boolean markEntityInvalid = templateService.markEntityInvalid(
        ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, "ds", TEMPLATE_VERSION_LABEL, "INVALID_YAML");
    assertThat(markEntityInvalid).isFalse();
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testFullSyncTemplate() {
    TemplateReferenceProtoDTO templateReferenceProtoDTO =
        TemplateReferenceProtoDTO.newBuilder()
            .setIdentifier(StringValue.of("identifier"))
            .setAccountIdentifier(StringValue.of("accountIdentifier"))
            .setScope(ScopeProtoEnum.valueOf(Scope.PROJECT.toString()))
            .setOrgIdentifier(StringValue.of("orgIdentifier"))
            .setVersionLabel(StringValue.of("v1"))
            .build();
    EntityDetailProtoDTO entityDetailProtoDTO =
        EntityDetailProtoDTO.newBuilder().setTemplateRef(templateReferenceProtoDTO).build();
    assertThatThrownBy(() -> templateService.fullSyncTemplate(entityDetailProtoDTO))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage(
            "Error while saving template [identifier] of versionLabel [v1]: Template with identifier [accountIdentifier] and versionLabel [v1] under Project[], Organization [orgIdentifier] doesn't exist.");
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testListReferredEntities() {
    Call<ResponseDTO<PageResponse<EntitySetupUsageDTO>>> requestCall = mock(Call.class);
    MockedStatic<NGRestUtils> mockStatic = Mockito.mockStatic(NGRestUtils.class);
    PageResponse<EntitySetupUsageDTO> pageResponse = mock(PageResponse.class);
    when(NGRestUtils.getResponse(requestCall)).thenReturn(pageResponse);
    mockStatic.when(() -> NGRestUtils.getResponse(any())).thenReturn(pageResponse);
    templateService.listTemplateReferences(
        100, 25, ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, "identifier", "version", "", true);

    templateService.listTemplateReferences(
        100, 25, ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, "identifier", "version", "", false);
    mockStatic.close();
  }
}