/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.environment.resources;

import static io.harness.accesscontrol.principals.PrincipalType.USER;
import static io.harness.rbac.CDNGRbacPermissions.ENVIRONMENT_UPDATE_PERMISSION;
import static io.harness.rule.OwnerRule.TATHAGAT;
import static io.harness.rule.OwnerRule.vivekveman;

import static javax.ws.rs.core.HttpHeaders.IF_MATCH;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.accesscontrol.NGAccessDeniedException;
import io.harness.accesscontrol.acl.api.AccessCheckResponseDTO;
import io.harness.accesscontrol.acl.api.AccessControlDTO;
import io.harness.accesscontrol.acl.api.AccessControlDTO.AccessControlDTOBuilder;
import io.harness.accesscontrol.acl.api.Principal;
import io.harness.accesscontrol.acl.api.ResourceScope;
import io.harness.accesscontrol.clients.AccessControlClient;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.category.element.UnitTests;
import io.harness.cdng.service.steps.helpers.serviceoverridesv2.validators.EnvironmentValidationHelper;
import io.harness.cdng.service.steps.helpers.serviceoverridesv2.validators.ServiceEntityValidationHelper;
import io.harness.exception.InvalidRequestException;
import io.harness.gitsync.interceptor.GitEntityCreateInfoDTO;
import io.harness.gitsync.interceptor.GitEntityFindInfoDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.core.environment.beans.Environment;
import io.harness.ng.core.environment.beans.EnvironmentType;
import io.harness.ng.core.environment.dto.EnvironmentRequestDTO;
import io.harness.ng.core.environment.dto.EnvironmentResponse;
import io.harness.ng.core.environment.services.EnvironmentService;
import io.harness.ng.core.environment.services.impl.EnvironmentEntityYamlSchemaHelper;
import io.harness.ng.core.serviceoverride.beans.NGServiceOverridesEntity;
import io.harness.ng.core.serviceoverride.beans.ServiceOverrideRequestDTO;
import io.harness.ng.core.serviceoverride.beans.ServiceOverrideResponseDTO;
import io.harness.ng.core.serviceoverride.services.ServiceOverrideService;
import io.harness.ng.core.serviceoverrides.resources.ServiceOverridesResource;
import io.harness.ng.core.serviceoverridev2.beans.ServiceOverrideRequestDTOV2;
import io.harness.ng.core.serviceoverridev2.beans.ServiceOverridesResponseDTOV2;
import io.harness.ng.core.serviceoverridev2.beans.ServiceOverridesSpec;
import io.harness.ng.core.serviceoverridev2.beans.ServiceOverridesType;
import io.harness.ng.core.utils.OrgAndProjectValidationHelper;
import io.harness.ngsettings.SettingValueType;
import io.harness.ngsettings.client.remote.NGSettingsClient;
import io.harness.ngsettings.dto.SettingValueResponseDTO;
import io.harness.pms.rbac.NGResourceType;
import io.harness.rule.Owner;
import io.harness.utils.NGFeatureFlagHelperService;
import io.harness.yaml.core.variables.NGVariable;

import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import retrofit2.Call;
import retrofit2.Response;

@OwnedBy(HarnessTeam.CDC)
public class EnvironmentResourceV2Test extends CategoryTest {
  @InjectMocks EnvironmentResourceV2 environmentResourceV2;
  @Mock NGFeatureFlagHelperService featureFlagHelperService;
  @Mock EnvironmentEntityYamlSchemaHelper entityYamlSchemaHelper;
  @Mock AccessControlClient accessControlClient;
  @Mock EnvironmentService environmentService;
  @Mock ServiceOverrideService serviceOverrideService;
  @Mock ServiceEntityValidationHelper serviceEntityValidationHelper;
  @Mock OrgAndProjectValidationHelper orgAndProjectValidationHelper;
  @Mock EnvironmentValidationHelper environmentValidationHelper;

  @Mock private NGSettingsClient ngSettingsClient;
  @Mock private Call<ResponseDTO<SettingValueResponseDTO>> request;
  @Mock private ServiceOverridesResource serviceOverridesResource;

  private static final String ACCOUNT_ID = "account_id";
  private static final String ORG_IDENTIFIER = "orgId";
  private static final String PROJ_IDENTIFIER = "projId";
  private static final String IDENTIFIER = "identifier";
  private static final String NAME = "name";

  private static final String ENV_IDENTIFIER = "envId";
  private static final String SVC_IDENTIFIER = "svcId";

  private final String OVERRIDE_YAML =
      "serviceOverrides:\n  environmentRef: envId\n  serviceRef: svcId\n  variables:\n    - name: var1\n      type: String\n      value: val1\n";

  private static final Environment entity = Environment.builder()
                                                .identifier("id")
                                                .projectIdentifier("projectId")
                                                .orgIdentifier("orgId")
                                                .accountId("accountId")
                                                .type(EnvironmentType.PreProduction)
                                                .build();
  private static final ServiceOverridesResponseDTOV2 OVERRIDE_RESPONSE =
      ServiceOverridesResponseDTOV2.builder()
          .identifier("OverrideId")
          .environmentRef(ENV_IDENTIFIER)
          .type(ServiceOverridesType.ENV_SERVICE_OVERRIDE)
          .spec(ServiceOverridesSpec.builder().build())
          .build();

  private final ClassLoader classLoader = this.getClass().getClassLoader();

  @Before
  public void setup() throws IOException {
    MockitoAnnotations.initMocks(this);
    doReturn(ResponseDTO.newResponse(OVERRIDE_RESPONSE))
        .when(serviceOverridesResource)
        .create(anyString(), any(ServiceOverrideRequestDTOV2.class));
    doReturn(ResponseDTO.newResponse(OVERRIDE_RESPONSE))
        .when(serviceOverridesResource)
        .update(anyString(), any(ServiceOverrideRequestDTOV2.class));
  }

  @Test
  @Owner(developers = vivekveman)
  @Category(UnitTests.class)
  public void testCreateEnvironmentWithSchemaValidationFlagOn() throws IOException {
    when(featureFlagHelperService.isEnabled(ACCOUNT_ID, FeatureName.DISABLE_CDS_SERVICE_ENV_SCHEMA_VALIDATION))
        .thenReturn(false);

    when(featureFlagHelperService.isEnabled(ACCOUNT_ID, FeatureName.NG_SVC_ENV_REDESIGN)).thenReturn(true);

    String yaml = readFile("ManifestYamlWithoutSpec.yaml");

    EnvironmentRequestDTO environmentRequestDTO = EnvironmentRequestDTO.builder()
                                                      .identifier(IDENTIFIER)
                                                      .orgIdentifier(ORG_IDENTIFIER)
                                                      .projectIdentifier(PROJ_IDENTIFIER)
                                                      .name(NAME)
                                                      .yaml(yaml)
                                                      .type(EnvironmentType.PreProduction)
                                                      .build();

    assertThatThrownBy(
        () -> environmentResourceV2.create(ACCOUNT_ID, environmentRequestDTO, GitEntityCreateInfoDTO.builder().build()))
        .isInstanceOf(InvalidRequestException.class);

    verify(entityYamlSchemaHelper, times(1)).validateSchema(ACCOUNT_ID, environmentRequestDTO.getYaml());
  }

  @Test
  @Owner(developers = vivekveman)
  @Category(UnitTests.class)
  public void testUpdateEnvironmentWithSchemaValidationFlagOn() throws IOException {
    when(featureFlagHelperService.isEnabled(ACCOUNT_ID, FeatureName.DISABLE_CDS_SERVICE_ENV_SCHEMA_VALIDATION))
        .thenReturn(false);

    when(featureFlagHelperService.isEnabled(ACCOUNT_ID, FeatureName.NG_SVC_ENV_REDESIGN)).thenReturn(true);

    String yaml = readFile("ManifestYamlWithoutSpec.yaml");

    EnvironmentRequestDTO environmentRequestDTO = EnvironmentRequestDTO.builder()
                                                      .identifier(IDENTIFIER)
                                                      .orgIdentifier(ORG_IDENTIFIER)
                                                      .projectIdentifier(PROJ_IDENTIFIER)
                                                      .name(NAME)
                                                      .yaml(yaml)
                                                      .type(EnvironmentType.PreProduction)
                                                      .build();
    List<AccessControlDTO> accessControlDTOS = new ArrayList<>();

    AccessControlDTOBuilder accessControlDTOBuilder = AccessControlDTO.builder()
                                                          .resourceType(NGResourceType.ENVIRONMENT)
                                                          .permission(ENVIRONMENT_UPDATE_PERMISSION)
                                                          .resourceScope(ResourceScope.builder()
                                                                             .accountIdentifier(ACCOUNT_ID)
                                                                             .orgIdentifier(ORG_IDENTIFIER)
                                                                             .projectIdentifier(PROJ_IDENTIFIER)
                                                                             .build());

    accessControlDTOS.add(accessControlDTOBuilder.permitted(true).resourceIdentifier("identifier").build());
    accessControlDTOS.add(accessControlDTOBuilder.permitted(true).resourceIdentifier("identifier").build());

    AccessCheckResponseDTO accessCheckResponseDTO =
        AccessCheckResponseDTO.builder()
            .principal(Principal.builder().principalIdentifier("id").principalType(USER).build())
            .accessControlList(accessControlDTOS)
            .build();

    doReturn(accessCheckResponseDTO).when(accessControlClient).checkForAccessOrThrow(anyList());
    assertThatThrownBy(() -> environmentResourceV2.update(IF_MATCH, ACCOUNT_ID, environmentRequestDTO))
        .isInstanceOf(InvalidRequestException.class);

    verify(entityYamlSchemaHelper, times(1)).validateSchema(ACCOUNT_ID, environmentRequestDTO.getYaml());
  }

  @Test
  @Owner(developers = vivekveman)
  @Category(UnitTests.class)
  public void testUpsertServiceWithSchemaValidationFlagOn() throws IOException {
    when(featureFlagHelperService.isEnabled(ACCOUNT_ID, FeatureName.DISABLE_CDS_SERVICE_ENV_SCHEMA_VALIDATION))
        .thenReturn(false);

    when(featureFlagHelperService.isEnabled(ACCOUNT_ID, FeatureName.NG_SVC_ENV_REDESIGN)).thenReturn(true);

    String yaml = readFile("ManifestYamlWithoutSpec.yaml");

    EnvironmentRequestDTO environmentRequestDTO = EnvironmentRequestDTO.builder()
                                                      .identifier(IDENTIFIER)
                                                      .orgIdentifier(ORG_IDENTIFIER)
                                                      .projectIdentifier(PROJ_IDENTIFIER)
                                                      .name(NAME)
                                                      .yaml(yaml)
                                                      .type(EnvironmentType.PreProduction)
                                                      .build();
    List<AccessControlDTO> accessControlDTOS = new ArrayList<>();

    AccessControlDTOBuilder accessControlDTOBuilder = AccessControlDTO.builder()
                                                          .resourceType(NGResourceType.ENVIRONMENT)
                                                          .permission(ENVIRONMENT_UPDATE_PERMISSION)
                                                          .resourceScope(ResourceScope.builder()
                                                                             .accountIdentifier(ACCOUNT_ID)
                                                                             .orgIdentifier(ORG_IDENTIFIER)
                                                                             .projectIdentifier(PROJ_IDENTIFIER)
                                                                             .build());

    accessControlDTOS.add(accessControlDTOBuilder.permitted(true).resourceIdentifier("identifier").build());
    accessControlDTOS.add(accessControlDTOBuilder.permitted(true).resourceIdentifier("identifier").build());

    AccessCheckResponseDTO accessCheckResponseDTO =
        AccessCheckResponseDTO.builder()
            .principal(Principal.builder().principalIdentifier("id").principalType(USER).build())
            .accessControlList(accessControlDTOS)
            .build();

    doReturn(accessCheckResponseDTO).when(accessControlClient).checkForAccessOrThrow(anyList());

    assertThatThrownBy(() -> environmentResourceV2.upsert(IF_MATCH, ACCOUNT_ID, environmentRequestDTO))
        .isInstanceOf(InvalidRequestException.class);

    verify(entityYamlSchemaHelper, times(1)).validateSchema(ACCOUNT_ID, environmentRequestDTO.getYaml());
  }
  @Test
  @Owner(developers = vivekveman)
  @Category(UnitTests.class)
  public void testUpdatEnvironmentForUserWithPreProdTypeAccess() throws IOException {
    String yaml = readFile("ManifestYamlWithoutSpec.yaml");

    EnvironmentRequestDTO environmentRequestDTO = EnvironmentRequestDTO.builder()
                                                      .identifier("id")
                                                      .orgIdentifier(ORG_IDENTIFIER)
                                                      .projectIdentifier(PROJ_IDENTIFIER)
                                                      .name(NAME)
                                                      .yaml(yaml)
                                                      .type(EnvironmentType.PreProduction)
                                                      .build();
    List<AccessControlDTO> accessControlDTOS = new ArrayList<>();

    AccessControlDTOBuilder accessControlDTOBuilder = AccessControlDTO.builder()
                                                          .resourceType(NGResourceType.ENVIRONMENT)
                                                          .permission(ENVIRONMENT_UPDATE_PERMISSION)
                                                          .resourceScope(ResourceScope.builder()
                                                                             .accountIdentifier(ACCOUNT_ID)
                                                                             .orgIdentifier(ORG_IDENTIFIER)
                                                                             .projectIdentifier(PROJ_IDENTIFIER)
                                                                             .build());

    accessControlDTOS.add(accessControlDTOBuilder.permitted(false).resourceIdentifier("id").build());
    accessControlDTOS.add(accessControlDTOBuilder.permitted(false).resourceIdentifier("id").build());

    AccessCheckResponseDTO accessCheckResponseDTO =
        AccessCheckResponseDTO.builder()
            .principal(Principal.builder().principalIdentifier("id").principalType(USER).build())
            .accessControlList(accessControlDTOS)
            .build();

    doReturn(accessCheckResponseDTO).when(accessControlClient).checkForAccessOrThrow(anyList());

    assertThatThrownBy(() -> environmentResourceV2.upsert(IF_MATCH, ACCOUNT_ID, environmentRequestDTO))
        .isInstanceOf(NGAccessDeniedException.class)
        .hasMessage("Missing permission core_environment_edit on ENVIRONMENT with identifier id");
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testGet() {
    when(environmentService.get(any(), any(), any(), any(), eq(false))).thenReturn(Optional.of(entity));
    when(accessControlClient.checkForAccessOrThrow(any()))
        .thenReturn(AccessCheckResponseDTO.builder()
                        .accessControlList(Arrays.asList(AccessControlDTO.builder().permitted(true).build()))
                        .build());
    ResponseDTO<EnvironmentResponse> environmentResponseResponseDTO = environmentResourceV2.get(IDENTIFIER, ACCOUNT_ID,
        ORG_IDENTIFIER, PROJ_IDENTIFIER, false, GitEntityFindInfoDTO.builder().build(), "false", false);
    assertThat(environmentResponseResponseDTO.getEntityTag()).isNull();
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testUpsertServiceOverrideCreate() throws IOException {
    ServiceOverrideRequestDTO requestDTO = ServiceOverrideRequestDTO.builder()
                                               .orgIdentifier(ORG_IDENTIFIER)
                                               .projectIdentifier(PROJ_IDENTIFIER)
                                               .environmentIdentifier(ENV_IDENTIFIER)
                                               .serviceIdentifier(SVC_IDENTIFIER)
                                               .yaml(OVERRIDE_YAML)
                                               .build();
    mockedReturnOverrideV2EnabledTrue();

    ResponseDTO<ServiceOverrideResponseDTO> serviceOverrideResponseDTOResponseDTO =
        environmentResourceV2.upsertServiceOverride(ACCOUNT_ID, requestDTO);

    ServiceOverrideResponseDTO serviceOverrideResponseDTO = serviceOverrideResponseDTOResponseDTO.getData();
    assertThat(serviceOverrideResponseDTO).isNotNull();
    assertThat(serviceOverrideResponseDTO.getYaml()).isEqualTo(OVERRIDE_YAML);

    ArgumentCaptor<ServiceOverrideRequestDTOV2> requestDTOV2Captor =
        ArgumentCaptor.forClass(ServiceOverrideRequestDTOV2.class);
    verify(serviceOverridesResource, times(1)).create(eq(ACCOUNT_ID), requestDTOV2Captor.capture());

    assertRequestDTOV2(requestDTOV2Captor.getValue());
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testUpsertServiceOverrideUpdate() throws IOException {
    ServiceOverrideRequestDTO requestDTO = ServiceOverrideRequestDTO.builder()
                                               .orgIdentifier(ORG_IDENTIFIER)
                                               .projectIdentifier(PROJ_IDENTIFIER)
                                               .environmentIdentifier(ENV_IDENTIFIER)
                                               .serviceIdentifier(SVC_IDENTIFIER)
                                               .yaml(OVERRIDE_YAML)
                                               .build();
    mockedReturnOverrideV2EnabledTrue();
    doReturn(Optional.of(NGServiceOverridesEntity.builder().build()))
        .when(serviceOverrideService)
        .getForV1AndV2(any(), any(), any(), any(), any());

    ResponseDTO<ServiceOverrideResponseDTO> serviceOverrideResponseDTOResponseDTO =
        environmentResourceV2.upsertServiceOverride(ACCOUNT_ID, requestDTO);

    ServiceOverrideResponseDTO serviceOverrideResponseDTO = serviceOverrideResponseDTOResponseDTO.getData();
    assertThat(serviceOverrideResponseDTO).isNotNull();
    assertThat(serviceOverrideResponseDTO.getYaml()).isEqualTo(OVERRIDE_YAML);

    ArgumentCaptor<ServiceOverrideRequestDTOV2> requestDTOV2Captor =
        ArgumentCaptor.forClass(ServiceOverrideRequestDTOV2.class);
    verify(serviceOverridesResource, times(1)).update(eq(ACCOUNT_ID), requestDTOV2Captor.capture());

    assertRequestDTOV2(requestDTOV2Captor.getValue());
  }

  private void assertRequestDTOV2(ServiceOverrideRequestDTOV2 requestDTOV2) {
    assertThat(requestDTOV2.isV1Api()).isTrue();
    assertThat(requestDTOV2.getYamlInternal())
        .isEqualTo(
            "serviceOverrides:\n  environmentRef: envId\n  serviceRef: svcId\n  variables:\n    - name: var1\n      type: String\n      value: val1\n");
    assertThat(requestDTOV2.getOrgIdentifier()).isEqualTo(ORG_IDENTIFIER);
    assertThat(requestDTOV2.getProjectIdentifier()).isEqualTo(PROJ_IDENTIFIER);
    assertThat(requestDTOV2.getEnvironmentRef()).isEqualTo(ENV_IDENTIFIER);
    assertThat(requestDTOV2.getServiceRef()).isEqualTo(SVC_IDENTIFIER);

    assertThat(requestDTOV2.getType()).isEqualTo(ServiceOverridesType.ENV_SERVICE_OVERRIDE);
    assertThat(requestDTOV2.getSpec()).isNotNull();

    assertThat(requestDTOV2.getSpec().getVariables()).hasSize(1);
    assertThat(requestDTOV2.getSpec().getVariables().stream().map(NGVariable::getName).collect(Collectors.toList()))
        .containsExactlyInAnyOrder("var1");
  }

  private void mockedReturnOverrideV2EnabledTrue() throws IOException {
    SettingValueResponseDTO settingValueResponseDTO =
        SettingValueResponseDTO.builder().value("true").valueType(SettingValueType.BOOLEAN).build();
    doReturn(request).when(ngSettingsClient).getSetting(anyString(), anyString(), anyString(), anyString());
    doReturn(Response.success(ResponseDTO.newResponse(settingValueResponseDTO))).when(request).execute();

    doReturn(true).when(featureFlagHelperService).isEnabled(ACCOUNT_ID, FeatureName.CDS_SERVICE_OVERRIDES_2_0);
  }

  private String readFile(String fileName) throws IOException {
    final URL testFile = classLoader.getResource(fileName);
    return Resources.toString(testFile, Charsets.UTF_8);
  }
}
