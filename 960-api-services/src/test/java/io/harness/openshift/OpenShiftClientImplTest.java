/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.openshift;

import static io.harness.rule.OwnerRule.VAIBHAV_SI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.cli.CliHelper;
import io.harness.cli.CliResponse;
import io.harness.exception.InvalidRequestException;
import io.harness.logging.LogCallback;
import io.harness.rule.Owner;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeoutException;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class OpenShiftClientImplTest extends CategoryTest {
  @Mock private CliHelper cliHelper;
  @Mock private LogCallback logCallback;
  @InjectMocks private OpenShiftClientImpl openShiftClient;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = VAIBHAV_SI)
  @Category(UnitTests.class)
  public void processCommandShouldBeAsExpectedWithoutParams()
      throws InterruptedException, TimeoutException, IOException {
    String OC_BINARY_PATH = "OC_BINARY_PATH";
    String TEMPLATE_FILE_PATH = "TEMPLATE_FILE_PATH";
    String MANIFEST_DIRECTORY_PATH = "MANIFEST_DIRECTORY_PATH";

    String expectedCommand = "OC_BINARY_PATH process -f TEMPLATE_FILE_PATH --local -o yaml";
    CliResponse expectedCliResponse = CliResponse.builder().build();

    doReturn(expectedCliResponse)
        .when(cliHelper)
        .executeCliCommand(expectedCommand, io.harness.openshift.OpenShiftConstants.COMMAND_TIMEOUT,
            Collections.emptyMap(), MANIFEST_DIRECTORY_PATH, logCallback);

    CliResponse cliResponse =
        openShiftClient.process(OC_BINARY_PATH, TEMPLATE_FILE_PATH, null, MANIFEST_DIRECTORY_PATH, logCallback);

    assertThat(cliResponse).isEqualTo(expectedCliResponse);
  }

  @Test
  @Owner(developers = VAIBHAV_SI)
  @Category(UnitTests.class)
  public void processCommandShouldBeAsExpectedWithParams() throws InterruptedException, TimeoutException, IOException {
    String OC_BINARY_PATH = "OC_BINARY_PATH";
    String TEMPLATE_FILE_PATH = "TEMPLATE_FILE_PATH";
    String MANIFEST_DIRECTORY_PATH = "MANIFEST_DIRECTORY_PATH";
    List<String> paramsFilePaths = Arrays.asList("params1", "params2");

    String expectedCommand =
        "OC_BINARY_PATH process -f TEMPLATE_FILE_PATH --local -o yaml --param-file params1 --param-file params2";
    CliResponse expectedCliResponse = CliResponse.builder().build();

    doReturn(expectedCliResponse)
        .when(cliHelper)
        .executeCliCommand(expectedCommand, OpenShiftConstants.COMMAND_TIMEOUT, Collections.emptyMap(),
            MANIFEST_DIRECTORY_PATH, logCallback);

    CliResponse cliResponse = openShiftClient.process(
        OC_BINARY_PATH, TEMPLATE_FILE_PATH, paramsFilePaths, MANIFEST_DIRECTORY_PATH, logCallback);

    assertThat(cliResponse).isEqualTo(expectedCliResponse);
  }

  @Test
  @Owner(developers = VAIBHAV_SI)
  @Category(UnitTests.class)
  public void processCommandShouldHandleExceptions() throws InterruptedException, TimeoutException, IOException {
    String RANDOM_STRING = "RANDOM_STRING";
    List<String> paramsFilePaths = Arrays.asList("params1", "params2");

    doThrow(new TimeoutException("timed out")).when(cliHelper).executeCliCommand(any(), anyLong(), any(), any(), any());
    assertThatThrownBy(
        () -> openShiftClient.process(RANDOM_STRING, RANDOM_STRING, paramsFilePaths, RANDOM_STRING, logCallback))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Timed out while running oc process.");

    doThrow(InterruptedException.class).when(cliHelper).executeCliCommand(any(), anyLong(), any(), any(), any());
    assertThatThrownBy(
        () -> openShiftClient.process(RANDOM_STRING, RANDOM_STRING, paramsFilePaths, RANDOM_STRING, logCallback))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Thread interrupted while running oc process. Try again.");

    doThrow(new IOException("Don't have permissions"))
        .when(cliHelper)
        .executeCliCommand(any(), anyLong(), any(), any(), any());
    assertThatThrownBy(
        () -> openShiftClient.process(RANDOM_STRING, RANDOM_STRING, paramsFilePaths, RANDOM_STRING, logCallback))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("IO Failure occurred while running oc process. Don't have permissions");
  }
}
