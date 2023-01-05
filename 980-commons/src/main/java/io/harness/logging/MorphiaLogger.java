/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.logging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MorphiaLogger implements dev.morphia.logging.Logger {
  private Logger log;

  MorphiaLogger(Class<?> c) {
    log = LoggerFactory.getLogger(c);
  }

  @Override
  public void debug(String msg) {
    log.debug(msg);
  }

  @Override
  public void debug(String msg, Object... arg) {
    log.debug(msg, arg);
  }

  @Override
  public void debug(String msg, Throwable t) {
    log.debug(msg, t);
  }

  @Override
  public void error(String msg) {
    log.error(msg);
  }

  @Override
  public void error(String msg, Object... arg) {
    log.error(msg, arg);
  }

  @Override
  public void error(String msg, Throwable t) {
    log.error(msg, t);
  }

  @Override
  public void info(String msg) {
    log.info(msg);
  }

  @Override
  public void info(String msg, Object... arg) {
    log.info(msg, arg);
  }

  @Override
  public void info(String msg, Throwable t) {
    log.info(msg, t);
  }

  @Override
  public boolean isDebugEnabled() {
    return log.isDebugEnabled();
  }

  @Override
  public boolean isErrorEnabled() {
    return log.isErrorEnabled();
  }

  @Override
  public boolean isInfoEnabled() {
    return log.isInfoEnabled();
  }

  @Override
  public boolean isTraceEnabled() {
    return log.isTraceEnabled();
  }

  @Override
  public boolean isWarningEnabled() {
    return log.isWarnEnabled();
  }

  @Override
  public void trace(String msg) {
    log.trace(msg);
  }

  @Override
  public void trace(String msg, Object... arg) {
    log.trace(msg, arg);
  }

  @Override
  public void trace(String msg, Throwable t) {
    log.trace(msg, t);
  }

  @Override
  public void warning(String msg) {
    // suppress warning for elemMatch for InfrastructureProvisioner.mappingBlueprints
    if (("The type(s) for the query/update may be inconsistent; using an instance of type "
            + "'software.wings.dl.HQuery' for the field "
            + "'software.wings.beans.InfrastructureProvisioner.mappingBlueprints' "
            + "which is declared as 'java.util.List'")
            .equals(msg)) {
      return;
    }
    log.warn(msg);
  }

  @Override
  public void warning(String msg, Object... arg) {
    log.warn(msg, arg);
  }

  @Override
  public void warning(String msg, Throwable t) {
    log.warn(msg, t);
  }
}
