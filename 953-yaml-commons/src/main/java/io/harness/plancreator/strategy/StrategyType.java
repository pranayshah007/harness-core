package io.harness.plancreator.strategy;

public enum StrategyType {
  FOR("for"),
  MATRIX("matrix");

  String displayName;

  StrategyType(String displayName) {
    this.displayName = displayName;
  }
}
