/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.environment.resources;

import static io.harness.accesscontrol.principals.PrincipalType.USER;
import static io.harness.rbac.CDNGRbacPermissions.ENVIRONMENT_UPDATE_PERMISSION;
import static io.harness.rule.OwnerRule.vivekveman;

import static javax.ws.rs.core.HttpHeaders.IF_MATCH;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
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
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.environment.beans.EnvironmentType;
import io.harness.ng.core.environment.dto.EnvironmentRequestDTO;
import io.harness.pms.rbac.NGResourceType;
import io.harness.rule.Owner;
import io.harness.utils.NGFeatureFlagHelperService;

import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.CDC)
public class EnvironmentResourceV2Test extends CategoryTest {
  @InjectMocks EnvironmentResourceV2 environmentResourceV2;
  @Mock NGFeatureFlagHelperService featureFlagHelperService;
  @Mock EnvironmentEntityYamlSchemaHelper entityYamlSchemaHelper;
  @Mock AccessControlClient accessControlClient;
  private final String ACCOUNT_ID = "account_id";
  private final String ORG_IDENTIFIER = "orgId";
  private final String PROJ_IDENTIFIER = "projId";
  private final String IDENTIFIER = "identifier";
  private final String NAME = "name";
  private final ClassLoader classLoader = this.getClass().getClassLoader();
  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
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

    assertThatThrownBy(() -> environmentResourceV2.create(ACCOUNT_ID, environmentRequestDTO))
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

  private String readFile(String fileName) throws IOException {
    final URL testFile = classLoader.getResource(fileName);
    return Resources.toString(testFile, Charsets.UTF_8);
  }
}
