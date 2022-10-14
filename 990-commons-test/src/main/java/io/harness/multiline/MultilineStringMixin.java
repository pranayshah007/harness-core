/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.multiline;

import io.harness.exception.LoadSourceCodeException;
import io.harness.resource.Project;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import lombok.experimental.UtilityClass;

public interface MultilineStringMixin {
  @UtilityClass
  class $ {
    private static final String OPEN_ML_COMMENT = "/*";
    private static final String CLOSE_ML_COMMENT = "*/";
    private static final String TEST_DIR = "/src/test/java/";

    // internal - do not use directly
    public static String GQL(Class clazz) {
      return internal(clazz);
    }

    static String internal(Class clazz) {
      StackTraceElement testClassElement = new RuntimeException().getStackTrace()[2];
      String classPath = testClassElement.getClassName().replace('.', '/') + ".java";
      String absolutePath = Project.moduleDirectory(clazz) + TEST_DIR + classPath;

      // Handle sandboxed bazel path - remove path starting from /sandbox till execroot. This is because the source
      // code is present outside the sandbox directory created by bazel.
      if (!Files.exists(Paths.get(absolutePath)) && absolutePath.contains("/sandbox/")) {
        absolutePath = absolutePath.substring(0, absolutePath.indexOf("/sandbox/"))
            + absolutePath.substring(absolutePath.lastIndexOf("/execroot"));
      }
      StringBuilder sb = new StringBuilder();
      try (InputStream in = new FileInputStream(new File(absolutePath));
           BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
        String line = null;
        int skip = testClassElement.getLineNumber();

        while ((line = reader.readLine()) != null) {
          if (--skip <= 0) {
            sb.append(line).append('\n');
            if (line.indexOf(CLOSE_ML_COMMENT) >= 0) {
              break;
            }
          }
        }
      } catch (IOException e) {
        throw new LoadSourceCodeException(e);
      }

      String commentArea = sb.toString();

      final int startIndex = commentArea.indexOf(OPEN_ML_COMMENT) + 2;
      final int endIndex = commentArea.indexOf(CLOSE_ML_COMMENT);

      return commentArea.substring(startIndex, endIndex);
    }
  }

  // This method is accepts a multiline comment as format pattern, followed with zero or more objects
  // Never use in production code. It depends on accessing the original java file and it useful only
  // for test purposes, while running the test as part of the build process.
  default String $ML(Object... objects) {
    return String.format($.internal(getClass()), objects);
  }

  // The same as $ML but for graphql query code
  default String $GQL(Object... objects) {
    return String.format($.internal(getClass()), objects);
  }
}
