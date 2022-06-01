/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.graphql.datafetcher.connector.types;

import static io.harness.rule.OwnerRule.TMACARI;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.ff.FeatureFlagService;
import io.harness.rule.Owner;

import software.wings.graphql.datafetcher.connector.ConnectorsController;
import software.wings.graphql.datafetcher.secrets.UsageScopeController;
import software.wings.graphql.schema.type.QLConnectorType;
import software.wings.service.intfc.SettingsService;
import software.wings.service.intfc.security.SecretManager;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class ConnectorFactoryTest extends CategoryTest {
  @Mock private SecretManager secretManager;
  @Mock private SettingsService settingsService;
  @Mock private ConnectorsController connectorsController;
  @Mock private UsageScopeController usageScopeController;
  @Mock private FeatureFlagService featureFlagService;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testGetGitConnector() {
    Connector connector = ConnectorFactory.getConnector(QLConnectorType.GIT, connectorsController, secretManager,
        settingsService, usageScopeController, featureFlagService);
    assertThat(connector).isInstanceOf(GitConnector.class);
  }
}
