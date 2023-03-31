/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.chartmuseum;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.chartmuseum.ChartMuseumConstants.DISABLE_STATEFILES;
import static io.harness.rule.OwnerRule.ABOSII;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.version.Version;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import com.google.common.collect.ImmutableMap;
import java.io.IOException;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.zeroturnaround.exec.StartedProcess;

@OwnedBy(CDP)
public class AbstractChartmuseumClientTest extends CategoryTest {
  private static final String CHARTMUSEUM_BIN = "/usr/local/bin/chartmuseum";
  private static final Version version012 = Version.parse("0.12.0");
  private static final Version version013 = Version.parse("0.13.1");

  @Mock private ChartMuseumClientHelper clientHelper;
  @Mock private ChartMuseumServer chartMuseumServer;
  @Mock private StartedProcess startedProcess;

  @Before
  public void setup() throws IOException {
    MockitoAnnotations.initMocks(this);

    doReturn(chartMuseumServer).when(clientHelper).startServer(anyString(), anyMap());
    doReturn(startedProcess).when(chartMuseumServer).getStartedProcess();
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testStartServerVersion012() throws IOException {
    testStartServer(version012, "--storage=amazon", CHARTMUSEUM_BIN + " --storage=amazon");
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testStartServerVersion013() throws IOException {
    testStartServer(version013, "--storage=google", CHARTMUSEUM_BIN + " --storage=google " + DISABLE_STATEFILES);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testStop() {
    AbstractChartmuseumClient client = new AbstractChartmuseumClient(clientHelper, CHARTMUSEUM_BIN, version012) {
      @Override
      public ChartMuseumServer start() {
        return null;
      }
    };

    client.stop(chartMuseumServer);

    verify(clientHelper).stopChartMuseumServer(startedProcess);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testStopNullChartmuseumServer() {
    AbstractChartmuseumClient client = new AbstractChartmuseumClient(clientHelper, CHARTMUSEUM_BIN, version012) {
      @Override
      public ChartMuseumServer start() {
        return null;
      }
    };

    client.stop(null);

    verify(clientHelper, never()).stopChartMuseumServer(any(StartedProcess.class));
  }

  private void testStartServer(Version version, String args, String expectedCommand) throws IOException {
    AbstractChartmuseumClient client = new AbstractChartmuseumClient(clientHelper, CHARTMUSEUM_BIN, version) {
      @Override
      public ChartMuseumServer start() throws IOException {
        return startServer(args, ImmutableMap.of("key", "test-key"));
      }
    };

    ArgumentCaptor<String> commandCaptor = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<Map> envCaptor = ArgumentCaptor.forClass(Map.class);

    ChartMuseumServer server = client.start();
    assertThat(server).isSameAs(chartMuseumServer);

    verify(clientHelper).startServer(commandCaptor.capture(), envCaptor.capture());
    assertThat(commandCaptor.getValue()).isEqualTo(expectedCommand);
    assertThat(envCaptor.getValue()).isEqualTo(ImmutableMap.of("key", "test-key"));
  }
}