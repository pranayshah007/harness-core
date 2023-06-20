/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.connector.helper;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.connector.accesscontrol.ConnectorsAccessControlPermissions.VIEW_CONNECTOR_PERMISSION;

import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.harness.accesscontrol.NGAccessDeniedException;
import io.harness.accesscontrol.acl.api.AccessCheckResponseDTO;
import io.harness.accesscontrol.acl.api.AccessControlDTO;
import io.harness.accesscontrol.acl.api.ResourceScope;
import io.harness.accesscontrol.clients.AccessControlClient;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.connector.accesscontrol.ResourceTypes;
import io.harness.connector.entities.Connector;
import io.harness.connector.entities.embedded.githubconnector.GithubConnector;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;

import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(PL)
public class ConnectorRbacHelperTest {
  @Mock AccessControlClient accessControlClient;
  @InjectMocks ConnectorRbacHelper connectorRbacHelper;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    // accessControlClient = mock(AccessControlClient.class);
    // connectorRbacHelper = new ConnectorRbacHelper(accessControlClient);
  }

  @Test
  @Owner(developers = OwnerRule.JIMIT_GANDHI)
  @Category(UnitTests.class)
  public void getPermitted_CalledWithEmptyConnectorList_ReturnsNGAccessDeniedException() {
    try {
      connectorRbacHelper.getPermitted(emptyList());
    } catch (NGAccessDeniedException ex) {
      assertThat(ex.getMessage())
          .isEqualTo(String.format("Missing permission %s on %s", VIEW_CONNECTOR_PERMISSION, ResourceTypes.CONNECTOR));
    }
  }
  @Test
  @Owner(developers = OwnerRule.JIMIT_GANDHI)
  @Category(UnitTests.class)
  public void getPermitted_ReturnsOnlyPermitted() {
    String accountIdentifier = "accountIdentifier";
    GithubConnector githubConnector = GithubConnector.builder().build();
    githubConnector.setIdentifier("connector1");
    githubConnector.setAccountIdentifier(accountIdentifier);
    GithubConnector githubConnector2 = GithubConnector.builder().build();
    githubConnector2.setIdentifier("connector2");
    githubConnector2.setAccountIdentifier(accountIdentifier);
    List<Connector> connectorList = List.of(githubConnector, githubConnector2);

    ResourceScope resourceScope = ResourceScope.of(accountIdentifier, null, null);
    List<AccessControlDTO> accessControlDTOList = List.of(AccessControlDTO.builder()
                                                              .permitted(true)
                                                              .resourceScope(resourceScope)
                                                              .resourceIdentifier("connector1")
                                                              .build(),
        AccessControlDTO.builder()
            .resourceScope(resourceScope)
            .resourceIdentifier("connector2")
            .permitted(false)
            .build());
    AccessCheckResponseDTO accessCheckResponse =
        AccessCheckResponseDTO.builder().accessControlList(accessControlDTOList).build();
    when(accessControlClient.checkForAccessOrThrow(any())).thenReturn(accessCheckResponse);
    List<Connector> permittedConnectors = connectorRbacHelper.getPermitted(connectorList);
    assertThat(permittedConnectors.size()).isEqualTo(1);
    assertThat(permittedConnectors.get(0)).isEqualTo(githubConnector);
  }
}
