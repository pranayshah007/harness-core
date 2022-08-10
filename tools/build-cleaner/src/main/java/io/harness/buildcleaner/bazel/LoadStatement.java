package io.harness.buildcleaner.bazel;

public class LoadStatement implements Comparable {
  String bazelExtension;
  String symbol;

  public LoadStatement(String bazelExtension, String symbol) {
    this.bazelExtension = bazelExtension;
    this.symbol = symbol;
  }

  public String toString() {
    StringBuilder response = new StringBuilder();
    return response.append("load(\"").append(bazelExtension).append("\", \"").append(symbol).append("\")").toString();
  }

  @Override
  public int compareTo(Object o) {
    return o.toString().compareTo(toString());
  }
}
