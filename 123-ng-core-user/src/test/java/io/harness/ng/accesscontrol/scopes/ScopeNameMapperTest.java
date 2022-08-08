/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ng.accesscontrol.scopes;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.rule.OwnerRule.NAMANG;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.MockitoAnnotations.initMocks;

import io.harness.CategoryTest;
import io.harness.accesscontrol.scopes.ScopeDTO;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.entities.Organization;
import io.harness.ng.core.entities.Project;
import io.harness.ng.core.services.OrganizationService;
import io.harness.ng.core.services.ProjectService;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import java.io.IOException;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;

@OwnedBy(PL)
public class ScopeNameMapperTest extends CategoryTest {
  @Mock private OrganizationService organizationService;
  @Mock private ProjectService projectService;
  @Spy @Inject @InjectMocks private ScopeNameMapper scopeNameMapper;

  private static final String ACCOUNT_IDENTIFIER = "A1";
  private static final String ORG_IDENTIFIER = "O1";
  private static final String PROJECT_IDENTIFIER = "P1";
  private static final String ORG_NAME = "O1_Name";
  private static final String PROJECT_NAME = "P1_Name";
  private static final Optional<Organization> organizationResponse =
      Optional.of(Organization.builder().name(ORG_NAME).identifier(ORG_IDENTIFIER).build());
  private static final Optional<Project> projectResponse = Optional.of(
      Project.builder().name(PROJECT_NAME).orgIdentifier(ORG_IDENTIFIER).identifier(PROJECT_IDENTIFIER).build());

  @Before
  public void setup() {
    initMocks(this);
  }

  @Test
  @Owner(developers = NAMANG)
  @Category(UnitTests.class)
  public void testToScopeNameDTOWhenOrgWhenExists() throws IOException {
    ScopeDTO scopeDTO = ScopeDTO.builder().accountIdentifier(ACCOUNT_IDENTIFIER).orgIdentifier(ORG_IDENTIFIER).build();
    doReturn(organizationResponse).when(organizationService).get(ACCOUNT_IDENTIFIER, ORG_IDENTIFIER);
    ScopeNameDTO result = scopeNameMapper.toScopeNameDTO(scopeDTO);
    verify(organizationService, times(1)).get(ACCOUNT_IDENTIFIER, ORG_IDENTIFIER);
    verifyNoMoreInteractions(organizationService);
    verifyNoMoreInteractions(projectService);
    assertThat(result.getAccountIdentifier()).isEqualTo(ACCOUNT_IDENTIFIER);
    assertThat(result.getOrgIdentifier()).isEqualTo(ORG_IDENTIFIER);
    assertThat(result.getProjectIdentifier()).isEqualTo(null);
    assertThat(result.getOrgName()).isEqualTo(ORG_NAME);
    assertThat(result.getProjectName()).isEqualTo(null);
  }

  @Test
  @Owner(developers = NAMANG)
  @Category(UnitTests.class)
  public void testToScopeNameDTOWhenOrgWhenNotExists() throws IOException {
    ScopeDTO scopeDTO = ScopeDTO.builder().accountIdentifier(ACCOUNT_IDENTIFIER).orgIdentifier(ORG_IDENTIFIER).build();
    doReturn(Optional.empty()).when(organizationService).get(ACCOUNT_IDENTIFIER, ORG_IDENTIFIER);
    try {
      scopeNameMapper.toScopeNameDTO(scopeDTO);
      fail("Expected failure as org does not exists");
    } catch (InvalidRequestException ex) {
      assertThat(ex.getParams().get("message"))
          .isEqualTo(String.format("Organization details not found for org Identifier: [%s]", ORG_IDENTIFIER));
    }
  }

  @Test
  @Owner(developers = NAMANG)
  @Category(UnitTests.class)
  public void testToScopeNameDTOWhenProjectWhenOrgNotExists() throws IOException {
    ScopeDTO scopeDTO = ScopeDTO.builder()
                            .accountIdentifier(ACCOUNT_IDENTIFIER)
                            .orgIdentifier(ORG_IDENTIFIER)
                            .projectIdentifier(PROJECT_IDENTIFIER)
                            .build();
    doReturn(Optional.empty()).when(organizationService).get(ACCOUNT_IDENTIFIER, ORG_IDENTIFIER);
    try {
      scopeNameMapper.toScopeNameDTO(scopeDTO);
      fail("Expected failure as org does not exists");
    } catch (InvalidRequestException ex) {
      assertThat(ex.getParams().get("message"))
          .isEqualTo(String.format("Organization details not found for org Identifier: [%s]", ORG_IDENTIFIER));
    }
  }

  @Test
  @Owner(developers = NAMANG)
  @Category(UnitTests.class)
  public void testToScopeNameDTOWhenProjectWhenOrgExistsProjectNotExists() throws IOException {
    ScopeDTO scopeDTO = ScopeDTO.builder()
                            .accountIdentifier(ACCOUNT_IDENTIFIER)
                            .orgIdentifier(ORG_IDENTIFIER)
                            .projectIdentifier(PROJECT_IDENTIFIER)
                            .build();
    doReturn(organizationResponse).when(organizationService).get(ACCOUNT_IDENTIFIER, ORG_IDENTIFIER);

    doReturn(Optional.empty()).when(projectService).get(ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER);
    try {
      scopeNameMapper.toScopeNameDTO(scopeDTO);
      fail("Expected failure as project does not exists");
    } catch (InvalidRequestException ex) {
      assertThat(ex.getParams().get("message"))
          .isEqualTo(String.format("Project details not found for project Identifier: [%s]", PROJECT_IDENTIFIER));
    }
  }

  @Test
  @Owner(developers = NAMANG)
  @Category(UnitTests.class)
  public void testToScopeNameDTOWhenProjectWhenOrgExistsProjectExists() throws IOException {
    ScopeDTO scopeDTO = ScopeDTO.builder()
                            .accountIdentifier(ACCOUNT_IDENTIFIER)
                            .orgIdentifier(ORG_IDENTIFIER)
                            .projectIdentifier(PROJECT_IDENTIFIER)
                            .build();
    doReturn(organizationResponse).when(organizationService).get(ACCOUNT_IDENTIFIER, ORG_IDENTIFIER);
    doReturn(projectResponse).when(projectService).get(ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER);

    ScopeNameDTO result = scopeNameMapper.toScopeNameDTO(scopeDTO);
    verify(organizationService, times(1)).get(ACCOUNT_IDENTIFIER, ORG_IDENTIFIER);
    verify(projectService, times(1)).get(ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER);
    verifyNoMoreInteractions(organizationService);
    verifyNoMoreInteractions(projectService);
    assertThat(result.getAccountIdentifier()).isEqualTo(ACCOUNT_IDENTIFIER);
    assertThat(result.getOrgIdentifier()).isEqualTo(ORG_IDENTIFIER);
    assertThat(result.getProjectIdentifier()).isEqualTo(PROJECT_IDENTIFIER);
    assertThat(result.getOrgName()).isEqualTo(ORG_NAME);
    assertThat(result.getProjectName()).isEqualTo(PROJECT_NAME);
  }

  @Test
  @Owner(developers = NAMANG)
  @Category(UnitTests.class)
  public void testToScopeNameDTOWhenAccount() {
    ScopeDTO scopeDTO = ScopeDTO.builder().accountIdentifier(ACCOUNT_IDENTIFIER).build();

    ScopeNameDTO result = scopeNameMapper.toScopeNameDTO(scopeDTO);
    verifyNoMoreInteractions(organizationService);
    verifyNoMoreInteractions(projectService);
    assertThat(result.getAccountIdentifier()).isEqualTo(ACCOUNT_IDENTIFIER);
    assertThat(result.getOrgIdentifier()).isEqualTo(null);
    assertThat(result.getProjectIdentifier()).isEqualTo(null);
    assertThat(result.getOrgName()).isEqualTo(null);
    assertThat(result.getProjectName()).isEqualTo(null);
  }
}