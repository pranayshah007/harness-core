/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.utils;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.RAGHU;
import static io.harness.rule.OwnerRule.VARSHA_LALWANI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import io.harness.CvNextGenTestBase;
import io.harness.category.element.UnitTests;
import io.harness.cvng.client.NextGenNonPrivilegedClient;
import io.harness.cvng.client.NextGenPrivilegedClient;
import io.harness.cvng.client.NextGenServiceImpl;
import io.harness.cvng.client.NextGenServiceImpl.EntityKey;
import io.harness.ng.beans.PageResponse;
import io.harness.ng.core.dto.OrganizationDTO;
import io.harness.ng.core.dto.OrganizationResponse;
import io.harness.ng.core.dto.ProjectDTO;
import io.harness.ng.core.dto.ProjectResponse;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.core.environment.dto.EnvironmentResponse;
import io.harness.ng.core.environment.dto.EnvironmentResponseDTO;
import io.harness.ng.core.service.dto.ServiceResponse;
import io.harness.ng.core.service.dto.ServiceResponseDTO;
import io.harness.rule.Owner;

import com.google.common.cache.CacheLoader;
import com.google.common.util.concurrent.UncheckedExecutionException;
import com.google.inject.Inject;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.Mockito;
import retrofit2.Call;
import retrofit2.Response;

public class NextGenServiceImplTest extends CvNextGenTestBase {
  // using NextGenServiceImpl as NextgenService in test env is mocked.
  // Please check - MockedNextGenService
  @Inject private NextGenServiceImpl nextGenService;
  @Mock private NextGenPrivilegedClient nextGenPrivilegedClient;

  @Mock private NextGenNonPrivilegedClient nextGenNonPrivilegedClient;
  private String accountId;
  private String orgIdentifier;
  private String projectIdentifier;

  @Before
  public void setup() throws Exception {
    accountId = generateUuid();
    orgIdentifier = generateUuid();
    projectIdentifier = generateUuid();
    FieldUtils.writeField(nextGenService, "nextGenPrivilegedClient", nextGenPrivilegedClient, true);
    FieldUtils.writeField(nextGenService, "nextGenNonPrivilegedClient", nextGenNonPrivilegedClient, true);
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void testGetEnvironment() throws IOException {
    Call<ResponseDTO<EnvironmentResponse>> call = Mockito.mock(Call.class);
    when(call.clone()).thenReturn(call);
    String envIdentifier = generateUuid();
    when(nextGenPrivilegedClient.getEnvironment(envIdentifier, accountId, orgIdentifier, projectIdentifier))
        .thenReturn(call);
    when(call.execute())
        .thenReturn(Response.success(ResponseDTO.newResponse(
            EnvironmentResponse.builder()
                .environment(EnvironmentResponseDTO.builder().identifier(envIdentifier).name("env").build())
                .build())));
    EnvironmentResponseDTO environment =
        nextGenService.getEnvironment(accountId, orgIdentifier, projectIdentifier, envIdentifier);
    assertThat(environment).isNotNull();
    assertThat(environment.getIdentifier()).isEqualTo(envIdentifier);
    assertThat(environment.getName()).isEqualTo("env");

    final String newEnvIdentifier = generateUuid();
    when(nextGenPrivilegedClient.getEnvironment(newEnvIdentifier, accountId, orgIdentifier, projectIdentifier))
        .thenReturn(call);
    when(call.execute()).thenReturn(Response.success(ResponseDTO.newResponse(EnvironmentResponse.builder().build())));
    assertThatThrownBy(
        () -> nextGenService.getEnvironment(accountId, orgIdentifier, projectIdentifier, newEnvIdentifier))
        .isInstanceOf(CacheLoader.InvalidCacheLoadException.class)
        .hasMessage("CacheLoader returned null for key "
            + EntityKey.builder()
                  .accountId(accountId)
                  .orgIdentifier(orgIdentifier)
                  .projectIdentifier(projectIdentifier)
                  .entityIdentifier(newEnvIdentifier)
                  .build()
            + ".");
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void testGetService() throws IOException {
    Call<ResponseDTO<ServiceResponse>> call = Mockito.mock(Call.class);
    when(call.clone()).thenReturn(call);
    String serviceIdentifier = generateUuid();
    when(nextGenPrivilegedClient.getService(serviceIdentifier, accountId, orgIdentifier, projectIdentifier))
        .thenReturn(call);
    when(call.execute())
        .thenReturn(Response.success(ResponseDTO.newResponse(
            ServiceResponse.builder()
                .service(ServiceResponseDTO.builder().identifier(serviceIdentifier).name("service").build())
                .build())));
    ServiceResponseDTO service =
        nextGenService.getService(accountId, orgIdentifier, projectIdentifier, serviceIdentifier);
    assertThat(service).isNotNull();
    assertThat(service.getIdentifier()).isEqualTo(serviceIdentifier);
    assertThat(service.getName()).isEqualTo("service");

    final String newServiceIdentifier = generateUuid();
    when(nextGenPrivilegedClient.getService(newServiceIdentifier, accountId, orgIdentifier, projectIdentifier))
        .thenReturn(call);
    when(call.execute()).thenReturn(Response.success(ResponseDTO.newResponse(null)));
    assertThatThrownBy(
        () -> nextGenService.getService(accountId, orgIdentifier, projectIdentifier, newServiceIdentifier))
        .isInstanceOf(UncheckedExecutionException.class)
        .hasMessage("java.lang.NullPointerException: Service Response from Ng Manager cannot be null");
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void testGetOrganization() throws IOException {
    Call<ResponseDTO<OrganizationResponse>> call = Mockito.mock(Call.class);
    when(call.clone()).thenReturn(call);
    when(nextGenPrivilegedClient.getOrganization(orgIdentifier, accountId)).thenReturn(call);
    when(call.execute())
        .thenReturn(Response.success(ResponseDTO.newResponse(
            OrganizationResponse.builder()
                .organization(OrganizationDTO.builder().identifier(orgIdentifier).name("orgName").build())
                .build())));
    OrganizationDTO organizationDTO = nextGenService.getOrganization(accountId, this.orgIdentifier);
    assertThat(organizationDTO).isNotNull();
    assertThat(organizationDTO.getIdentifier()).isEqualTo(orgIdentifier);
    assertThat(organizationDTO.getName()).isEqualTo("orgName");

    final String newOrgIdentifier = generateUuid();
    when(nextGenPrivilegedClient.getOrganization(newOrgIdentifier, accountId)).thenReturn(call);
    when(call.execute()).thenReturn(Response.success(ResponseDTO.newResponse(OrganizationResponse.builder().build())));
    assertThatThrownBy(() -> nextGenService.getOrganization(accountId, newOrgIdentifier))
        .isInstanceOf(CacheLoader.InvalidCacheLoadException.class)
        .hasMessage("CacheLoader returned null for key "
            + EntityKey.builder().accountId(accountId).orgIdentifier(newOrgIdentifier).build() + ".");
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void testGetCachedProject() throws IOException {
    Call<ResponseDTO<ProjectResponse>> call = Mockito.mock(Call.class);
    when(call.clone()).thenReturn(call);
    when(nextGenPrivilegedClient.getProject(projectIdentifier, accountId, orgIdentifier)).thenReturn(call);
    when(call.execute())
        .thenReturn(Response.success(ResponseDTO.newResponse(
            ProjectResponse.builder()
                .project(ProjectDTO.builder().identifier(projectIdentifier).name("projectName").build())
                .build())));
    ProjectDTO projectDTO = nextGenService.getCachedProject(accountId, orgIdentifier, projectIdentifier);
    assertThat(projectDTO).isNotNull();
    assertThat(projectDTO.getIdentifier()).isEqualTo(projectIdentifier);
    assertThat(projectDTO.getName()).isEqualTo("projectName");

    final String newProjectIdentifier = generateUuid();
    when(nextGenPrivilegedClient.getProject(newProjectIdentifier, accountId, orgIdentifier)).thenReturn(call);
    when(call.execute()).thenReturn(Response.success(ResponseDTO.newResponse(ProjectResponse.builder().build())));
    assertThatThrownBy(() -> nextGenService.getCachedProject(accountId, orgIdentifier, newProjectIdentifier))
        .isInstanceOf(CacheLoader.InvalidCacheLoadException.class)
        .hasMessage("CacheLoader returned null for key "
            + EntityKey.builder()
                  .accountId(accountId)
                  .orgIdentifier(orgIdentifier)
                  .projectIdentifier(newProjectIdentifier)
                  .build()
            + ".");
  }

  @Test
  @Owner(developers = VARSHA_LALWANI)
  @Category(UnitTests.class)
  public void testGetAllProjects() throws IOException {
    Call<ResponseDTO<PageResponse<ProjectResponse>>> call = Mockito.mock(Call.class);
    when(call.clone()).thenReturn(call);
    when(nextGenNonPrivilegedClient.listProjectsForUser(accountId, null, 0, 100)).thenReturn(call);
    ProjectResponse projectResponse =
        ProjectResponse.builder()
            .project(ProjectDTO.builder().identifier(projectIdentifier).orgIdentifier(orgIdentifier).build())
            .build();
    PageResponse<ProjectResponse> pageResponse = PageResponse.<ProjectResponse>builder()
                                                     .content(Collections.singletonList(projectResponse))
                                                     .totalPages(1)
                                                     .pageIndex(0)
                                                     .pageSize(1)
                                                     .build();
    when(call.execute()).thenReturn(Response.success(ResponseDTO.newResponse(pageResponse)));
    List<ProjectDTO> projectDTOS = nextGenService.listAccessibleProjects(accountId);
    assertThat(projectDTOS).isNotNull();
    assertThat(projectDTOS.size()).isEqualTo(1);
  }
}
