package software.wings.service.intfc;

import io.harness.beans.DelegateTask;
import io.harness.selection.log.DelegateSelectionLog;

public interface DMSDelegateSelectionLogService {
  void logTaskAssigned(String delegateId, DelegateTask delegateTask);
  void save(DelegateSelectionLog log);
}
