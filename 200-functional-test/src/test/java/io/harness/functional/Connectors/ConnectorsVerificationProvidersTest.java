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
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runners.MethodSorters;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class ConnectorsVerificationProvidersTest extends AbstractFunctionalTest {
  private static String CONNECTOR_APPDYNAMICS_PREFIX = "Automation-Appdynamics-Connector-";
  private static String CONNECTOR_NEWRELIC_PREFIX = "Automation-Newrelic-Connector-";
  private static String CONNECTOR_ELK_PREFIX = "Automation-ELK-Connector-";
  private static String CONNECTOR_SPLUNK_PREFIX = "Automation-Splunk-Connector-";
  private static String CONNECTOR_BUGSNAG_PREFIX = "Automation-Bugsnag-Connector-";
  private static String CONNECTOR_DATADOG_PREFIX = "Automation-Datadog-Connector-";
  private static String CONNECTOR_PROMETHEUS_PREFIX = "Automation-Prometheus-Connector-";
  private static String CONNECTOR_SUMOLOGIC_PREFIX = "Automation-SumoLogic-Connector-";
  private static String CATEGORY = "CONNECTOR";

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
  public void TC1_createAndDeleteAppdynamicsConnector() {
    String connectorName = CONNECTOR_APPDYNAMICS_PREFIX + System.currentTimeMillis();
    String connectorId = ConnectorUtils.createAppDynamicsConnector(bearerToken, connectorName, getAccount().getUuid());

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

  @Test
  @Owner(developers = SHASWAT, intermittent = true)
  @Category(FunctionalTests.class)
  public void TC2_createAndDeleteNewrelicConnector() {
    String connectorName = CONNECTOR_NEWRELIC_PREFIX + System.currentTimeMillis();
    String connectorId = ConnectorUtils.createNewrelicConnector(bearerToken, connectorName, getAccount().getUuid());

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

  @Test
  @Owner(developers = SHASWAT, intermittent = true)
  @Category(FunctionalTests.class)
  public void TC3_createAndDeleteELKConnector() {
    String connectorName = CONNECTOR_ELK_PREFIX + System.currentTimeMillis();
    String connectorId = ConnectorUtils.createELKConnector(bearerToken, connectorName, getAccount().getUuid());

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

  @Test
  @Owner(developers = SHASWAT, intermittent = true)
  @Category(FunctionalTests.class)
  public void TC4_createAndDeleteSplunkConnector() {
    String connectorName = CONNECTOR_SPLUNK_PREFIX + System.currentTimeMillis();
    String connectorId = ConnectorUtils.createSplunkConnector(bearerToken, connectorName, getAccount().getUuid());

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

  @Test
  @Owner(developers = SHASWAT, intermittent = true)
  @Category(FunctionalTests.class)
  public void TC5_createAndDeleteBugsnagConnector() {
    String connectorName = CONNECTOR_BUGSNAG_PREFIX + System.currentTimeMillis();
    String connectorId = ConnectorUtils.createBugsnagConnector(bearerToken, connectorName, getAccount().getUuid());

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

  @Test
  @Owner(developers = SHASWAT, intermittent = true)
  @Category(FunctionalTests.class)
  public void TC6_createAndDeleteDatadogConnector() {
    String connectorName = CONNECTOR_DATADOG_PREFIX + System.currentTimeMillis();
    String connectorId = ConnectorUtils.createDatadogConnector(bearerToken, connectorName, getAccount().getUuid());

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

  @Test
  @Owner(developers = SHASWAT, intermittent = true)
  @Category(FunctionalTests.class)
  public void TC7_createAndDeleteSumoLogicConnector() {
    String connectorName = CONNECTOR_SUMOLOGIC_PREFIX + System.currentTimeMillis();
    String connectorId = ConnectorUtils.createSumoLogicConnector(bearerToken, connectorName, getAccount().getUuid());

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

  @Test
  @Owner(developers = SHASWAT, intermittent = true)
  @Category(FunctionalTests.class)
  public void TC8_createAndDeletePrometheusConnector() {
    String connectorName = CONNECTOR_PROMETHEUS_PREFIX + System.currentTimeMillis();
    String connectorId = ConnectorUtils.createPrometheusConnector(bearerToken, connectorName, getAccount().getUuid());

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
