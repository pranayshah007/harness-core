/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.events;

import static io.harness.audit.ResourceTypeConstants.CONNECTOR;
import static io.harness.connector.ConnectorEvent.CONNECTOR_FORCE_DELETED;
import static io.harness.rule.OwnerRule.MEENAKSHI;

import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;
import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.events.ConnectorForceDeleteEvent;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.delegate.beans.connector.k8Connector.KubernetesClusterConfigDTO;
import io.harness.delegate.beans.connector.k8Connector.KubernetesCredentialDTO;
import io.harness.delegate.beans.connector.k8Connector.KubernetesCredentialType;
import io.harness.ng.core.OrgScope;
import io.harness.ng.core.Resource;
import io.harness.ng.core.ResourceScope;
import io.harness.rule.Owner;

import java.util.Collections;
import java.util.HashSet;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class ConnectorForceDeleteEventTest extends CategoryTest {
  private String ACC_ID = randomAlphabetic(10);
  private String ORG_ID = randomAlphabetic(10);
  private String ID = randomAlphabetic(10);
  private ConnectorInfoDTO connector =
      ConnectorInfoDTO.builder()
          .identifier(ID)
          .orgIdentifier(ORG_ID)
          .connectorType(ConnectorType.KUBERNETES_CLUSTER)
          .connectorConfig(KubernetesClusterConfigDTO.builder()
                               .credential(KubernetesCredentialDTO.builder()
                                               .kubernetesCredentialType(KubernetesCredentialType.INHERIT_FROM_DELEGATE)
                                               .build())
                               .delegateSelectors(new HashSet<>(Collections.singletonList("delegate")))
                               .build())
          .build();

  private ConnectorForceDeleteEvent deleteEvent;
  @Before
  public void before() {
    deleteEvent = new ConnectorForceDeleteEvent(ACC_ID, connector);
  }
  @Test
  @Owner(developers = MEENAKSHI)
  @Category(UnitTests.class)
  public void testGetResource() {
    Resource resource = deleteEvent.getResource();
    assertThat(resource.getIdentifier()).isEqualTo(connector.getIdentifier());
    assertThat(resource.getType()).isEqualTo(CONNECTOR);
  }

  @Test
  @Owner(developers = MEENAKSHI)
  @Category(UnitTests.class)
  public void getEventType() {
    assertThat(deleteEvent.getEventType()).isEqualTo(CONNECTOR_FORCE_DELETED);
  }

  @Test
  @Owner(developers = MEENAKSHI)
  @Category(UnitTests.class)
  public void testGetResourceScope() {
    ResourceScope resourceScope = deleteEvent.getResourceScope();
    assertThat(resourceScope.getScope()).isEqualTo("org");
    OrgScope projectScope = (OrgScope) resourceScope;
    assertThat(projectScope.getAccountIdentifier()).isEqualTo(ACC_ID);
    assertThat(projectScope.getOrgIdentifier()).isEqualTo(ORG_ID);
  }
}
