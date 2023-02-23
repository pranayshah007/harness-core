/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.sm.states;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.data.structure.UUIDGenerator.generateUuid;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.ExecutionStatus;
import io.harness.delay.SimpleNotifier;
import io.harness.scheduler.PersistentScheduler;
import io.harness.waiter.WaitNotifyEngine;

import software.wings.api.WaitStateExecutionData;
import software.wings.sm.ExecutionContext;
import software.wings.sm.ExecutionResponse;
import software.wings.sm.ExecutionStatusData;
import software.wings.sm.State;
import software.wings.sm.StateType;

import com.github.reinert.jjschema.Attributes;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import dev.morphia.annotations.Transient;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * dummy implementation for wait state.
 *
 * @author Rishi
 */
@OwnedBy(CDC)
@TargetModule(HarnessModule._870_CG_ORCHESTRATION)
public class WaitState extends State {
  @Inject @Named("waitStateResumer") @Transient private ScheduledExecutorService executorService;

  @Transient @Inject private WaitNotifyEngine waitNotifyEngine;

  @Transient @Inject @Named("BackgroundJobScheduler") private PersistentScheduler jobScheduler;

  @Attributes(title = "Wait Duration") private long duration;

  /**
   * Creates a new wait state.
   *
   * @param name state name
   */
  public WaitState(String name) {
    super(name, StateType.WAIT.name());
  }

  /* (non-Javadoc)
   * @see software.wings.sm.State#execute(software.wings.sm.ExecutionContext)
   */
  @Override
  public ExecutionResponse execute(ExecutionContext context) {
    WaitStateExecutionData waitStateExecutionData = new WaitStateExecutionData();
    waitStateExecutionData.setDuration(duration);
    long wakeupTs = System.currentTimeMillis() + (duration * 1000);
    waitStateExecutionData.setWakeupTs(wakeupTs);
    waitStateExecutionData.setResumeId(generateUuid());

    // TODO: Fix the test cases and then checkin the persistent notification
    //    JobDetail job = JobBuilder.newJob(NotifyJob.class).withIdentity(Constants.WAIT_RESUME_GROUP,
    //    waitStateExecutionData.getResumeId())
    //        .usingJobData("correlationId", waitStateExecutionData.getResumeId()).usingJobData("executionStatus",
    //        ExecutionStatus.SUCCESS.name()).build();
    //
    //    Trigger trigger = TriggerBuilder.newTrigger().withIdentity(waitStateExecutionData.getResumeId()).startAt(new
    //    Date(wakeupTs)).forJob(job).build();
    //
    //    jobScheduler.scheduleJob(job, trigger);

    executorService.schedule(new SimpleNotifier(waitNotifyEngine, waitStateExecutionData.getResumeId(),
                                 ExecutionStatusData.builder().executionStatus(ExecutionStatus.SUCCESS).build()),
        duration, TimeUnit.SECONDS);

    return ExecutionResponse.builder()
        .async(true)
        .correlationId(waitStateExecutionData.getResumeId())
        .stateExecutionData(waitStateExecutionData)
        .build();
  }

  /**
   * Handle abort event.
   *
   * @param context the context
   */
  @Override
  public void handleAbortEvent(ExecutionContext context) {}

  /**
   * Gets duration.
   *
   * @return the duration
   */
  public long getDuration() {
    return duration;
  }

  /**
   * Sets duration.
   *
   * @param duration the duration
   */
  public void setDuration(long duration) {
    this.duration = duration;
  }
}
