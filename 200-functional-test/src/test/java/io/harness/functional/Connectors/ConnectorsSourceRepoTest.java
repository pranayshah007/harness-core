/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.functional.Connectors;

import static io.harness.rule.OwnerRule.SHASWAT;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.category.element.FunctionalTests;
import io.harness.functional.AbstractFunctionalTest;
import io.harness.rule.Owner;
import io.harness.testframework.restutils.ConnectorUtils;
import io.harness.testframework.restutils.SettingsUtils;

import io.restassured.path.json.JsonPath;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runners.MethodSorters;

@Slf4j
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class ConnectorsSourceRepoTest extends AbstractFunctionalTest {
  private static String CATEGORY = "CONNECTOR";
  private static String CONNECTOR_GIT_PREFIX = "Automation-GIT-Connector-";

  @Test
  @Owner(developers = SHASWAT)
  @Category(FunctionalTests.class)
  public void TC0_listAllConnectors() {
    JsonPath connectors = SettingsUtils.listCloudproviderConnector(bearerToken, getAccount().getUuid(), CATEGORY);
    assertThat(connectors).isNotNull();
  }

  @Test
  @Owner(developers = SHASWAT, intermittent = true)
  @Category(FunctionalTests.class)
  public void TC1_createAndDeleteGitConnector() {
    String connectorName = CONNECTOR_GIT_PREFIX + System.currentTimeMillis();
    List<String> details = ConnectorUtils.createGitConnector(bearerToken, connectorName, getAccount().getUuid());
    String connectorId = details.get(0);
    String webHookToken = details.get(1);

    log.info("WebHook Token: " + webHookToken);

    // Verify ConnectorDisconnectHandler is created i.e ConnectorDisconnectHandler with specific name exist
    boolean connectorFound =
        SettingsUtils.checkCloudproviderConnectorExist(bearerToken, getAccount().getUuid(), CATEGORY, connectorName);
    assertThat(connectorFound).isTrue();

    // Delete the created ConnectorDisconnectHandler
    SettingsUtils.delete(bearerToken, getAccount().getUuid(), connectorId);

    // Verify ConnectorDisconnectHandler is deleted i.e ConnectorDisconnectHandler with specific name doesn't exist
    connectorFound =
        SettingsUtils.checkCloudproviderConnectorExist(bearerToken, getAccount().getUuid(), CATEGORY, connectorName);
    assertThat(connectorFound).isFalse();
  }
}
