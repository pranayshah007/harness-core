package io.harness.taskResponse;

import io.harness.delegate.beans.DelegateMetaInfo;
import io.harness.delegate.beans.DelegateTaskResponse;
import io.harness.delegate.beans.logstreaming.UnitProgressData;
import io.harness.delegate.task.shell.ShellScriptTaskResponseNG;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.UnitProgress;
import io.harness.logging.UnitStatus;
import io.harness.shell.ExecuteCommandResponse;
import io.harness.shell.ShellExecutionData;
import software.wings.beans.SerializationFormat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ShellTaskResponseNG {
    public DelegateTaskResponse buildShellTaskResponse(String delegateId, String accountId){
        DelegateTaskResponse delegateTaskResponse = DelegateTaskResponse.builder().build();
        ShellExecutionData shellExecutionData = ShellExecutionData.builder().build();
        DelegateMetaInfo delegateMetaInfo = DelegateMetaInfo.builder().build();

        UnitProgress unitProgress = UnitProgress.newBuilder().setStatus(UnitStatus.SUCCESS).setUnitName("Execute").setStartTime(Instant.now().toEpochMilli()).setEndTime(Instant.now().toEpochMilli()).build();

        List<UnitProgress> unitProgresses = new ArrayList<>();
        unitProgresses.add(unitProgress);
        UnitProgressData unitProgressData = UnitProgressData.builder().unitProgresses(unitProgresses).build();
        Map<String, String> map = new HashMap<>();
        shellExecutionData.setExpired(false);
        shellExecutionData.setSweepingOutputEnvVariables(map);
        ExecuteCommandResponse executeCommandResponse = ExecuteCommandResponse.builder().commandExecutionData(shellExecutionData).status(CommandExecutionStatus.SUCCESS).build();

        delegateMetaInfo.setHostName(delegateId+"-delegate-0");
        delegateMetaInfo.setId(delegateId);
        ShellScriptTaskResponseNG shellScriptTaskResponseNG = ShellScriptTaskResponseNG.builder().unitProgressData(unitProgressData).errorMessage("").delegateMetaInfo(delegateMetaInfo).status(CommandExecutionStatus.SUCCESS).executeCommandResponse(executeCommandResponse).build();

        delegateTaskResponse.setAccountId(accountId);
        delegateTaskResponse.setSerializationFormat(SerializationFormat.KRYO);
        delegateTaskResponse.setTaskTypeName("SHELL_SCRIPT_TASK_NG");
        delegateTaskResponse.setTaskType(null);
        delegateTaskResponse.setResponseCode(DelegateTaskResponse.ResponseCode.OK);
        delegateTaskResponse.setResponse(shellScriptTaskResponseNG);
        return delegateTaskResponse;
    }
}
