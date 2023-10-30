/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.logging.common;

import ch.qos.logback.classic.pattern.ThrowableHandlingConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.StackTraceElementProxy;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import ch.qos.logback.core.CoreConstants;

/**
 * See {@link ch.qos.logback.classic.pattern.ThrowableProxyConverter}
 */
public class StackTraceProxyConverter extends ThrowableHandlingConverter {
  private static final int BUILDER_CAPACITY = 2048;

  @Override
  public String convert(ILoggingEvent event) {
    IThrowableProxy tp = event.getThrowableProxy();
    if (tp == null) {
      return CoreConstants.EMPTY_STRING;
    }

    return throwableProxyToString(tp);
  }

  private String throwableProxyToString(IThrowableProxy tp) {
    StringBuilder sb = new StringBuilder(BUILDER_CAPACITY);
    recursiveAppend(sb, null, ThrowableProxyUtil.REGULAR_EXCEPTION_INDENT, tp);
    return sb.toString();
  }

  private void recursiveAppend(StringBuilder sb, String prefix, int indent, IThrowableProxy tp) {
    if (tp == null) {
      return;
    }
    subjoinFirstLine(sb, prefix, indent, tp);
    sb.append(CoreConstants.LINE_SEPARATOR);
    subjoinSTEPArray(sb, indent, tp);
    IThrowableProxy[] suppressed = tp.getSuppressed();
    if (suppressed != null) {
      for (IThrowableProxy current : suppressed) {
        recursiveAppend(sb, CoreConstants.SUPPRESSED, indent + ThrowableProxyUtil.SUPPRESSED_EXCEPTION_INDENT, current);
      }
    }
    recursiveAppend(sb, CoreConstants.CAUSED_BY, indent, tp.getCause());
  }

  private void subjoinFirstLine(StringBuilder buf, String prefix, int indent, IThrowableProxy tp) {
    ThrowableProxyUtil.indent(buf, indent - 1);
    if (prefix != null) {
      buf.append(prefix);
    }
    subjoinExceptionMessage(buf, tp);
  }

  private void subjoinExceptionMessage(StringBuilder buf, IThrowableProxy tp) {
    buf.append(tp.getClassName()).append(": ").append(tp.getMessage());
  }

  private void subjoinSTEPArray(StringBuilder buf, int indent, IThrowableProxy tp) {
    StackTraceElementProxy[] stepArray = tp.getStackTraceElementProxyArray();
    int commonFrames = tp.getCommonFrames();
    int maxIndex = stepArray.length;
    if (commonFrames > 0) {
      maxIndex -= commonFrames;
    }

    for (int i = 0; i < maxIndex; i++) {
      StackTraceElementProxy element = stepArray[i];
      ThrowableProxyUtil.indent(buf, indent);
      buf.append(element).append(CoreConstants.LINE_SEPARATOR);
    }

    if (commonFrames > 0) {
      ThrowableProxyUtil.indent(buf, indent);
      buf.append("... ")
          .append(tp.getCommonFrames())
          .append(" common frames omitted")
          .append(CoreConstants.LINE_SEPARATOR);
    }
  }
}
