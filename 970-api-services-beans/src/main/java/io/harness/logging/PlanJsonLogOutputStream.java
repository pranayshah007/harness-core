/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.logging;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.TerraformCommandExecutionException;
import io.harness.exception.WingsException;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.zip.GZIPOutputStream;
import org.zeroturnaround.exec.stream.LogOutputStream;

@OwnedBy(CDP)
public class PlanJsonLogOutputStream extends LogOutputStream {
  private static final String PLAN_JSON_FILE_PREFIX = "harnessTfJsonPlan";
  private static final String PLAN_JSON_FILE_NAME = "tfPlan.json";

  private String planJson;
  private Path planJsonLocalFilePath;
  private OutputStream planJsonOutputStream;
  private boolean saveLocal;
  private boolean closed;
  private CommandExecutionStatus tfPlanShowJsonStatus;

  public PlanJsonLogOutputStream() {}

  public PlanJsonLogOutputStream(boolean saveLocal) {
    this.saveLocal = saveLocal;
  }

  @Override
  public void write(int cc) throws IOException {
    if (!saveLocal) {
      super.write(cc);
    }

    getPlanJsonOutputStream().write(cc);
  }

  @Override
  public void write(byte[] b, int off, int len) throws IOException {
    if (!saveLocal) {
      super.write(b, off, len);
    }

    getPlanJsonOutputStream().write(b, off, len);
  }

  @Override
  public void flush() {
    if (!saveLocal) {
      super.flush();
    }

    try {
      getPlanJsonOutputStream().flush();
    } catch (IOException e) {
      throw new TerraformCommandExecutionException("Failed to flush plan json", WingsException.USER_SRE, e);
    }
  }

  @Override
  public void close() throws IOException {
    if (closed) {
      return;
    }

    if (saveLocal && planJsonOutputStream != null) {
      planJsonOutputStream.close();
    }

    super.close();
    this.closed = true;
  }

  @Override
  protected void processLine(String line) {
    planJson = line;
  }

  public String getPlanJson() {
    return planJson;
  }

  public String getTfPlanJsonLocalPath() {
    return planJsonLocalFilePath.toAbsolutePath().toString();
  }

  private OutputStream getPlanJsonOutputStream() throws IOException {
    if (planJsonOutputStream == null) {
      planJsonLocalFilePath =
          Paths.get(Files.createTempDirectory(PLAN_JSON_FILE_PREFIX).toString(), PLAN_JSON_FILE_NAME);
      planJsonOutputStream =
          new GZIPOutputStream(new FileOutputStream(planJsonLocalFilePath.toAbsolutePath().toString()));
    }

    return planJsonOutputStream;
  }

  public CommandExecutionStatus getTfPlanShowJsonStatus() {
    return tfPlanShowJsonStatus;
  }

  public void setTfPlanShowJsonStatus(CommandExecutionStatus tfPlanShowJsonStatus) {
    this.tfPlanShowJsonStatus = tfPlanShowJsonStatus;
  }
}
