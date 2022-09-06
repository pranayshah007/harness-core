/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.buildcleaner.javaparser;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import java.io.IOException;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PackageParser {
  private static final Logger logger = LoggerFactory.getLogger(PackageParser.class);

  private final Path workspace;
  private static final String BUILD_FILE = "BUILD.bazel";
  private JavaParser javaParser;

  public PackageParser(Path workspace) {
    this.workspace = workspace;
    KnownTypeSolvers solvers = new KnownTypeSolvers();

    // Configure java parser
    ParserConfiguration config = new ParserConfiguration().setSymbolResolver(solvers.getTypeSolver());
    javaParser = new JavaParser(config);
  }

  public JavaParser getJavaParser() {
    return javaParser;
  }

  public ClasspathParser getClassPathParser() throws IOException {
    ClasspathParser parser = new ClasspathParser(this.javaParser, workspace);
    return parser;
  }
}
