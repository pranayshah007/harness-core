/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.logging;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.provision.TerraformConstants.PLAN_HUMAN_READABLE_FILE_NAME;
import static io.harness.provision.TerraformConstants.PLAN_HUMAN_READABLE_FILE_PREFIX;

import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.TerraformCommandExecutionException;
import io.harness.exception.WingsException;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import lombok.extern.slf4j.Slf4j;
import org.zeroturnaround.exec.stream.LogOutputStream;

@Slf4j
@OwnedBy(CDP)
public class PlanHumanReadableOutputStream extends LogOutputStream {
  private Path planHumanReadableLocalFilePath;
  private OutputStream humanReadablePlanOutputStream;
  private boolean closed;

  private String humanReadablePlan;
  private static String NEW_LINE = "\n";
  @Override
  protected void processLine(String s) {
    // Only append when not empty, Add a new line after the string so it looks better as a plan in the end
    if (!s.isEmpty()) {
      humanReadablePlan += s + NEW_LINE;
    }
  }

  @Override
  public void write(int cc) throws IOException {
    getHumanReadablePlanOutputStream().write(cc);
  }

  @Override
  public void write(byte[] b, int off, int len) throws IOException {
    getHumanReadablePlanOutputStream().write(b, off, len);
  }

  @Override
  public void flush() {
    try {
      getHumanReadablePlanOutputStream().flush();
    } catch (IOException e) {
      throw new TerraformCommandExecutionException("Failed to flush Human Readable Plan", WingsException.USER_SRE, e);
    }
  }

  @Override
  public void close() throws IOException {
    if (closed) {
      return;
    }

    if (humanReadablePlanOutputStream != null) {
      humanReadablePlanOutputStream.close();
    }

    super.close();
    this.closed = true;
  }

  public String getHumanReadablePlan() {
    if (getTfHumanReadablePlanLocalPath() != null) {
      try (FileInputStream fis = new FileInputStream(getTfHumanReadablePlanLocalPath());
           GZIPInputStream tfDecompressedInputStream = new GZIPInputStream(fis);) {
        byte[] buffer = new byte[4096];
        StringBuilder sb = new StringBuilder();
        while (tfDecompressedInputStream.read(buffer) != -1) {
          sb.append(new String(buffer));
          buffer = new byte[4096];
        }
        return sb.toString();
      } catch (IOException e) {
        log.error("Couldn't read the File HumanReadable Plan into a String", e);
      }
    }
    return humanReadablePlan;
  }

  public String getTfHumanReadablePlanLocalPath() {
    if (planHumanReadableLocalFilePath != null) {
      return planHumanReadableLocalFilePath.toAbsolutePath().toString();
    }
    return null;
  }

  private OutputStream getHumanReadablePlanOutputStream() throws IOException {
    if (humanReadablePlanOutputStream == null) {
      planHumanReadableLocalFilePath = Paths.get(
          Files.createTempDirectory(PLAN_HUMAN_READABLE_FILE_PREFIX).toString(), PLAN_HUMAN_READABLE_FILE_NAME);
      humanReadablePlanOutputStream =
          new GZIPOutputStream(new FileOutputStream(planHumanReadableLocalFilePath.toAbsolutePath().toString()));
    }

    return humanReadablePlanOutputStream;
  }
}
