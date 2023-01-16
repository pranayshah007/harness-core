package io.harness.cvng.cdng.services.api;

import io.harness.cvng.cdng.beans.LogFeedback;

public interface LogFeedbackService {
  void create(LogFeedback logFeedback);

  LogFeedback update(LogFeedback logFeedback);

  boolean delete(LogFeedback logFeedback);

  LogFeedback get(LogFeedback logFeedback);
}
