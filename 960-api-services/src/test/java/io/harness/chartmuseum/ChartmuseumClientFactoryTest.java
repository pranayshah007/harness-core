/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.chartmuseum;

import static io.harness.rule.OwnerRule.ABOSII;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.version.Version;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.CDP)
public class ChartmuseumClientFactoryTest extends CategoryTest {
  private static final Version VERSION = Version.parse("0.14.0");
  private static final String CLI_PATH = "/usr/bin/chartmuseum";

  @Mock private ChartMuseumClientHelper clientHelper;

  @InjectMocks ChartmuseumClientFactory chartmuseumClientFactory;

  @Before
  public void before() {
    MockitoAnnotations.initMocks(this);

    doReturn(VERSION).when(clientHelper).getVersion(CLI_PATH);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testS3() {
    ChartmuseumClient result =
        chartmuseumClientFactory.s3(CLI_PATH, "bucket", "basePath", "region", true, null, null, false);
    assertThat(result).isInstanceOf(ChartmuseumS3Client.class);
    verify(clientHelper).getVersion(CLI_PATH);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testGcs() {
    ChartmuseumClient result =
        chartmuseumClientFactory.gcs(CLI_PATH, "bucket", "basePath", "json-content".toCharArray(), "./resources");
    assertThat(result).isInstanceOf(ChartmuseumGcsClient.class);
    verify(clientHelper).getVersion(CLI_PATH);
  }
}