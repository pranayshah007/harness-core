/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.buildcleaner.bazel;

import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BuildFile {
  private final SortedSet<LoadStatement> loadStatements = new TreeSet<>();
  private final List<JavaLibrary> javaLibraries = new ArrayList<>();
  private final List<JavaBinary> javaBinaries = new ArrayList<>();
  private boolean runAnalysisPerModule = false;
  private static final Logger logger = LoggerFactory.getLogger(BuildFile.class);
  private static final Pattern default_pattern = Pattern.compile("HelloWorld!");
  private static final Pattern java_library_deps_pattern =
      Pattern.compile("java_library\\([\\s\\S]*?(deps = \\[[\\s\\S]*?\\]),?", Pattern.MULTILINE);
  private static final Pattern java_library_runtime_deps_pattern =
      Pattern.compile("java_library\\([\\s\\S]*?(runtime_deps = \\[[\\s\\S]*?\\]),?", Pattern.MULTILINE);
  private static final Pattern java_binary_deps_pattern =
      Pattern.compile("java_binary\\([\\s\\S]*?(deps = \\[[\\s\\S]*?\\]),?", Pattern.MULTILINE);
  private static final Pattern java_binary_runtime_deps_pattern =
      Pattern.compile("java_binary\\([\\s\\S]*?(runtime_deps = \\[[\\s\\S]*?\\]),?", Pattern.MULTILINE);

  public void addJavaLibrary(JavaLibrary javaLibrary) {
    loadStatements.add(new LoadStatement("@rules_java//java:defs.bzl", "java_library"));
    javaLibraries.add(javaLibrary);
  }
  public void addJavaBinary(JavaBinary javaBinary) {
    loadStatements.add(new LoadStatement("@rules_java//java:defs.bzl", "java_binary"));
    javaBinaries.add(javaBinary);
  }

  public void enableAnalysisPerModule() {
    loadStatements.add(new LoadStatement("//:tools/bazel/macros.bzl", "run_analysis_per_module"));
    runAnalysisPerModule = true;
  }

  /* Given a target name get all the dependencies */
  public String getDependencies(String targetName) {
    for (JavaLibrary javaLibrary : javaLibraries) {
      if (javaLibrary.getName().equalsIgnoreCase(targetName)) {
        return javaLibrary.getDepsSection();
      }
    }
    return "";
  }

  public String toString() {
    StringBuilder response = new StringBuilder();
    for (LoadStatement loadStatement : loadStatements) {
      response.append(loadStatement.toString());
      response.append("\n");
    }
    response.append("\n");

    for (JavaLibrary javaLibrary : javaLibraries) {
      response.append(javaLibrary.toString());
      response.append("\n");
    }
    response.append("\n");

    for (JavaBinary javaBinary : javaBinaries) {
      response.append(javaBinary.toString());
      response.append("\n");
    }

    if (runAnalysisPerModule) {
      response.append("run_analysis_per_module()");
      response.append("\n");
    }

    return response.toString();
  }

  public void writeToPackage(Path directory) throws FileNotFoundException {
    try (PrintWriter out = new PrintWriter(directory + "/BUILD.bazel");) {
      out.println(toString());
    } catch (FileNotFoundException ex) {
      throw new RuntimeException("Could not write out build file to: " + directory + "/BUILD.bazel");
    }
  }

  // Update deps and runtime_deps in a BUILD.bazel file.
  public void updateDependencies(Path buildFilePath) throws IOException {
    updateDepsHelper(buildFilePath, "java_library", "deps");
    updateDepsHelper(buildFilePath, "java_binary", "deps");
    updateDepsHelper(buildFilePath, "java_binary", "runtime_deps");
  }

  private String getNewLibraryDeps(String updateField) {
    String newDeps = String.format("%s = []", updateField);
    for (JavaLibrary javaLibrary : getJavaLibraryList()) {
      logger.info("Library Name: {}", javaLibrary.getName());
      switch (updateField) {
        case "deps":
          newDeps = javaLibrary.getDepsSection();
          break;
        case "runtime_deps":
          newDeps = javaLibrary.getRunTimeDepsSection();
          break;
      }
    }
    logger.info("New {} for java_library: {}", updateField, newDeps);
    return newDeps;
  }

  private String getNewBinaryDeps(String updateField) {
    String newDeps = String.format("%s = []", updateField);
    for (JavaBinary javaBinary : getJavaBinaryList()) {
      logger.info("Binary Name: {}", javaBinary.getName());
      switch (updateField) {
        case "deps":
          newDeps = javaBinary.getDepsSection();
          break;
        case "runtime_deps":
          newDeps = javaBinary.getRunTimeDepsSection();
          break;
      }
    }
    logger.info("New {} for java_binary: {}", updateField, newDeps);
    return newDeps;
  }

  // Updates BUILD.bazel file's specified buildRule and updateField
  // buildRule: Accepted values are "java_library" and "java_binary"
  // updateField: Accepted values are "deps" and "runtime_deps"
  private void updateDepsHelper(Path buildFilePath, String buildRule, String updateField) throws IOException {
    // This will contain the latest values which should be updated in the file.
    String newDeps = "";
    Pattern pattern = default_pattern;
    switch (buildRule) {
      case "java_library":
        newDeps = getNewLibraryDeps(updateField);
        if (updateField.equalsIgnoreCase("deps")) {
          pattern = java_library_deps_pattern;
        } else if (updateField.equalsIgnoreCase("runtime_deps")) {
          pattern = java_library_runtime_deps_pattern;
        }
        break;
      case "java_binary":
        newDeps = getNewBinaryDeps(updateField);
        if (updateField.equalsIgnoreCase("deps")) {
          pattern = java_binary_deps_pattern;
        } else if (updateField.equalsIgnoreCase("runtime_deps")) {
          pattern = java_binary_runtime_deps_pattern;
        }
        break;
      default:
        logger.info("Unsupported buildRule: {}", buildRule);
        return;
    }

    String currContent = Files.readString(buildFilePath);
    logger.debug("Current file content: \n {}", currContent);
    Matcher matcher = pattern.matcher(currContent);
    StringBuilder builder = new StringBuilder();
    builder.append(currContent);
    String replacedFileContent = "";
    while (matcher.find()) {
      String textToReplace = matcher.group(1);
      logger.debug("Found text to replace: {}", textToReplace);
      replacedFileContent = builder.replace(matcher.start(1), matcher.end(1), newDeps).toString();
      logger.debug("Replaced file content: {}", replacedFileContent);
    }
    if (!replacedFileContent.equalsIgnoreCase("")) {
      // Overwrite a file only if we are able to replace.
      Files.writeString(buildFilePath, replacedFileContent, TRUNCATE_EXISTING);
    }
  }

  public SortedSet<LoadStatement> getLoadStatements() {
    return loadStatements;
  }

  public List<JavaLibrary> getJavaLibraryList() {
    return javaLibraries;
  }

  public List<JavaBinary> getJavaBinaryList() {
    return javaBinaries;
  }
}