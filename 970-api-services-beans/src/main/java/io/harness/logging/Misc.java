/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.logging;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.apache.commons.lang3.StringUtils.trim;

import io.harness.exception.utils.ExceptionUtils;

import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Miscellaneous utility class.
 *
 * @author Rishi
 */
public class Misc {
  private static final Pattern wildCharPattern = Pattern.compile("[-|+|*|/|\\\\| |&|$|\"|'|\\.|\\|]");
  public static final Pattern commaCharPattern = Pattern.compile("\\s*,\\s*");
  public static final int MAX_CAUSES = 10;

  /**
   * Normalize expression string.
   *
   * @param expression the expression
   * @return the string
   */
  public static String normalizeExpression(String expression) {
    return normalizeExpression(expression, "__");
  }

  /**
   * Normalize expression string.
   *
   * @param expression  the expression
   * @param replacement the replacement
   * @return the string
   */
  public static String normalizeExpression(String expression, String replacement) {
    Matcher matcher = wildCharPattern.matcher(trim(expression));
    return matcher.replaceAll(replacement);
  }

  /**
   * Ignore exception.
   *
   * @param callable the callable
   */
  public static void ignoreException(ThrowingCallable callable) {
    try {
      callable.run();
    } catch (Exception e) {
      // Ignore
    }
  }

  /**
   * Ignore exception.
   *
   * @param <T>          the generic type
   * @param callable     the callable
   * @param defaultValue the default value
   * @return the t
   */
  public static <T> T ignoreException(ReturningThrowingCallable<T> callable, T defaultValue) {
    try {
      return callable.run();
    } catch (Exception e) {
      // Ignore
      return defaultValue;
    }
  }

  public static void logAllMessages(Exception ex, LogCallback executionLogCallback) {
    int i = 0;
    Throwable t = ex;
    while (t != null && i++ < MAX_CAUSES) {
      String msg = ExceptionUtils.getMessage(t);
      if (isNotBlank(msg)) {
        executionLogCallback.saveExecutionLog(msg, LogLevel.ERROR);
      }
      t = t.getCause();
    }
  }

  public static void logAllMessages(
      Throwable ex, LogCallback executionLogCallback, CommandExecutionStatus commandExecutionStatus) {
    int i = 0;
    Throwable t = ex;
    while (t != null && i++ < MAX_CAUSES) {
      String msg = ExceptionUtils.getMessage(t);
      if (isNotBlank(msg)) {
        executionLogCallback.saveExecutionLog(msg, LogLevel.ERROR, commandExecutionStatus);
      }
      t = t.getCause();
    }
  }

  public static String getDurationString(long start, long end) {
    return getDurationString(end - start);
  }

  public static String getDurationString(long duration) {
    long elapsedHours = duration / TimeUnit.HOURS.toMillis(1);
    duration = duration % TimeUnit.HOURS.toMillis(1);

    long elapsedMinutes = duration / TimeUnit.MINUTES.toMillis(1);
    duration = duration % TimeUnit.MINUTES.toMillis(1);

    long elapsedSeconds = duration / TimeUnit.SECONDS.toMillis(1);

    StringBuilder elapsed = new StringBuilder();

    if (elapsedHours > 0) {
      elapsed.append(elapsedHours).append('h');
    }
    if (elapsedMinutes > 0) {
      if (isNotEmpty(elapsed.toString())) {
        elapsed.append(' ');
      }
      elapsed.append(elapsedMinutes).append('m');
    }
    if (elapsedSeconds > 0) {
      if (isNotEmpty(elapsed.toString())) {
        elapsed.append(' ');
      }
      elapsed.append(elapsedSeconds).append('s');
    }

    if (isEmpty(elapsed.toString())) {
      elapsed.append("0s");
    }

    return elapsed.toString();
  }

  /**
   * The Interface ThrowingCallable.
   */
  public interface ThrowingCallable {
    /**
     * Run.
     *
     * @throws Exception the exception
     */
    void run() throws Exception;
  }

  /**
   * The Interface ReturningThrowingCallable.
   *
   * @param <T> the generic type
   */
  public interface ReturningThrowingCallable<T> {
    /**
     * Run.
     *
     * @return the t
     * @throws Exception the exception
     */
    T run() throws Exception;
  }

  /**
   * Replace dot with the unicode value so
   * the string can be used as Map keys in
   * mongo.
   * @param str a string containing dots
   * @return replaced string
   */
  public static String replaceDotWithUnicode(String str) {
    return str.replaceAll("\\.", "\u2024");
  }

  /**
   * Replace "\u2024" with the dot char. Reverses the
   * replaceDotWithUnicode action above
   * @param str a string containing \u2024
   * @return replaced string
   */
  public static String replaceUnicodeWithDot(String str) {
    return str.replaceAll("\u2024", ".");
  }

  public static boolean isLong(String s) {
    if (s == null) {
      return false;
    }
    try {
      Long.parseLong(s);
      return true;
    } catch (NumberFormatException e) {
      return false;
    }
  }
}
