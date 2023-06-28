/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.common.delegateselectors.utils;

import static io.harness.rule.OwnerRule.VIKYATH_HAREKAL;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.delegate.beans.connector.ConnectorConfigDTO;
import io.harness.delegate.beans.connector.scm.genericgitconnector.GitConfigDTO;
import io.harness.delegate.beans.connector.scm.github.GithubConnectorDTO;
import io.harness.rule.Owner;

import java.util.HashSet;
import java.util.Set;
import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class DelegateSelectorsUtilsTest extends CategoryTest {
  @Test
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testExtractDelegateSelectorsReturnsDelegateSelectors() {
    Set<String> expectedDelegateSelectors = new HashSet<>();
    expectedDelegateSelectors.add("selector1");
    expectedDelegateSelectors.add("selector2");

    ConnectorInfoDTO connectorInfoDTO = new ConnectorInfoDTO();
    GithubConnectorDTO connectorConfig = new GithubConnectorDTO();
    connectorConfig.setDelegateSelectors(new HashSet<>(Set.of("selector1", "selector2")));
    connectorInfoDTO.setConnectorConfig(connectorConfig);

    Set<String> actualDelegateSelectors =
        DelegateSelectorsUtils.extractDelegateSelectors((ConnectorInfoDTO) connectorInfoDTO);

    Assert.assertEquals(expectedDelegateSelectors, actualDelegateSelectors);
  }

  @Test
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testExtractDelegateSelectorsWithNullConfigReturnsEmptySet() {
    ConnectorInfoDTO connectorInfoDTO = new ConnectorInfoDTO();
    connectorInfoDTO.setConnectorConfig(null);

    Set<String> actualDelegateSelectors = DelegateSelectorsUtils.extractDelegateSelectors(connectorInfoDTO);

    Assert.assertTrue(actualDelegateSelectors.isEmpty());
  }
}
