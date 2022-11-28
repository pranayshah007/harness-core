package io.harness.steps.plugin;

import java.time.Duration;

public interface ContainerStepConstants {
  int MIN_RETRY = 0;
  int MAX_RETRY = 5;
  long DEFAULT_TIMEOUT = Duration.ofHours(2).toMillis();
}
