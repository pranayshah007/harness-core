/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.gitintegration.resources;

import static io.harness.rule.OwnerRule.VIGNESWARA;

import static junit.framework.TestCase.assertEquals;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.idp.gitintegration.beans.CatalogInfraConnectorType;
import io.harness.idp.gitintegration.entities.CatalogConnectorEntity;
import io.harness.idp.gitintegration.service.GitIntegrationService;
import io.harness.rule.Owner;
import io.harness.spec.server.idp.v1.model.ConnectorDetails;
import io.harness.spec.server.idp.v1.model.ConnectorInfoRequest;
import io.harness.spec.server.idp.v1.model.ConnectorInfoResponse;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.ws.rs.core.Response;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class ConnectorInfoApiImplTest {
  @InjectMocks ConnectorInfoApiImpl connectorInfoApiImpl;
  @Mock GitIntegrationService gitIntegrationService;
  private static final String ACCOUNT_ID = "123";
  private static final String GITHUB_IDENTIFIER = "testGithub";
  public static final String GITHUB_CONNECTOR_TYPE = "Github";
  private static final String GITLAB_IDENTIFIER = "testGitlab";
  public static final String GITLAB_CONNECTOR_TYPE = "Gitlab";

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testGetConnectorInfo() {
    when(gitIntegrationService.findDefaultConnectorDetails(ACCOUNT_ID)).thenReturn(getGithubConnectorEntity());
    Response response = connectorInfoApiImpl.getConnectorInfo(ACCOUNT_ID);
    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());

    when(gitIntegrationService.findDefaultConnectorDetails(ACCOUNT_ID)).thenReturn(null);
    response = connectorInfoApiImpl.getConnectorInfo(ACCOUNT_ID);
    assertThat(response.getStatus()).isEqualTo(Response.Status.NOT_FOUND.getStatusCode());
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testGetConnectorInfoByProviderType() {
    when(gitIntegrationService.findByAccountIdAndProviderType(ACCOUNT_ID, GITHUB_CONNECTOR_TYPE))
        .thenReturn(Optional.ofNullable(getGithubConnectorEntity()));
    Response response = connectorInfoApiImpl.getConnectorInfoByProviderType(GITHUB_CONNECTOR_TYPE, ACCOUNT_ID);
    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());

    when(gitIntegrationService.findByAccountIdAndProviderType(ACCOUNT_ID, GITHUB_CONNECTOR_TYPE))
        .thenReturn(Optional.empty());
    response = connectorInfoApiImpl.getConnectorInfoByProviderType(GITHUB_CONNECTOR_TYPE, ACCOUNT_ID);
    assertThat(response.getStatus()).isEqualTo(Response.Status.NOT_FOUND.getStatusCode());
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testGetConnectorsInfo() {
    List<CatalogConnectorEntity> catalogConnectorEntityList = new ArrayList<>();
    catalogConnectorEntityList.add(getGithubConnectorEntity());
    catalogConnectorEntityList.add(getGitlabConnectorEntity());
    when(gitIntegrationService.getAllConnectorDetails(ACCOUNT_ID)).thenReturn(catalogConnectorEntityList);
    Response response = connectorInfoApiImpl.getConnectorsInfo(ACCOUNT_ID);
    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
    assertEquals(2, ((List<ConnectorInfoResponse>) response.getEntity()).size());
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testSaveConnectorInfo() {
    ConnectorInfoRequest request = new ConnectorInfoRequest();
    ConnectorDetails connectorDetails = new ConnectorDetails();
    connectorDetails.setIdentifier(GITHUB_IDENTIFIER);
    connectorDetails.setType(GITHUB_CONNECTOR_TYPE);
    request.setConnectorDetails(connectorDetails);
    when(gitIntegrationService.saveConnectorDetails(ACCOUNT_ID, connectorDetails))
        .thenReturn(getGithubConnectorEntity());
    Response response = connectorInfoApiImpl.saveConnectorInfo(request, ACCOUNT_ID);
    assertThat(response.getStatus()).isEqualTo(Response.Status.CREATED.getStatusCode());

    when(gitIntegrationService.saveConnectorDetails(ACCOUNT_ID, connectorDetails))
        .thenThrow(InvalidRequestException.class);
    response = connectorInfoApiImpl.saveConnectorInfo(request, ACCOUNT_ID);
    assertThat(response.getStatus()).isEqualTo(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode());
  }

  private CatalogConnectorEntity getGithubConnectorEntity() {
    return CatalogConnectorEntity.builder()
        .accountIdentifier(ACCOUNT_ID)
        .connectorIdentifier(GITHUB_IDENTIFIER)
        .connectorProviderType(GITHUB_CONNECTOR_TYPE)
        .type(CatalogInfraConnectorType.DIRECT)
        .build();
  }

  private CatalogConnectorEntity getGitlabConnectorEntity() {
    return CatalogConnectorEntity.builder()
        .accountIdentifier(ACCOUNT_ID)
        .connectorIdentifier(GITLAB_IDENTIFIER)
        .connectorProviderType(GITLAB_CONNECTOR_TYPE)
        .type(CatalogInfraConnectorType.DIRECT)
        .build();
  }
}
