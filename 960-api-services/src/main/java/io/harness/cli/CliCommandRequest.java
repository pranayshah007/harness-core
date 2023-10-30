/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cli;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;
import io.harness.logging.LogCallback;

import java.util.Map;
import lombok.Builder;
import lombok.Data;
import org.zeroturnaround.exec.stream.LogOutputStream;

@Data
@Builder
@OwnedBy(CDP)
public class CliCommandRequest {
  private String command;
  private long timeoutInMillis;
  private Map<String, String> envVariables;
  private String directory;
  private LogCallback logCallback;
  private String loggingCommand;
  private LogOutputStream logOutputStream;
  private ErrorLogOutputStream errorLogOutputStream;
  private long secondsToWaitForGracefulShutdown;
}
