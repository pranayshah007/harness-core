/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.POOJA;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.beans.EmbeddedUser;
import io.harness.category.element.UnitTests;
import io.harness.category.layer.GraphQLTests;
import io.harness.generator.OwnerManager;
import io.harness.generator.OwnerManager.Owners;
import io.harness.generator.Randomizer.Seed;
import io.harness.generator.SettingGenerator;
import io.harness.rule.Owner;
import io.harness.testframework.graphql.QLTestObject;

import software.wings.beans.SettingAttribute;
import software.wings.graphql.schema.type.QLUser.QLUserKeys;
import software.wings.graphql.schema.type.connector.QLConnectorsConnection.QLConnectorsConnectionKeys;
import software.wings.graphql.schema.type.connector.QLJiraConnector.QLJiraConnectorKeys;

import com.google.inject.Inject;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class ConnectorTest extends GraphQLTest {
  @Inject private OwnerManager ownerManager;
  @Inject private SettingGenerator settingGenerator;

  private static String connectorQueryPattern = $.GQL(/*
{
  ConnectorDisconnectHandler(connectorId: "%s") {
    id
    name
    createdAt
    createdBy {
      id
    }
  }
}*/ ConnectorTest.class);

  @Test
  @Owner(developers = POOJA)
  @Category({GraphQLTests.class, UnitTests.class})
  public void testQueryConnector() {
    final Seed seed = new Seed(0);
    final Owners owners = ownerManager.create();
    owners.add(EmbeddedUser.builder().uuid(generateUuid()).build());
    {
      final SettingAttribute settingAttribute = settingGenerator.ensureHarnessJira(seed, owners);
      assertThat(settingAttribute).isNotNull();

      String query = String.format(connectorQueryPattern, settingAttribute.getUuid());

      QLTestObject qlJiraConnector = qlExecute(query, settingAttribute.getAccountId());
      assertThat(qlJiraConnector.get(QLJiraConnectorKeys.id)).isEqualTo(settingAttribute.getUuid());
      assertThat(qlJiraConnector.get(QLJiraConnectorKeys.name)).isEqualTo(settingAttribute.getName());
      assertThat(qlJiraConnector.get(QLJiraConnectorKeys.createdAt)).isEqualTo(settingAttribute.getCreatedAt());
      assertThat(qlJiraConnector.sub(QLJiraConnectorKeys.createdBy).get(QLUserKeys.id))
          .isEqualTo(settingAttribute.getCreatedBy().getUuid());
    }
  }

  @Test
  @Owner(developers = POOJA)
  @Category({GraphQLTests.class, UnitTests.class})
  public void testQueryConnectors() {
    final Seed seed = new Seed(0);
    final Owners owners = ownerManager.create();
    owners.add(EmbeddedUser.builder().uuid(generateUuid()).build());

    final SettingAttribute jira = settingGenerator.ensureHarnessJira(seed, owners);
    assertThat(jira).isNotNull();

    {
      String query = $GQL(/*
{
  connectors(limit:100) {
    nodes {
      id
      name
    }
  }
}*/);

      QLTestObject qlJiraConnector = qlExecute(query, jira.getAccountId());
      assertThat(qlJiraConnector.sub(QLConnectorsConnectionKeys.nodes).size()).isNotEqualTo(0);
    }
  }
}
