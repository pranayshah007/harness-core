package io.harness.cvng.core.services.api;

import io.harness.cvng.core.beans.LogFeedback;
import io.harness.cvng.core.beans.LogFeedbackHistory;
import io.harness.cvng.core.beans.params.ProjectParams;

import java.util.List;

public interface LogFeedbackService {
  LogFeedback create(ProjectParams projectParams, String userId, LogFeedback logFeedback);

  LogFeedback update(ProjectParams projectParams, String userId, String feedbackId, LogFeedback logFeedback);

  boolean delete(ProjectParams projectParams, String userId, String feedbackId);

  LogFeedback get(ProjectParams projectParams, String feedbackId);

  List<LogFeedbackHistory> history(ProjectParams projectParams, String feedbackId);
}
