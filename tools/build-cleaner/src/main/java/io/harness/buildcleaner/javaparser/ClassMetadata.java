package io.harness.buildcleaner.javaparser;

public class ClassMetadata {
  private final String buildModulePath;
  private final String fullyQualifiedClassName;

  public ClassMetadata(String buildModulePath, String fullyQualifiedClassName) {
    this.buildModulePath = buildModulePath;
    this.fullyQualifiedClassName = fullyQualifiedClassName;
  }

  public String getBuildModulePath() {
    return buildModulePath;
  }

  public String getFullyQualifiedClassName() {
    return fullyQualifiedClassName;
  }
}
