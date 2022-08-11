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
