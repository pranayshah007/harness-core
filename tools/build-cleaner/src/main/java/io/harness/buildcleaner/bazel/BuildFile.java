package io.harness.buildcleaner.bazel;

import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

public class BuildFile {
  private Set<LoadStatement> loadStatementSet = new TreeSet<>();
  private List<JavaLibrary> javaLibraryList = new ArrayList<>();
  private List<JavaBinary> javaBinaryList = new ArrayList<>();

  public void addLoadStatement(LoadStatement loadStatement) {
    loadStatementSet.add(loadStatement);
  }
  public void addJavaLibrary(JavaLibrary javaLibrary) {
    loadStatementSet.add(new LoadStatement("@rules_java//java:defs.bzl", "java_library"));
    javaLibraryList.add(javaLibrary);
  }
  public void addJavaBinary(JavaBinary javaBinary) {
    loadStatementSet.add(new LoadStatement("@rules_java//java:defs.bzl", "java_binary"));
    javaBinaryList.add(javaBinary);
  }

  public String toString() {
    StringBuilder response = new StringBuilder();
    for (LoadStatement loadStatement : loadStatementSet) {
      response.append(loadStatement.toString());
      response.append("\n");
    }
    response.append("\n");

    for (JavaLibrary javaLibrary : javaLibraryList) {
      response.append(javaLibrary.toString());
      response.append("\n");
    }
    response.append("\n");

    for (JavaBinary javaBinary : javaBinaryList) {
      response.append(javaBinary.toString());
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

  public Set<LoadStatement> getLoadStatements() {
    return loadStatementSet;
  }

  public List<JavaLibrary> getJavaLibraryList() {
    return javaLibraryList;
  }

  public List<JavaBinary> getJavaBinaryList() {
    return javaBinaryList;
  }
}
