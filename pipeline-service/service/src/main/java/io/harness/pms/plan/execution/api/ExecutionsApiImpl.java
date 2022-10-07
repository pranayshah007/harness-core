package io.harness.pms.plan.execution.api;

import io.harness.spec.server.pipeline.ExecutionsApi;
import io.harness.spec.server.pipeline.model.InterruptRequestBody;
import io.harness.spec.server.pipeline.model.PipelineExecuteRequestBody;

import java.util.List;
import javax.ws.rs.core.Response;

public class ExecutionsApiImpl implements ExecutionsApi {
  @Override
  public Response executePipeline(PipelineExecuteRequestBody requestBody, String org, String project, String pipeline,
      String account, String branchGitX) {
    return null;
  }

  @Override
  public Response getExecutionDetails(String org, String project, String execution, String account) {
    return null;
  }

  @Override
  public Response getExecutionDetailsGraph(
      String org, String project, String execution, String account, String stageNode, Boolean fullGraph) {
    return null;
  }

  @Override
  public Response getRuntimeTemplate(
      String org, String project, String pipeline, String account, List<String> stageIds, String branchGitX) {
    return null;
  }

  @Override
  public Response listExecutions(String org, String project, String account, Integer page, Integer limit,
      String searchTerm, String sort, String order, String pipelineId, String filterId, List<String> status,
      Boolean myDeployments, String repositoryCI, String branchCI, String tagCI, String prSourceCI, String prTargetCI,
      List<String> services, List<String> envs, List<String> infras, String branchGitX) {
    return null;
  }

  @Override
  public Response registerInterrupt(
      InterruptRequestBody interruptRequestBody, String org, String project, String execution, String account) {
    return null;
  }
}
