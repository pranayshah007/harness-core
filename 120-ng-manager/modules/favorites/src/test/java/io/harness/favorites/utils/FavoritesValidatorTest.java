/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.favorites.utils;

import static io.harness.rule.OwnerRule.BOOPESH;

import static java.util.Optional.of;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.ConnectorResponseDTO;
import io.harness.connector.services.ConnectorService;
import io.harness.delegate.beans.connector.nexusconnector.NexusConnectorDTO;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.api.SecretCrudService;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.core.dto.secrets.SecretDTOV2;
import io.harness.ng.core.dto.secrets.SecretResponseWrapper;
import io.harness.ng.core.entities.Project;
import io.harness.ng.core.services.ProjectService;
import io.harness.ng.core.user.UserInfo;
import io.harness.pipeline.remote.PipelineServiceClient;
import io.harness.pms.pipeline.PMSPipelineResponseDTO;
import io.harness.rest.RestResponse;
import io.harness.rule.Owner;
import io.harness.spec.server.ng.v1.model.FavoriteDTO;
import io.harness.spec.server.ng.v1.model.FavoritesResourceType;
import io.harness.user.remote.UserClient;

import java.io.IOException;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import retrofit2.Call;
import retrofit2.Response;

public class FavoritesValidatorTest extends CategoryTest {
  @Mock private UserClient userClient;
  @Mock private ConnectorService connectorService;
  @Mock private PipelineServiceClient pipelineServiceClient;
  @Mock private SecretCrudService secretCrudService;
  @Mock private ProjectService projectService;
  private FavoritesValidator favoritesValidator;
  private FavoriteDTO favoriteDTO;

  private final String userId = "userId";
  private final String accountId = "accountId";
  private final String orgId = "org";
  private final String projectId = "project";
  private final String resourceId = "resourceUUID";
  private final String errorMessage =
      "The Resource with ID [%s] and type [%s], which is being marked as favorite does not exist in account [%s]";

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
    favoritesValidator =
        new FavoritesValidator(userClient, connectorService, pipelineServiceClient, secretCrudService, projectService);
    favoriteDTO = new FavoriteDTO()
                      .org(orgId)
                      .project(projectId)
                      .userId(userId)
                      .module(io.harness.spec.server.ng.v1.model.ModuleType.CD)
                      .resourceType(FavoritesResourceType.CONNECTOR)
                      .resourceId(resourceId);
  }

  @Test
  @Owner(developers = BOOPESH)
  @Category(UnitTests.class)
  public void testValidConnectorFavorite() throws IOException {
    Call userCall = mock(Call.class);
    when(userClient.getUserById(userId)).thenReturn(userCall);
    when(userCall.execute()).thenReturn(Response.success(new RestResponse(of(UserInfo.builder().build()))));
    Optional<ConnectorResponseDTO> connectorResponseDTO =
        of(ConnectorResponseDTO.builder()
                .connector(ConnectorInfoDTO.builder().connectorConfig(NexusConnectorDTO.builder().build()).build())
                .build());
    when(connectorService.get(any(), any(), any(), any())).thenReturn(connectorResponseDTO);
    assertDoesNotThrow(() -> favoritesValidator.validateFavoriteEntry(favoriteDTO, accountId));
  }

  @Test
  @Owner(developers = BOOPESH)
  @Category(UnitTests.class)
  public void testInValidUserCallFavorite() throws IOException {
    Call userCall = mock(Call.class);
    when(userClient.getUserById(userId)).thenReturn(userCall);
    when(userCall.execute()).thenReturn(Response.success(new RestResponse(Optional.empty())));
    assertThatThrownBy(() -> favoritesValidator.validateFavoriteEntry(favoriteDTO, accountId))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("User doesn't exist");
  }

  @Test
  @Owner(developers = BOOPESH)
  @Category(UnitTests.class)
  public void testInValidConnectorFavorite() throws IOException {
    Call userCall = mock(Call.class);
    when(userClient.getUserById(userId)).thenReturn(userCall);
    when(userCall.execute()).thenReturn(Response.success(new RestResponse(Optional.of(UserInfo.builder().build()))));
    when(connectorService.get(any(), any(), any(), any())).thenReturn(Optional.empty());
    assertThatThrownBy(() -> favoritesValidator.validateFavoriteEntry(favoriteDTO, accountId))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage(String.format(errorMessage, favoriteDTO.getResourceId(), favoriteDTO.getResourceType(), accountId));
  }

  @Test
  @Owner(developers = BOOPESH)
  @Category(UnitTests.class)
  public void testValidProjectFavorite() throws IOException {
    favoriteDTO.setResourceType(FavoritesResourceType.PROJECT);
    Call userCall = mock(Call.class);
    when(userClient.getUserById(userId)).thenReturn(userCall);
    when(userCall.execute()).thenReturn(Response.success(new RestResponse(Optional.of(UserInfo.builder().build()))));
    Project project = Project.builder().identifier(resourceId).name(resourceId).build();
    when(projectService.get(accountId, orgId, resourceId)).thenReturn(of(project));
    assertDoesNotThrow(() -> favoritesValidator.validateFavoriteEntry(favoriteDTO, accountId));
  }

  @Test
  @Owner(developers = BOOPESH)
  @Category(UnitTests.class)
  public void testInValidProjectFavorite() throws IOException {
    favoriteDTO.setResourceType(FavoritesResourceType.PROJECT);
    Call userCall = mock(Call.class);
    when(userClient.getUserById(userId)).thenReturn(userCall);
    when(userCall.execute()).thenReturn(Response.success(new RestResponse(Optional.of(UserInfo.builder().build()))));
    when(projectService.get(accountId, orgId, resourceId)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> favoritesValidator.validateFavoriteEntry(favoriteDTO, accountId))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage(String.format(errorMessage, favoriteDTO.getResourceId(), favoriteDTO.getResourceType(), accountId));
  }

  @Test
  @Owner(developers = BOOPESH)
  @Category(UnitTests.class)
  public void testValidPipelineFavorite() throws IOException {
    favoriteDTO.setResourceType(FavoritesResourceType.PIPELINE);
    Call userCall = mock(Call.class);
    when(userClient.getUserById(userId)).thenReturn(userCall);
    when(userCall.execute()).thenReturn(Response.success(new RestResponse(Optional.of(UserInfo.builder().build()))));
    Call pipelineCall = mock(Call.class);
    when(pipelineServiceClient.getPipelineByIdentifier(
             favoriteDTO.getResourceId(), accountId, favoriteDTO.getOrg(), favoriteDTO.getProject(), null, null, null))
        .thenReturn(pipelineCall);
    when(pipelineCall.execute())
        .thenReturn(Response.success(
            ResponseDTO.newResponse(PMSPipelineResponseDTO.builder().yamlPipeline(resourceId).build())));
    assertDoesNotThrow(() -> favoritesValidator.validateFavoriteEntry(favoriteDTO, accountId));
  }

  @Test
  @Owner(developers = BOOPESH)
  @Category(UnitTests.class)
  public void testInValidPipelineFavorite() throws IOException {
    favoriteDTO.setResourceType(FavoritesResourceType.PIPELINE);
    Call userCall = mock(Call.class);
    when(userClient.getUserById(userId)).thenReturn(userCall);
    when(userCall.execute()).thenReturn(Response.success(new RestResponse(Optional.of(UserInfo.builder().build()))));
    Call pipelineCall = mock(Call.class);
    when(pipelineServiceClient.getPipelineByIdentifier(
             favoriteDTO.getResourceId(), accountId, favoriteDTO.getOrg(), favoriteDTO.getProject(), null, null, null))
        .thenReturn(pipelineCall);
    when(pipelineCall.execute())
        .thenReturn(Response.success(ResponseDTO.newResponse(PMSPipelineResponseDTO.builder().build())));
    assertThatThrownBy(() -> favoritesValidator.validateFavoriteEntry(favoriteDTO, accountId))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage(String.format(errorMessage, favoriteDTO.getResourceId(), favoriteDTO.getResourceType(), accountId));
  }

  @Test
  @Owner(developers = BOOPESH)
  @Category(UnitTests.class)
  public void testValidSecretFavorite() throws IOException {
    favoriteDTO.setResourceType(FavoritesResourceType.SECRET);
    Call userCall = mock(Call.class);
    when(userClient.getUserById(userId)).thenReturn(userCall);
    when(userCall.execute()).thenReturn(Response.success(new RestResponse(Optional.of(UserInfo.builder().build()))));
    SecretResponseWrapper secretResponseWrapper =
        SecretResponseWrapper.builder().secret(SecretDTOV2.builder().build()).build();
    when(secretCrudService.get(accountId, orgId, projectId, resourceId)).thenReturn(of(secretResponseWrapper));
    assertDoesNotThrow(() -> favoritesValidator.validateFavoriteEntry(favoriteDTO, accountId));
  }

  @Test
  @Owner(developers = BOOPESH)
  @Category(UnitTests.class)
  public void testInValidSecretFavorite() throws IOException {
    favoriteDTO.setResourceType(FavoritesResourceType.SECRET);
    Call userCall = mock(Call.class);
    when(userClient.getUserById(userId)).thenReturn(userCall);
    when(userCall.execute()).thenReturn(Response.success(new RestResponse(Optional.of(UserInfo.builder().build()))));
    when(secretCrudService.get(accountId, orgId, projectId, resourceId)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> favoritesValidator.validateFavoriteEntry(favoriteDTO, accountId))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage(String.format(errorMessage, favoriteDTO.getResourceId(), favoriteDTO.getResourceType(), accountId));
  }
}
