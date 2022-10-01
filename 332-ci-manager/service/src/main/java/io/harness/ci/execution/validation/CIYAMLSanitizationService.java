package io.harness.ci.validation;

import io.harness.plancreator.execution.ExecutionWrapperConfig;

import java.util.List;

public interface CIYAMLSanitizationService {
  boolean validate(List<ExecutionWrapperConfig> wrapper);
}
