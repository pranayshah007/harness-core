package io.harness.delegate.beans.ci.vm.steps;

public interface VmUnitTestReport {
  enum Type { JUNIT }
  Type getType();
}
