/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.stresstesting.script;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.AccountId;
import io.harness.delegate.Capability;
import io.harness.delegate.SubmitTaskRequest;
import io.harness.delegate.TaskDetails;
import io.harness.delegate.TaskMode;
import io.harness.delegate.TaskType;
import io.harness.delegate.beans.executioncapability.SystemEnvCheckerCapability;
import io.harness.shell.ScriptType;
import io.harness.testing.DelegateTaskStressTest;
import io.harness.testing.DelegateTaskStressTestStage;

import software.wings.beans.delegation.ShellScriptParameters;

import com.google.protobuf.ByteString;
import com.google.protobuf.util.Durations;

@OwnedBy(HarnessTeam.DEL)
public class CapabilityStressTestGenerator extends StressTestGenerator {
  // The default account id we use is kmpySmUISimoRrJL6NL73w
  private final String ACCOUNT_ID = "fill_me_in";
  private ShellScriptParameters shellScriptTaskParameters = ShellScriptParameters.builder()
                                                                .executeOnDelegate(true)
                                                                .scriptType(ScriptType.BASH)
                                                                .script("echo \"hello world!\"; sleep 10")
                                                                .accountId(ACCOUNT_ID)
                                                                .build();

  // Construct
  // 1. Tasks that were executed before - has delegates to run these tasks - 1000 at 40 qps
  // 2. Regular tasks that are coming - with no delegates to serve them - delegates not having capabilities - 1000 at 40
  // qps
  // 3. Tasks with unique capabilities -- 1000 distinct tasks, all fired off at the approximately the same time
  @Override
  public DelegateTaskStressTest makeStressTest() {
    DelegateTaskStressTest.Builder stressTestBuilder = DelegateTaskStressTest.newBuilder();
    // stage 1
    stressTestBuilder.addStage(DelegateTaskStressTestStage.newBuilder()
                                   .addTaskRequest(createTaskRequest("good_key", "good_value"))
                                   .addTaskRequest(createTaskRequest("key", "value"))
                                   .setOffset(0)
                                   .setIterations(1000)
                                   .setQps(40));

    // stage 2
    stressTestBuilder.addStage(DelegateTaskStressTestStage.newBuilder()
                                   .addTaskRequest(createTaskRequest("bad_key", "invalid"))
                                   .setOffset(30)
                                   .setIterations(1000)
                                   .setQps(40));

    // stage 3
    DelegateTaskStressTestStage.Builder stageBuilder =
        DelegateTaskStressTestStage.newBuilder().setOffset(15).setIterations(2000).setQps(1000);
    for (int i = 0; i < 1000; i++) {
      stageBuilder.addTaskRequest(createTaskRequest("random" + i, "value" + i));
    }
    stressTestBuilder.addStage(stageBuilder);

    return stressTestBuilder.build();
  }

  private SubmitTaskRequest createTaskRequest(String key, String value) {
    return SubmitTaskRequest.newBuilder()
        .setAccountId(AccountId.newBuilder().setId(ACCOUNT_ID))
        .setDetails(TaskDetails.newBuilder()
                        .setMode(TaskMode.ASYNC)
                        .setType(TaskType.newBuilder().setType("SCRIPT"))
                        .setExecutionTimeout(Durations.fromMinutes(5))
                        .setKryoParameters(
                            ByteString.copyFrom(kryoSerializerWrapper.asDeflatedBytes(shellScriptTaskParameters))))
        .addCapabilities(generateTestCapability(key, value))
        .setQueueTimeout(Durations.fromMinutes(1))
        .build();
  }

  private Capability generateTestCapability(String key, String value) {
    return Capability.newBuilder()
        .setKryoCapability(ByteString.copyFrom(kryoSerializerWrapper.asDeflatedBytes(
            SystemEnvCheckerCapability.builder().systemPropertyName(key).comparate(value).build())))
        .build();
  }
}
