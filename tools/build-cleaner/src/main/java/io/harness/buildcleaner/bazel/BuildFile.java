/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.buildcleaner.bazel;

import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BuildFile {
  private final SortedSet<LoadStatement> loadStatements = new TreeSet<>();
  private final List<JavaLibrary> javaLibraries = new ArrayList<>();
  private final List<JavaBinary> javaBinaries = new ArrayList<>();
  private boolean runAnalysisPerModule = false;

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
    for (JavaLibrary javaLibrary : javaLibraryList) {
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
