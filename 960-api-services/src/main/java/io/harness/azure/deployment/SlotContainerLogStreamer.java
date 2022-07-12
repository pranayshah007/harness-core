/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.azure.deployment;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.azure.model.AzureConstants.DEPLOYMENT_SLOT_PRODUCTION_NAME;
import static io.harness.azure.model.AzureConstants.FAIL_DEPLOYMENT_ERROR_MSG;
import static io.harness.azure.model.AzureConstants.TIME_PATTERN;
import static io.harness.azure.model.AzureConstants.TIME_STAMP_REGEX;
import static io.harness.azure.model.AzureConstants.containerSuccessPattern;
import static io.harness.azure.model.AzureConstants.deploymentLogPattern;
import static io.harness.azure.model.AzureConstants.failureContainerLogPattern;
import static io.harness.azure.model.AzureConstants.tomcatSuccessPattern;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.logging.CommandExecutionStatus.FAILURE;
import static io.harness.logging.LogLevel.ERROR;

import io.harness.annotations.dev.OwnedBy;
import io.harness.azure.client.AzureWebClient;
import io.harness.azure.context.AzureWebClientContext;
import io.harness.exception.InvalidRequestException;
import io.harness.logging.LogCallback;

import com.microsoft.azure.management.appservice.DeploymentSlot;
import com.microsoft.azure.management.appservice.WebApp;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

@OwnedBy(CDP)
public class SlotContainerLogStreamer {
  private final AzureWebClientContext azureWebClientContext;
  private final AzureWebClient azureWebClient;
  private final String slotName;
  private final LogCallback logCallback;
  private DateTime lastTime;
  private boolean hasFailed;
  private boolean isSuccess;
  private String errorLog;

  private static final DateTimeFormatter withZoneUTC = DateTimeFormat.forPattern(TIME_PATTERN).withZoneUTC();

  public SlotContainerLogStreamer(AzureWebClientContext azureWebClientContext, AzureWebClient azureWebClient,
      String slotName, LogCallback logCallback) {
    this.azureWebClientContext = azureWebClientContext;
    this.azureWebClient = azureWebClient;
    this.slotName = slotName;
    this.logCallback = logCallback;
    initializeLastTime();
  }

  private void initializeLastTime() {
    if (DEPLOYMENT_SLOT_PRODUCTION_NAME.equals(slotName)) {
      Optional<WebApp> azureWebApp = azureWebClient.getWebAppByName(azureWebClientContext);
      azureWebApp.ifPresent(app -> {
        byte[] containerLogs = app.getContainerLogs();
        String log = new String(containerLogs);
        parseLastTime(log);
      });
    } else {
      Optional<DeploymentSlot> deploymentSlotByName =
          azureWebClient.getDeploymentSlotByName(azureWebClientContext, slotName);
      deploymentSlotByName.ifPresent(slot -> {
        byte[] containerLogs = slot.getContainerLogs();
        String log = new String(containerLogs);
        parseLastTime(log);
      });
    }
  }

  private void parseLastTime(String log) {
    Pattern timePattern = Pattern.compile(TIME_STAMP_REGEX);
    Matcher matcher = timePattern.matcher(log);
    int timeStampBeginIndex = 0;
    int timeStampEndIndex = 0;

    while (matcher.find()) {
      timeStampBeginIndex = matcher.start();
      timeStampEndIndex = matcher.end();
    }
    String timeStamp = log.substring(timeStampBeginIndex, timeStampEndIndex).trim();
    lastTime =
        isEmpty(timeStamp) ? new DateTime(DateTimeZone.UTC) : withZoneUTC.parseDateTime(timeStamp).plusMinutes(1);
    logCallback.saveExecutionLog(String.format("Star time for container log watching - [%s]", lastTime));
  }

  public void readContainerLogs() {
    String containerLogs;
    if (DEPLOYMENT_SLOT_PRODUCTION_NAME.equalsIgnoreCase(slotName)) {
      Optional<WebApp> azureApp = azureWebClient.getWebAppByName(azureWebClientContext);
      if (!azureApp.isPresent()) {
        throw new IllegalArgumentException("WebApp not found - " + azureWebClientContext.getAppName());
      }
      containerLogs = new String(azureApp.get().getContainerLogs());
    } else {
      Optional<DeploymentSlot> slot = azureWebClient.getDeploymentSlotByName(azureWebClientContext, slotName);
      if (!slot.isPresent()) {
        throw new IllegalArgumentException("Slot not found - " + slotName);
      }
      containerLogs = new String(slot.get().getContainerLogs());
    }

    logCallback.saveExecutionLog("Fetching latest container logs ... ");
    boolean noNewContainerLogFound = true;

    Pattern timePattern = Pattern.compile(TIME_STAMP_REGEX);
    Matcher matcher = timePattern.matcher(containerLogs);
    int timeStampBeginIndex = 0;
    DateTime dateTime = new DateTime(DateTimeZone.UTC).minusDays(365);
    if (matcher.find()) {
      timeStampBeginIndex = matcher.start();
      dateTime = withZoneUTC.parseDateTime(matcher.group().trim());
    }

    while (matcher.find() && operationNotCompleted()) {
      if (dateTime.isAfter(lastTime)) {
        String logLine = containerLogs.substring(timeStampBeginIndex, matcher.start());
        logCallback.saveExecutionLog(logLine.replaceAll("[\\n]+", " "));
        verifyContainerLogLine(logLine);
        noNewContainerLogFound = false;
      }
      dateTime = withZoneUTC.parseDateTime(matcher.group().trim());
      timeStampBeginIndex = matcher.start();
    }

    if ((timeStampBeginIndex < containerLogs.length()) && operationNotCompleted() && dateTime.isAfter(lastTime)) {
      String logLine = containerLogs.substring(timeStampBeginIndex);
      logCallback.saveExecutionLog(logLine);
      verifyContainerLogLine(logLine);
      noNewContainerLogFound = false;
    }
    if (noNewContainerLogFound) {
      logCallback.saveExecutionLog("No new container log found ... ");
    }
    lastTime = dateTime;
  }

  private void verifyContainerLogLine(String logLine) {
    if (operationFailed(logLine)) {
      hasFailed = true;
      errorLog = logLine;
      logCallback.saveExecutionLog(String.format(FAIL_DEPLOYMENT_ERROR_MSG, slotName, logLine), ERROR, FAILURE);
      throw new InvalidRequestException(errorLog);
    }
    Matcher deploymentLogMatcher = deploymentLogPattern.matcher(logLine);
    Matcher containerLogMatcher = containerSuccessPattern.matcher(logLine);
    Matcher tomcatLogMatcher = tomcatSuccessPattern.matcher(logLine);

    isSuccess = deploymentLogMatcher.find() || containerLogMatcher.find() || tomcatLogMatcher.find();
  }

  private boolean operationFailed(String logLine) {
    Matcher matcher = failureContainerLogPattern.matcher(logLine);
    return matcher.find();
  }

  public String getErrorLog() {
    return errorLog;
  }

  public boolean failed() {
    return hasFailed;
  }

  public boolean isSuccess() {
    return isSuccess;
  }

  private boolean operationNotCompleted() {
    return !hasFailed && !isSuccess;
  }
}
