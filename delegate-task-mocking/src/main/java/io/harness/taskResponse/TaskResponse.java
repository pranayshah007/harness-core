package io.harness.taskResponse;


import io.harness.delegate.beans.DelegateTaskEvent;
import io.harness.delegate.beans.DelegateTaskResponse;

public class TaskResponse {

    public DelegateTaskResponse getTaskResponse(DelegateTaskEvent delegateTaskEvent, String delegateId) {
        ShellTaskResponseNG shellTaskResponseNG = new ShellTaskResponseNG();

        if(delegateTaskEvent.getTaskType().equalsIgnoreCase("SHELL_SCRIPT_TASK_NG")) {
            return shellTaskResponseNG.buildShellTaskResponse(delegateId, delegateTaskEvent.getAccountId());
        }
        return null;
    }

}
