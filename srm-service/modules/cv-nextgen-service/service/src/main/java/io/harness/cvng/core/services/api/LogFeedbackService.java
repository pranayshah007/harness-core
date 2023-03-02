/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.services.api;

import io.harness.cvng.core.beans.LogFeedback;
import io.harness.cvng.core.beans.LogFeedbackHistory;
import io.harness.cvng.core.beans.params.ProjectParams;

import java.util.List;

public interface LogFeedbackService {
  LogFeedback create(ProjectParams projectParams, LogFeedback logFeedback);

  LogFeedback update(ProjectParams projectParams, String feedbackId, LogFeedback logFeedback);

  boolean delete(ProjectParams projectParams, String feedbackId);

  LogFeedback get(ProjectParams projectParams, String feedbackId);

  List<LogFeedbackHistory> history(ProjectParams projectParams, String feedbackId);

  List<LogFeedback> list(String envIdentifier, String serviceIdentifier);
}
