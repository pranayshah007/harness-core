/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.gitintegration.processor.factory;

import static io.harness.delegate.beans.connector.ConnectorType.AZURE_REPO;
import static io.harness.delegate.beans.connector.ConnectorType.BITBUCKET;
import static io.harness.delegate.beans.connector.ConnectorType.CODECOMMIT;
import static io.harness.delegate.beans.connector.ConnectorType.GITHUB;
import static io.harness.delegate.beans.connector.ConnectorType.GITLAB;
import static io.harness.rule.OwnerRule.VIKYATH_HAREKAL;

import static junit.framework.TestCase.assertEquals;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.idp.gitintegration.processor.impl.AzureRepoConnectorProcessor;
import io.harness.idp.gitintegration.processor.impl.BitbucketConnectorProcessor;
import io.harness.idp.gitintegration.processor.impl.GithubConnectorProcessor;
import io.harness.idp.gitintegration.processor.impl.GitlabConnectorProcessor;
import io.harness.rule.Owner;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class ConnectorProcessorFactoryTest extends CategoryTest {
  AutoCloseable openMocks;
  @Mock GithubConnectorProcessor githubConnectorProcessor;
  @Mock GitlabConnectorProcessor gitlabConnectorProcessor;
  @Mock BitbucketConnectorProcessor bitbucketConnectorProcessor;
  @Mock AzureRepoConnectorProcessor azureRepoConnectorProcessor;
  @InjectMocks ConnectorProcessorFactory factory;

  @Before
  public void setUp() throws Exception {
    openMocks = MockitoAnnotations.openMocks(this);
  }

  @Test
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testGetConnectorProcessor() {
    assertEquals(githubConnectorProcessor, factory.getConnectorProcessor(GITHUB));
    assertEquals(gitlabConnectorProcessor, factory.getConnectorProcessor(GITLAB));
    assertEquals(bitbucketConnectorProcessor, factory.getConnectorProcessor(BITBUCKET));
    assertEquals(azureRepoConnectorProcessor, factory.getConnectorProcessor(AZURE_REPO));
  }

  @Test(expected = UnsupportedOperationException.class)
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testGetConnectorProcessorUnsupportedType() {
    factory.getConnectorProcessor(CODECOMMIT);
  }

  @Test(expected = IllegalArgumentException.class)
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testGetConnectorProcessorNullType() {
    factory.getConnectorProcessor(null);
  }

  @After
  public void tearDown() throws Exception {
    openMocks.close();
  }
}
