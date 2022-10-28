package io.harness;

public interface PipelineSettingsService {
  PlanExecutionSettingResponse shouldQueuePlanExecution(
      String accountId, String orgId, String projectId, String pipelineIdentifier);

  long getMaxPipelineCreationCount(String accountId);
}
