/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.chartmuseum;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.chartmuseum.ChartMuseumConstants.DISABLE_STATEFILES;
import static io.harness.k8s.kubectl.Utils.encloseWithQuotesIfNeeded;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.version.Version;
import io.harness.exception.sanitizer.ExceptionMessageSanitizer;

import java.io.IOException;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(CDP)
public abstract class AbstractChartmuseumClient implements ChartmuseumClient {
  private static final Version VERSION_0_13 = Version.parse("0.13");

  private final String cliPath;
  private final Version version;

  // Injecting clientHelper to reuse existing logic and better testability. It can be replaced in future
  protected final ChartMuseumClientHelper clientHelper;

  public AbstractChartmuseumClient(ChartMuseumClientHelper clientHelper, String cliPath, Version version) {
    this.clientHelper = clientHelper;
    this.cliPath = cliPath;
    this.version = version;
  }

  @Override
  public void stop(ChartMuseumServer server) {
    if (server == null) {
      return;
    }

    clientHelper.stopChartMuseumServer(server.getStartedProcess());
  }

  protected ChartMuseumServer startServer(String arguments, Map<String, String> environment) throws IOException {
    try {
      log.info("Starting chartmuseum server using binary: {}", cliPath);
      StringBuilder command = new StringBuilder(128);
      command.append(encloseWithQuotesIfNeeded(cliPath)).append(' ').append(arguments);

      if (version.compareTo(VERSION_0_13) >= 0) {
        command.append(' ').append(DISABLE_STATEFILES);
      }

      return clientHelper.startServer(command.toString(), environment);
    } catch (IOException e) {
      throw ExceptionMessageSanitizer.sanitizeException(e);
    } catch (RuntimeException e) {
      throw ExceptionMessageSanitizer.sanitizeException(e);
    }
  }
}
