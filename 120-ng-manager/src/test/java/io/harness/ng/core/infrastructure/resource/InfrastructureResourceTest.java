/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ng.core.infrastructure.resource;

import static io.harness.rule.OwnerRule.SOURABH;
import static io.harness.rule.OwnerRule.vivekveman;

import static java.util.List.of;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.accesscontrol.acl.api.AccessCheckResponseDTO;
import io.harness.accesscontrol.acl.api.AccessControlDTO;
import io.harness.accesscontrol.acl.api.ResourceScope;
import io.harness.accesscontrol.clients.AccessControlClient;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.category.element.UnitTests;
import io.harness.cdng.featureFlag.CDFeatureFlagHelper;
import io.harness.cdng.service.steps.helpers.serviceoverridesv2.validators.EnvironmentValidationHelper;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.core.infrastructure.dto.InfrastructureRequestDTO;
import io.harness.ng.core.infrastructure.dto.InfrastructureResponse;
import io.harness.ng.core.infrastructure.dto.InfrastructureResponseDTO;
import io.harness.ng.core.infrastructure.entity.InfrastructureEntity;
import io.harness.ng.core.infrastructure.entity.InfrastructureEntity.InfrastructureEntityKeys;
import io.harness.ng.core.infrastructure.services.InfrastructureEntityService;
import io.harness.ng.core.infrastructure.services.impl.InfrastructureYamlSchemaHelper;
import io.harness.ng.core.utils.OrgAndProjectValidationHelper;
import io.harness.rule.Owner;
import io.harness.utils.NGFeatureFlagHelperService;

import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import java.io.IOException;
import java.net.URL;
import java.util.Arrays;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

@OwnedBy(HarnessTeam.CDC)
public class InfrastructureResourceTest extends CategoryTest {
  @InjectMocks InfrastructureResource infrastructureResource;
  @Mock NGFeatureFlagHelperService featureFlagHelperService;
  @Mock InfrastructureEntityService infrastructureEntityService;
  @Mock InfrastructureYamlSchemaHelper entityYamlSchemaHelper;
  @Mock OrgAndProjectValidationHelper orgAndProjectValidationHelper;
  @Mock EnvironmentValidationHelper environmentValidationHelper;
  @Mock AccessControlClient accessControlClient;
  @Mock private CDFeatureFlagHelper cdFeatureFlagHelper;

  private final String ACCOUNT_ID = "account_id";
  private final String ORG_IDENTIFIER = "orgId";
  private final String PROJ_IDENTIFIER = "projId";
  private final String IDENTIFIER = "identifier";
  private final String ENV_IDENTIFIER = "env_identifier";
  private final String NAME = "name";
  private final ClassLoader classLoader = this.getClass().getClassLoader();
  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = vivekveman)
  @Category(UnitTests.class)
  public void testCreateServiceWithSchemaValidationFlagOn() throws IOException {
    when(featureFlagHelperService.isEnabled(ACCOUNT_ID, FeatureName.DISABLE_CDS_SERVICE_ENV_SCHEMA_VALIDATION))
        .thenReturn(false);

    when(featureFlagHelperService.isEnabled(ACCOUNT_ID, FeatureName.NG_SVC_ENV_REDESIGN)).thenReturn(true);

    String yaml = readFile("InfraYamlWithIncorrectDeploymentType.yaml");

    InfrastructureRequestDTO environmentRequestDTO = InfrastructureRequestDTO.builder()
                                                         .identifier(IDENTIFIER)
                                                         .orgIdentifier(ORG_IDENTIFIER)
                                                         .projectIdentifier(PROJ_IDENTIFIER)
                                                         .name(NAME)
                                                         .yaml(yaml)
                                                         .build();

    assertThatThrownBy(() -> infrastructureResource.create(ACCOUNT_ID, environmentRequestDTO, null))
        .isInstanceOf(InvalidRequestException.class);

    verify(entityYamlSchemaHelper, times(1)).validateSchema(ACCOUNT_ID, environmentRequestDTO.getYaml());
  }

  @Test
  @Owner(developers = vivekveman)
  @Category(UnitTests.class)
  public void testUpdateServiceWithSchemaValidationFlagOn() throws IOException {
    when(featureFlagHelperService.isEnabled(ACCOUNT_ID, FeatureName.DISABLE_CDS_SERVICE_ENV_SCHEMA_VALIDATION))
        .thenReturn(false);

    when(featureFlagHelperService.isEnabled(ACCOUNT_ID, FeatureName.NG_SVC_ENV_REDESIGN)).thenReturn(true);

    String yaml = readFile("InfraYamlWithIncorrectDeploymentType.yaml");

    InfrastructureRequestDTO environmentRequestDTO = InfrastructureRequestDTO.builder()
                                                         .identifier(IDENTIFIER)
                                                         .orgIdentifier(ORG_IDENTIFIER)
                                                         .projectIdentifier(PROJ_IDENTIFIER)
                                                         .name(NAME)
                                                         .yaml(yaml)
                                                         .build();

    assertThatThrownBy(() -> infrastructureResource.update(ACCOUNT_ID, environmentRequestDTO, null))
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

    String yaml = readFile("InfraYamlWithIncorrectDeploymentType.yaml");

    InfrastructureRequestDTO infrastructureRequestDTO = InfrastructureRequestDTO.builder()
                                                            .identifier(IDENTIFIER)
                                                            .orgIdentifier(ORG_IDENTIFIER)
                                                            .projectIdentifier(PROJ_IDENTIFIER)
                                                            .name(NAME)
                                                            .yaml(yaml)
                                                            .build();

    assertThatThrownBy(() -> infrastructureResource.upsert(ACCOUNT_ID, infrastructureRequestDTO))
        .isInstanceOf(InvalidRequestException.class);

    verify(entityYamlSchemaHelper, times(1)).validateSchema(ACCOUNT_ID, infrastructureRequestDTO.getYaml());
  }

  @Test
  @Owner(developers = SOURABH)
  @Category(UnitTests.class)
  public void testListAccess() throws IOException {
    Pageable pageable = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, InfrastructureEntityKeys.createdAt));
    InfrastructureEntity infra = InfrastructureEntity.builder()
                                     .identifier(IDENTIFIER)
                                     .accountId(ACCOUNT_ID)
                                     .projectIdentifier(PROJ_IDENTIFIER)
                                     .orgIdentifier(ORG_IDENTIFIER)
                                     .envIdentifier(ENV_IDENTIFIER)
                                     .yaml("yaml")
                                     .build();
    Page<InfrastructureEntity> infraEntities = new PageImpl<>(Arrays.asList(infra), pageable, 1);
    when(cdFeatureFlagHelper.isEnabled(ACCOUNT_ID, FeatureName.DISABLE_CDS_SERVICE_ENV_SCHEMA_VALIDATION))
        .thenReturn(false);

    when(cdFeatureFlagHelper.isEnabled(ACCOUNT_ID, FeatureName.CDS_SCOPE_INFRA_TO_SERVICES)).thenReturn(false);
    when(infrastructureEntityService.list(any(), any())).thenReturn(infraEntities);
    doNothing().when(accessControlClient).checkForAccessOrThrow(any(), any(), any());
    doReturn(AccessCheckResponseDTO.builder()
                 .accessControlList(of(AccessControlDTO.builder()
                                           .permitted(true)
                                           .resourceIdentifier(ENV_IDENTIFIER)
                                           .resourceScope(ResourceScope.builder()
                                                              .accountIdentifier(ACCOUNT_ID)
                                                              .orgIdentifier(ORG_IDENTIFIER)
                                                              .projectIdentifier(PROJ_IDENTIFIER)
                                                              .build())
                                           .build()))
                 .build())
        .when(accessControlClient)
        .checkForAccess(any());

    ResponseDTO<List<InfrastructureResponse>> responseDTO = infrastructureResource.listAccessInfrastructures(
        0, 10, ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, null, null, null, null, null, null, null, null);

    InfrastructureResponseDTO infrastructure = responseDTO.getData().get(0).getInfrastructure();
    assertThat(infrastructure.getAccountId()).isEqualTo(ACCOUNT_ID);
    assertThat(infrastructure.getEnvironmentRef()).isEqualTo(ENV_IDENTIFIER);
    assertThat(infrastructure.getIdentifier()).isEqualTo(IDENTIFIER);
  }

  @Test
  @Owner(developers = SOURABH)
  @Category(UnitTests.class)
  public void testListAccessWithMultipleInfrasWithAccessControlCheck() throws IOException {
    Pageable pageable = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, InfrastructureEntityKeys.createdAt));
    InfrastructureEntity infra_project = InfrastructureEntity.builder()
                                             .identifier("infra1")
                                             .accountId(ACCOUNT_ID)
                                             .orgIdentifier(ORG_IDENTIFIER)
                                             .projectIdentifier(PROJ_IDENTIFIER)
                                             .envIdentifier(ENV_IDENTIFIER)
                                             .yaml("yaml")
                                             .build();

    InfrastructureEntity infra_account = InfrastructureEntity.builder()
                                             .identifier("infra2")
                                             .accountId(ACCOUNT_ID)
                                             .envIdentifier("env")
                                             .yaml("yaml")
                                             .build();

    InfrastructureEntity infra_org = InfrastructureEntity.builder()
                                         .identifier("infra3")
                                         .accountId(ACCOUNT_ID)
                                         .orgIdentifier(ORG_IDENTIFIER)
                                         .envIdentifier("env")
                                         .yaml("yaml")
                                         .build();

    Page<InfrastructureEntity> infraEntities =
        new PageImpl<>(Arrays.asList(infra_project, infra_account, infra_org), pageable, 1);
    when(cdFeatureFlagHelper.isEnabled(ACCOUNT_ID, FeatureName.DISABLE_CDS_SERVICE_ENV_SCHEMA_VALIDATION))
        .thenReturn(false);

    when(cdFeatureFlagHelper.isEnabled(ACCOUNT_ID, FeatureName.CDS_SCOPE_INFRA_TO_SERVICES)).thenReturn(false);
    when(infrastructureEntityService.list(any(), any())).thenReturn(infraEntities);
    doNothing().when(accessControlClient).checkForAccessOrThrow(any(), any(), any());
    doReturn(
        AccessCheckResponseDTO.builder()
            .accessControlList(Arrays.asList(AccessControlDTO.builder()
                                                 .permitted(false)
                                                 .resourceIdentifier(ENV_IDENTIFIER)
                                                 .resourceScope(ResourceScope.builder()
                                                                    .accountIdentifier(ACCOUNT_ID)
                                                                    .orgIdentifier(ORG_IDENTIFIER)
                                                                    .projectIdentifier(PROJ_IDENTIFIER)
                                                                    .build())
                                                 .build(),
                AccessControlDTO.builder()
                    .permitted(true)
                    .resourceIdentifier("env")
                    .resourceScope(
                        ResourceScope.builder().accountIdentifier(ACCOUNT_ID).orgIdentifier(ORG_IDENTIFIER).build())
                    .build(),
                AccessControlDTO.builder()
                    .permitted(true)
                    .resourceIdentifier("env")
                    .resourceScope(ResourceScope.builder().accountIdentifier(ACCOUNT_ID).build())
                    .build()))
            .build())
        .when(accessControlClient)
        .checkForAccess(any());

    ResponseDTO<List<InfrastructureResponse>> responseDTO = infrastructureResource.listAccessInfrastructures(
        0, 10, ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, null, null, null, null, null, null, null, null);

    assertThat(responseDTO.getData()).hasSize(2);
    InfrastructureResponseDTO infrastructure1 = responseDTO.getData().get(0).getInfrastructure();
    InfrastructureResponseDTO infrastructure2 = responseDTO.getData().get(1).getInfrastructure();

    assertThat(infrastructure1.getAccountId()).isEqualTo(ACCOUNT_ID);
    assertThat(infrastructure1.getOrgIdentifier()).isEqualTo(null);
    assertThat(infrastructure1.getEnvironmentRef()).isEqualTo("env");
    assertThat(infrastructure1.getIdentifier()).isEqualTo("infra2");

    assertThat(infrastructure2.getAccountId()).isEqualTo(ACCOUNT_ID);
    assertThat(infrastructure2.getOrgIdentifier()).isEqualTo(ORG_IDENTIFIER);
    assertThat(infrastructure2.getEnvironmentRef()).isEqualTo("env");
    assertThat(infrastructure2.getIdentifier()).isEqualTo("infra3");
  }

  private String readFile(String fileName) throws IOException {
    final URL testFile = classLoader.getResource(fileName);
    return Resources.toString(testFile, Charsets.UTF_8);
  }
}
