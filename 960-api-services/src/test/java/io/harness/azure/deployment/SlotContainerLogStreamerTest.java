/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.azure.deployment;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.ABOSII;
import static io.harness.rule.OwnerRule.TMACARI;

import static java.lang.String.format;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.azure.client.AzureWebClient;
import io.harness.azure.context.AzureWebClientContext;
import io.harness.category.element.UnitTests;
import io.harness.logging.LogCallback;
import io.harness.rule.Owner;

import com.microsoft.azure.management.appservice.DeploymentSlot;
import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Queue;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@OwnedBy(CDP)
public class SlotContainerLogStreamerTest extends CategoryTest {
  private static final int MAX_LOOP_COUNT = 30;
  private static final int LOG_TIME_STEP_SEC = 3;
  private static final String LOG_LINE_FORMAT = "%s %-5s - %s";
  private static final String SLOT_NAME = "test-slot";

  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  @Mock private AzureWebClientContext clientContext;
  @Mock private AzureWebClient webClient;
  @Mock private LogCallback logCallback;
  @Mock private DeploymentSlot deploymentSlot;

  private final Queue<List<LogRecord>> containerLogsPriority = new ArrayDeque<>();
  private final StringBuilder containerLogs = new StringBuilder();
  private ZonedDateTime timeCursor = null;

  private SlotContainerLogStreamer slotContainerLogStreamer;

  @Before
  public void setup() {
    doReturn(Optional.of(deploymentSlot)).when(webClient).getDeploymentSlotByName(clientContext, SLOT_NAME);

    doAnswer(invocation -> {
      if (!containerLogsPriority.isEmpty()) {
        if (timeCursor == null) {
          timeCursor = ZonedDateTime.now();
        }

        List<LogRecord> logRecords = containerLogsPriority.poll();
        timeCursor = timeCursor.plusSeconds(LOG_TIME_STEP_SEC);
        for (LogRecord logRecord : logRecords) {
          if (!logRecord.skip) {
            createLogLine(timeCursor, logRecord.level, logRecord.line);
          }
        }
      }

      return containerLogs.toString().getBytes(StandardCharsets.UTF_8);
    })
        .when(deploymentSlot)
        .getContainerLogs();
    initOldLogs();
    slotContainerLogStreamer = new SlotContainerLogStreamer(clientContext, webClient, SLOT_NAME, logCallback);
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testDDMMYYYYdateTimeParrternLogs() {
    DeploymentSlot deploymentSlot = mock(DeploymentSlot.class);
    doReturn(Optional.of(deploymentSlot)).when(webClient).getDeploymentSlotByName(clientContext, SLOT_NAME);
    when(deploymentSlot.getContainerLogs())
        .thenReturn("14/08/2022 12:41:38.167 STDOUT - Site: test log 1".getBytes(StandardCharsets.UTF_8))
        .thenReturn("14/08/2022 12:45:38.167 STDOUT - Site: test log 2".getBytes(StandardCharsets.UTF_8));
    SlotContainerLogStreamer slotContainerLogStreamer =
        new SlotContainerLogStreamer(clientContext, webClient, SLOT_NAME, logCallback);

    slotContainerLogStreamer.readContainerLogs();

    verify(logCallback).saveExecutionLog(contains("14/08/2022 12:45:38.167 STDOUT - Site: test log 2"));
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testReadContainerLogs() {
    addLogRecords(LogRecord.info("Pulling image: mcr.microsoft.com/azure-app-service/java:11-java11_211210001317"));
    addLogRecords(LogRecord.skipLog());
    addLogRecords(LogRecord.info("Starting container for site"),
        LogRecord.info(
            "Container test-container for site test-container initialized successfully and is ready to serve requests."));

    loopReadContainerLogs();

    assertThat(slotContainerLogStreamer.failed()).isFalse();
    assertThat(slotContainerLogStreamer.isSuccess()).isTrue();
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testReadContainerLogsTomcatSuccess() {
    addLogRecords(LogRecord.skipLog());
    addLogRecords(LogRecord.info("Starting container for site"));
    addLogRecords(
        LogRecord.info("Deployment of web application directory /usr/share/tomcat/Catalina/app has finished"));

    loopReadContainerLogs();

    assertThat(slotContainerLogStreamer.failed()).isFalse();
    assertThat(slotContainerLogStreamer.isSuccess()).isTrue();
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testReadContainerLogsContainerSuccess() {
    addLogRecords(LogRecord.info("initialized successfully and is ready to serve requests."));

    loopReadContainerLogs();

    assertThat(slotContainerLogStreamer.failed()).isFalse();
    assertThat(slotContainerLogStreamer.isSuccess()).isTrue();
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testReadContainerLogsFailureContainer() {
    addLogRecords(LogRecord.info("Pull Image successful, Time taken: 0 Minutes and 13 Seconds"));
    addLogRecords(LogRecord.info("Starting container for site"),
        LogRecord.info(
            "docker run -d -p 4322:80 --name test-container -e ... harnesscdp.azurecr.io/nginx:latest ./docker-entrypoint.sh"),
        LogRecord.info(
            "Logging is not enabled for this container. Please use https://aka.ms/linux-diagnostics to enable logging to see container logs here."));
    addLogRecords(LogRecord.skipLog());
    addLogRecords(LogRecord.skipLog());
    addLogRecords(LogRecord.info("Initiating warmup request to container test-container"));
    addLogRecords(LogRecord.skipLog());
    addLogRecords(LogRecord.error("Container test-container for site test-container has exited, failing site start"));
    addLogRecords(LogRecord.error(
        "Container test-container didn't respond to HTTP pings on port: 80, failing site start. See container logs for debugging."));

    assertThatThrownBy(this::loopReadContainerLogs)
        .hasMessageEndingWith(
            "Container test-container didn't respond to HTTP pings on port: 80, failing site start. See container logs for debugging.");

    assertThat(slotContainerLogStreamer.failed()).isTrue();
    assertThat(slotContainerLogStreamer.isSuccess()).isFalse();
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testReadContainerLogsFailureContainerStartup() {
    addLogRecords(LogRecord.info("Pull Image successful, Time taken: 0 Minutes and 13 Seconds"));
    addLogRecords(LogRecord.error(
        "Container start failed for test-container with System.AggregateException, One or more errors occurred. (Docker API responded with status code=BadRequest, response={\"message\":\"OCI runtime create failed: container_linux.go:380: starting container process caused: exec: \\\"echo\\\": executable file not found in $PATH: unknown\"} ) (Docker API responded with status code=BadRequest, response={\"message\":\"OCI runtime create failed: container_linux.go:380: starting container process caused: exec: \\\"echo\\\": executable file not found in $PATH: unknown\"} ) InnerException: Docker.DotNet.DockerApiException, Docker API responded with status code=BadRequest, response={\"message\":\"OCI runtime create failed: container_linux.go:380: starting container process caused: exec: \\\"echo\\\": executable file not found in $PATH: unknown\"}"));
    addLogRecords(LogRecord.error("Stopping site test-container because it failed during startup."));

    assertThatThrownBy(this::loopReadContainerLogs)
        .hasMessageEndingWith("Stopping site test-container because it failed during startup.");

    assertThat(slotContainerLogStreamer.failed()).isTrue();
    assertThat(slotContainerLogStreamer.isSuccess()).isFalse();
  }

  private void addLogRecords(LogRecord... logRecords) {
    containerLogsPriority.add(Arrays.asList(logRecords));
  }

  private void initOldLogs() {
    ZonedDateTime startPoint = ZonedDateTime.now().minusHours(2).minusMinutes(28);
    createLogLine(startPoint.plusSeconds(1), "INFO", "Deployment started");
    createLogLine(startPoint.plusSeconds(2), "INFO", "Deployment in progress...");
    createLogLine(startPoint.plusSeconds(3), "INFO", "Deployment completed");
  }

  private void createLogLine(ZonedDateTime dateTime, String level, String line) {
    containerLogs.append("\n");
    containerLogs.append(format(LOG_LINE_FORMAT, dateTime.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME), level, line));
  }

  private void loopReadContainerLogs() {
    int loopCounter = 0;
    while (!slotContainerLogStreamer.isSuccess() && !slotContainerLogStreamer.failed()) {
      slotContainerLogStreamer.readContainerLogs();
      loopCounter++;
      if (loopCounter > MAX_LOOP_COUNT) {
        fail("Break condition wasn't meet in " + MAX_LOOP_COUNT + " cycles");
      }
    }
  }

  private static class LogRecord {
    String level;
    String line;
    boolean skip;

    LogRecord(String level, String line) {
      this.level = level;
      this.line = line;
    }

    LogRecord(boolean skip) {
      this.skip = skip;
    }

    static LogRecord info(String line) {
      return new LogRecord("INFO", line);
    }

    static LogRecord error(String line) {
      return new LogRecord("ERROR", line);
    }

    static LogRecord skipLog() {
      return new LogRecord(true);
    }
  }
}