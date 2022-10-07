package io.harness.pms.plan.execution.api;

import io.harness.accesscontrol.NGAccessControlCheck;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.InvalidRequestException;
import io.harness.pms.annotations.PipelineServiceAuth;
import io.harness.pms.plan.execution.PipelineExecutor;
import io.harness.pms.plan.execution.PlanExecutionResponseDto;
import io.harness.pms.rbac.PipelineRbacPermissions;
import io.harness.spec.server.pipeline.ExecutionsApi;
import io.harness.spec.server.pipeline.model.InterruptRequestBody;
import io.harness.spec.server.pipeline.model.PipelineExecuteRequestBody;
import io.harness.spec.server.pipeline.model.PipelineExecuteResponseBody;

import com.google.inject.Inject;
import java.util.List;
import javax.ws.rs.core.Response;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.PIPELINE)
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@PipelineServiceAuth
@Slf4j
public class ExecutionsApiImpl implements ExecutionsApi {
  @Inject private final PipelineExecutor pipelineExecutor;

  @Override
  @NGAccessControlCheck(resourceType = "PIPELINE", permission = PipelineRbacPermissions.PIPELINE_EXECUTE)
  public Response executePipeline(PipelineExecuteRequestBody requestBody, String org, String project, String pipeline,
      String account, String branchGitX) {
    if (requestBody.getInputSetRefs() != null && requestBody.getRuntimeYaml() != null) {
      throw new InvalidRequestException(
          "Both InputSetReferences and RuntimeInputYAML are passed, please pass only either.");
    }
    if (requestBody.getInputSetRefs() == null && requestBody.getRuntimeYaml() == null) {
      throw new InvalidRequestException(
          "Both InputSetReferences and RuntimeInputYAML are null, please pass one of them.");
    }
    PlanExecutionResponseDto oldExecutionResponseDto;
    if (requestBody.getInputSetRefs() != null) {
      oldExecutionResponseDto = pipelineExecutor.runPipelineWithInputSetReferencesList(account, org, project, pipeline,
          null, requestBody.getInputSetRefs(), branchGitX, null, requestBody.isNotifyExecutorOnly());
    } else {
      oldExecutionResponseDto = pipelineExecutor.runPipelineWithInputSetPipelineYaml(account, org, project, pipeline,
          null, requestBody.getRuntimeYaml(), false, requestBody.isNotifyExecutorOnly());
    }
    PipelineExecuteResponseBody responseBody = ExecutionsApiUtils.getExecuteResponseBody(oldExecutionResponseDto);
    return Response.ok().entity(responseBody).build();
  }

  @Override
  @NGAccessControlCheck(resourceType = "PIPELINE", permission = PipelineRbacPermissions.PIPELINE_VIEW)
  public Response getExecutionDetails(String org, String project, String execution, String account) {
    return null;
  }

  @Override
  @NGAccessControlCheck(resourceType = "PIPELINE", permission = PipelineRbacPermissions.PIPELINE_VIEW)
  public Response getExecutionDetailsGraph(
      String org, String project, String execution, String account, String stageNode, Boolean fullGraph) {
    return null;
  }

  @Override
  @NGAccessControlCheck(resourceType = "PIPELINE", permission = PipelineRbacPermissions.PIPELINE_VIEW)
  public Response getRuntimeTemplate(
      String org, String project, String pipeline, String account, List<String> stageIds, String branchGitX) {
    return null;
  }

  @Override
  @NGAccessControlCheck(resourceType = "PIPELINE", permission = PipelineRbacPermissions.PIPELINE_VIEW)
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
