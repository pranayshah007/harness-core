package io.harness.cvng.analysis.beans;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ExceptionInfo {
  String stackTrace;
  String exception;
}
