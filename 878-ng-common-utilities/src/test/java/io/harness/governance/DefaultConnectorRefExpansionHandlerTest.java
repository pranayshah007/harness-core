/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.governance;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.rule.OwnerRule.NAMAN;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.connector.ConnectorDTO;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.ConnectorResourceClient;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.pms.contracts.governance.ExpansionPlacementStrategy;
import io.harness.pms.contracts.governance.ExpansionRequestMetadata;
import io.harness.pms.gitsync.PmsGitSyncHelper;
import io.harness.pms.sdk.core.governance.ExpansionResponse;
import io.harness.remote.client.NGRestUtils;
import io.harness.rule.Owner;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.TextNode;
import java.io.IOException;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.powermock.core.classloader.annotations.PrepareForTest;
import retrofit2.Call;

@OwnedBy(PIPELINE)
@PrepareForTest({NGRestUtils.class})
public class DefaultConnectorRefExpansionHandlerTest extends CategoryTest {
  @InjectMocks DefaultConnectorRefExpansionHandler connectorRefExpansionHandler;
  @Mock ConnectorResourceClient connectorResourceClient;
  @Mock PmsGitSyncHelper gitSyncHelper;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    Mockito.mockStatic(NGRestUtils.class);
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testExpand() throws IOException {
    String acc = "acc";
    String org = "org";
    String proj = "proj";
    String connProj = "basic-ConnectorDisconnectHandler";
    ConnectorDTO connectorDTOProj =
        ConnectorDTO.builder().connectorInfo(ConnectorInfoDTO.builder().identifier(connProj).build()).build();
    JsonNode jsonNodeProj = new TextNode(connProj);
    ExpansionRequestMetadata metadataProject =
        ExpansionRequestMetadata.newBuilder().setAccountId(acc).setOrgId(org).setProjectId(proj).build();
    Call<ResponseDTO<Optional<ConnectorDTO>>> requestToClient = mock(Call.class);
    doReturn(requestToClient).when(connectorResourceClient).get(connProj, acc, org, proj);
    when(NGRestUtils.getResponse(
             requestToClient, "Could not get ConnectorDisconnectHandler response for account: " + acc + " after {} attempts."))
        .thenReturn(Optional.of(connectorDTOProj));
    ExpansionResponse expansionResponseProj = connectorRefExpansionHandler.expand(jsonNodeProj, metadataProject, null);
    assertThat(expansionResponseProj.isSuccess()).isTrue();
    assertThat(expansionResponseProj.getKey()).isEqualTo("ConnectorDisconnectHandler");
    assertThat(expansionResponseProj.getValue().toJson()).isEqualTo("{\"identifier\":\"basic-ConnectorDisconnectHandler\"}");
    assertThat(expansionResponseProj.getPlacement()).isEqualTo(ExpansionPlacementStrategy.REPLACE);

    String connOrg = "org.conn";
    ConnectorDTO connectorDTOOrg =
        ConnectorDTO.builder().connectorInfo(ConnectorInfoDTO.builder().identifier("conn").build()).build();
    JsonNode jsonNodeOrg = new TextNode(connOrg);
    ExpansionRequestMetadata metadataOrg =
        ExpansionRequestMetadata.newBuilder().setAccountId(acc).setOrgId(org).build();
    doReturn(requestToClient).when(connectorResourceClient).get("conn", acc, org, null);
    when(NGRestUtils.getResponse(
             requestToClient, "Could not get ConnectorDisconnectHandler response for account: " + acc + " after {} attempts."))
        .thenReturn(Optional.of(connectorDTOOrg));
    ExpansionResponse expansionResponseOrg = connectorRefExpansionHandler.expand(jsonNodeOrg, metadataOrg, null);
    assertThat(expansionResponseOrg.isSuccess()).isTrue();
    assertThat(expansionResponseOrg.getKey()).isEqualTo("ConnectorDisconnectHandler");
    assertThat(expansionResponseOrg.getValue().toJson()).isEqualTo("{\"identifier\":\"conn\"}");
    assertThat(expansionResponseOrg.getPlacement()).isEqualTo(ExpansionPlacementStrategy.REPLACE);
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testSendErrorResponseForNotFoundConnector() {
    String id = "conn";
    ExpansionResponse errorResponse = connectorRefExpansionHandler.sendErrorResponseForNotFoundConnector(id);
    assertThat(errorResponse.isSuccess()).isFalse();
  }
}
