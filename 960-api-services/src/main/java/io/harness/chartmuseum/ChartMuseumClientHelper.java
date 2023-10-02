/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.chartmuseum;
import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.chartmuseum.ChartMuseumConstants.ADDRESS_BIND_CODE;
import static io.harness.chartmuseum.ChartMuseumConstants.ADDRESS_BIND_ERROR;
import static io.harness.chartmuseum.ChartMuseumConstants.AWS_ACCESS_KEY_ID;
import static io.harness.chartmuseum.ChartMuseumConstants.AWS_SECRET_ACCESS_KEY;
import static io.harness.chartmuseum.ChartMuseumConstants.BUCKET_REGION_ERROR_CODE;
import static io.harness.chartmuseum.ChartMuseumConstants.CHART_MUSEUM_SERVER_HOST;
import static io.harness.chartmuseum.ChartMuseumConstants.CHART_MUSEUM_SERVER_START_RETRIES;
import static io.harness.chartmuseum.ChartMuseumConstants.CHART_MUSEUM_SERVER_URL;
import static io.harness.chartmuseum.ChartMuseumConstants.CHART_MUSEUM_SERVICE_START_TIMEOUT;
import static io.harness.chartmuseum.ChartMuseumConstants.HEALTH_CHECK_TIME_GAP_SECONDS;
import static io.harness.chartmuseum.ChartMuseumConstants.INVALID_ACCESS_KEY_ID_ERROR;
import static io.harness.chartmuseum.ChartMuseumConstants.INVALID_ACCESS_KEY_ID_ERROR_CODE;
import static io.harness.chartmuseum.ChartMuseumConstants.NO_SUCH_BBUCKET_ERROR;
import static io.harness.chartmuseum.ChartMuseumConstants.NO_SUCH_BBUCKET_ERROR_CODE;
import static io.harness.chartmuseum.ChartMuseumConstants.PORTS_BOUND;
import static io.harness.chartmuseum.ChartMuseumConstants.PORTS_START_POINT;
import static io.harness.chartmuseum.ChartMuseumConstants.SERVER_HEALTH_CHECK_RETRIES;
import static io.harness.chartmuseum.ChartMuseumConstants.SIGNATURE_DOES_NOT_MATCH_ERROR;
import static io.harness.chartmuseum.ChartMuseumConstants.SIGNATURE_DOES_NOT_MATCH_ERROR_CODE;
import static io.harness.chartmuseum.ChartMuseumConstants.VERSION;
import static io.harness.chartmuseum.ChartMuseumConstants.VERSION_PATTERN;
import static io.harness.exception.ExceptionUtils.getMessage;
import static io.harness.threading.Morpheus.sleep;

import static java.lang.String.format;
import static java.time.Duration.ofSeconds;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.version.Version;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.network.Http;
import io.harness.shell.ScriptProcessExecutor;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Singleton;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.Socket;
import java.net.SocketException;
import java.net.URL;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.zeroturnaround.exec.ProcessExecutor;
import org.zeroturnaround.exec.ProcessResult;
import org.zeroturnaround.exec.StartedProcess;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_K8S})
@OwnedBy(CDP)
@Slf4j
@Singleton
public class ChartMuseumClientHelper {
  // Minimal Chartmuseum version to fallback to
  private static final Version CHARTMUSEUM_DEFAULT_VERSION = Version.parse("0.0.1");
  private static final SecureRandom random = new SecureRandom();
  private static final String CHARTMUSEUM_SERVER_STARTUP_FAILURE_MESSAGE =
      "Could not start chart museum server. Failed after %s retries %n";
  private static final String ADDRESS_ALREADY_IN_USE_REGEX = "tcp :(\\d+): bind: address already in use";
  private static final Pattern ADDRESS_ALREADY_IN_USE_PATTERN =
      Pattern.compile(ADDRESS_ALREADY_IN_USE_REGEX, Pattern.MULTILINE);

  public void stopChartMuseumServer(StartedProcess process) {
    try {
      if (process != null) {
        process.getProcess().destroyForcibly().waitFor();
        log.info("Successfully stopped chart museum server");
      } else {
        log.info(
            "Not able to find any process associated with the chart museum server. It may have already been stopped");
      }
    } catch (Exception ex) {
      log.warn("Failed to stop chart museum server " + getMessage(ex));
    }
  }

  public ChartMuseumServer startServer(String command, Map<String, String> environment) throws IOException {
    int port = 0;
    StartedProcess process = null;
    int retries = 0;
    StringBuffer stringBuffer = null;

    log.info(command);

    while (retries < CHART_MUSEUM_SERVER_START_RETRIES) {
      port = getNextRandomPort(random);
      String commandWithPort = command.replace("${PORT}", Integer.toString(port));
      log.info("Starting server at port {}. Retry #{}", port, retries);

      stringBuffer = new StringBuffer();
      process = startProcess(commandWithPort, environment, stringBuffer);

      if (waitForServerReady(process, port)) {
        log.info(stringBuffer.toString());

        if (waitForServiceReady(port)) {
          log.info("Chart museum service available at  port {}", port);
        }

        break;
      } else {
        String processOutput = stringBuffer.toString();
        log.info(processOutput);

        if (checkAddressInUseError(processOutput, port)) {
          retries++;
        } else {
          break;
        }
      }
    }

    if (!isPortInUse(port)) {
      log.error("Port {} is still not in use", port);
    }

    if (process == null || !process.getProcess().isAlive()) {
      throw new InvalidRequestException(getErrorMessage(stringBuffer.toString()), WingsException.USER);
    }

    return ChartMuseumServer.builder().startedProcess(process).port(port).build();
  }

  public Version getVersion(String cliPath) {
    try {
      ProcessResult versionResult = executeCommand(cliPath + ' ' + VERSION);

      if (versionResult.getExitValue() != 0) {
        log.warn("Failed to get chartmuseum version. Exit code: {}, output: {}", versionResult.getExitValue(),
            versionResult.hasOutput() ? versionResult.outputUTF8() : "no output");
        return CHARTMUSEUM_DEFAULT_VERSION;
      }

      if (versionResult.hasOutput()) {
        String versionOutput = versionResult.outputUTF8();
        Matcher versionMatcher = VERSION_PATTERN.matcher(versionOutput);
        if (!versionMatcher.find()) {
          log.warn("No valid chartmuseum version present in output: {}", versionOutput);
          return CHARTMUSEUM_DEFAULT_VERSION;
        }

        return Version.parse(versionMatcher.group(1));
      }

    } catch (IOException | InterruptedException | TimeoutException e) {
      log.error("Failed to get chartmuseum version", e);
    }

    return CHARTMUSEUM_DEFAULT_VERSION;
  }

  @VisibleForTesting
  StartedProcess startProcess(String command, Map<String, String> environment, StringBuffer stringBuffer)
      throws IOException {
    try (ScriptProcessExecutor.StringBufferOutputStream stringBufferOutputStream =
             new ScriptProcessExecutor.StringBufferOutputStream(stringBuffer)) {
      return new ProcessExecutor()
          .environment(environment)
          .timeout(5, TimeUnit.MINUTES)
          .commandSplit(command)
          .readOutput(true)
          .redirectOutput(stringBufferOutputStream)
          .redirectError(stringBufferOutputStream)
          .start();
    }
  }

  @VisibleForTesting
  ProcessResult executeCommand(String command) throws IOException, InterruptedException, TimeoutException {
    return new ProcessExecutor().commandSplit(command).readOutput(true).execute();
  }

  public Map<String, String> getEnvForAwsConfig(
      char[] accessKey, char[] secretKey, boolean useIamCredentials, boolean useIrsa) {
    Map<String, String> environment = new HashMap<>();
    if (useIrsa) {
      environment.put("AWS_ROLE_ARN", System.getenv("AWS_ROLE_ARN"));
      environment.put("AWS_WEB_IDENTITY_TOKEN_FILE", System.getenv("AWS_WEB_IDENTITY_TOKEN_FILE"));
      environment.put("AWS_ROLE_SESSION_NAME", "aws-sdk-java-" + System.currentTimeMillis());

      // adding this env variable allows access to IRSA creds:
      environment.put("AWS_SDK_LOAD_CONFIG", "true");
    } else if (!useIamCredentials) {
      environment.put(AWS_ACCESS_KEY_ID, new String(accessKey));
      environment.put(AWS_SECRET_ACCESS_KEY, new String(secretKey));
    }
    return environment;
  }

  @VisibleForTesting
  static int getNextRandomPort(Random rand) {
    return rand.nextInt(PORTS_BOUND) + PORTS_START_POINT;
  }

  private static boolean waitForServerReady(StartedProcess process, int port) {
    int count = -1;

    while (count < SERVER_HEALTH_CHECK_RETRIES) {
      log.info("Waiting for chart museum server to get ready");
      count++;
      sleep(ofSeconds(HEALTH_CHECK_TIME_GAP_SECONDS));

      if (!process.getProcess().isAlive()) {
        return false;
      }

      if (isPortInUse(port)) {
        return true;
      }
    }

    return false;
  }

  protected static boolean waitForServiceReady(int port) {
    log.info("Waiting for chart museum service to get ready");
    if (Http.connectableHost(CHART_MUSEUM_SERVER_HOST, port, CHART_MUSEUM_SERVICE_START_TIMEOUT)) {
      if (isServiceUpAndRunning(port)) {
        return true;
      }
    }
    log.error("Chart museum service failed to start!");
    return false;
  }

  private static boolean isServiceUpAndRunning(int port) {
    HttpURLConnection connection = null;
    try {
      connection = (HttpURLConnection) new URL(
          format("%s/health", CHART_MUSEUM_SERVER_URL.replace("${PORT}", String.valueOf(port))))
                       .openConnection();
      connection.setRequestMethod("GET");
      connection.connect();
      int code = connection.getResponseCode();
      log.info("Chart museum health status response code {}", code);
      if (code >= 200 && code < 300) {
        return true;
      }
    } catch (IOException e) {
      log.error("Failed to check chart museum health API", e);
    } finally {
      if (connection != null) {
        connection.disconnect();
      }
    }
    return false;
  }

  private static boolean isPortInUse(int port) {
    // Assume no connection is possible.
    boolean result = false;

    try {
      (new Socket("localhost", port)).close();
      result = true;
    } catch (SocketException e) {
      // Could not connect.
    } catch (Exception e) {
      result = true;
    }

    return result;
  }

  private static String getErrorMessage(String processOutput) {
    log.warn(String.format("Could not start Chart museum server. Process output: [%s]", processOutput));
    String errorPrefix = "Failed with error: ";

    if (processOutput.contains(NO_SUCH_BBUCKET_ERROR_CODE)) {
      return errorPrefix + NO_SUCH_BBUCKET_ERROR;
    }

    if (processOutput.contains(INVALID_ACCESS_KEY_ID_ERROR_CODE)) {
      return errorPrefix + INVALID_ACCESS_KEY_ID_ERROR;
    }

    if (processOutput.contains(SIGNATURE_DOES_NOT_MATCH_ERROR_CODE)) {
      return errorPrefix + SIGNATURE_DOES_NOT_MATCH_ERROR;
    }

    if (processOutput.contains(BUCKET_REGION_ERROR_CODE)) {
      String[] outputLines = processOutput.split(System.lineSeparator());

      for (String line : outputLines) {
        if (line.contains(BUCKET_REGION_ERROR_CODE)) {
          return errorPrefix + line.substring(line.indexOf(BUCKET_REGION_ERROR_CODE));
        }
      }

      return errorPrefix + BUCKET_REGION_ERROR_CODE;
    }

    if (processOutput.contains(ADDRESS_BIND_CODE)) {
      String port = extractPortFromAddressInUseMessage(processOutput);
      return format(CHARTMUSEUM_SERVER_STARTUP_FAILURE_MESSAGE, CHART_MUSEUM_SERVER_START_RETRIES)
          + format(ADDRESS_BIND_ERROR, port);
    }

    return format(CHARTMUSEUM_SERVER_STARTUP_FAILURE_MESSAGE, CHART_MUSEUM_SERVER_START_RETRIES);
  }

  private static boolean checkAddressInUseError(String processOutput, int port) {
    return processOutput.contains(format("listen tcp :%d: bind: address already in use", port));
  }

  private static String extractPortFromAddressInUseMessage(String errorMessage) {
    Matcher matcher = ADDRESS_ALREADY_IN_USE_PATTERN.matcher(errorMessage);
    return matcher.find() ? matcher.group(1) : "";
  }
}
