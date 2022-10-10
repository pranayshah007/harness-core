package io.harness.pms.plan.execution.api;

import io.harness.accesscontrol.AccountIdentifier;
import io.harness.accesscontrol.NGAccessControlCheck;
import io.harness.accesscontrol.OrgIdentifier;
import io.harness.accesscontrol.ProjectIdentifier;
import io.harness.accesscontrol.ResourceIdentifier;
import io.harness.accesscontrol.acl.api.Resource;
import io.harness.accesscontrol.acl.api.ResourceScope;
import io.harness.accesscontrol.clients.AccessControlClient;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.InvalidRequestException;
import io.harness.gitaware.helper.GitAwareContextHelper;
import io.harness.gitsync.interceptor.GitEntityInfo;
import io.harness.gitsync.sdk.EntityGitDetails;
import io.harness.pms.annotations.PipelineServiceAuth;
import io.harness.pms.gitsync.PmsGitSyncHelper;
import io.harness.pms.plan.execution.PipelineExecutor;
import io.harness.pms.plan.execution.PlanExecutionInterruptType;
import io.harness.pms.plan.execution.PlanExecutionResponseDto;
import io.harness.pms.plan.execution.beans.PipelineExecutionSummaryEntity;
import io.harness.pms.plan.execution.service.PMSExecutionService;
import io.harness.pms.rbac.PipelineRbacPermissions;
import io.harness.spec.server.pipeline.ExecutionsApi;
import io.harness.spec.server.pipeline.model.ExecutionsDetailsSummary;
import io.harness.spec.server.pipeline.model.ExecutionsDetailsSummaryWithGraph;
import io.harness.spec.server.pipeline.model.Graph;
import io.harness.spec.server.pipeline.model.InterruptRequestBody;
import io.harness.spec.server.pipeline.model.InterruptResponseBody;
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
  @Inject private final PMSExecutionService pmsExecutionService;
  @Inject private final PmsGitSyncHelper pmsGitSyncHelper;
  @Inject private final AccessControlClient accessControlClient;

  @Override
  @NGAccessControlCheck(resourceType = "PIPELINE", permission = PipelineRbacPermissions.PIPELINE_EXECUTE)
  public Response executePipeline(PipelineExecuteRequestBody requestBody, @OrgIdentifier String org,
      @ProjectIdentifier String project, @ResourceIdentifier String pipeline, @AccountIdentifier String account,
      String branchGitX) {
    GitAwareContextHelper.populateGitDetails(GitEntityInfo.builder().branch(branchGitX).build());
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
  public Response getExecutionDetails(String org, String project, String execution, String account) {
    PipelineExecutionSummaryEntity executionSummaryEntity =
        pmsExecutionService.getPipelineExecutionSummaryEntity(account, org, project, execution, false);
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(account, org, project),
        Resource.of("PIPELINE", executionSummaryEntity.getPipelineIdentifier()), PipelineRbacPermissions.PIPELINE_VIEW);
    EntityGitDetails entityGitDetails;
    if (executionSummaryEntity.getEntityGitDetails() == null) {
      entityGitDetails =
          pmsGitSyncHelper.getEntityGitDetailsFromBytes(executionSummaryEntity.getGitSyncBranchContext());
    } else {
      entityGitDetails = executionSummaryEntity.getEntityGitDetails();
    }
    pmsExecutionService.sendGraphUpdateEvent(executionSummaryEntity);
    ExecutionsDetailsSummary summary =
        ExecutionsApiUtils.getExecutionDetailsSummary(executionSummaryEntity, entityGitDetails);
    return Response.ok().entity(summary).build();
  }

  @Override
  public Response getExecutionDetailsGraph(
      String org, String project, String execution, String account, String stageNode, Boolean fullGraph) {
    PipelineExecutionSummaryEntity executionSummaryEntity =
        pmsExecutionService.getPipelineExecutionSummaryEntity(account, org, project, execution, false);
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(account, org, project),
        Resource.of("PIPELINE", executionSummaryEntity.getPipelineIdentifier()), PipelineRbacPermissions.PIPELINE_VIEW);
    EntityGitDetails entityGitDetails;
    if (executionSummaryEntity.getEntityGitDetails() == null) {
      entityGitDetails =
          pmsGitSyncHelper.getEntityGitDetailsFromBytes(executionSummaryEntity.getGitSyncBranchContext());
    } else {
      entityGitDetails = executionSummaryEntity.getEntityGitDetails();
    }
    ExecutionsDetailsSummary summary =
        ExecutionsApiUtils.getExecutionDetailsSummary(executionSummaryEntity, entityGitDetails);
    Graph graph =
        ExecutionsApiUtils.getGraph(pmsExecutionService.getOrchestrationGraph(stageNode, execution, null), fullGraph);
    ExecutionsDetailsSummaryWithGraph summaryWithGraph = new ExecutionsDetailsSummaryWithGraph();
    summaryWithGraph.setSummary(summary);
    summaryWithGraph.setGraph(graph);
    return Response.ok().entity(summaryWithGraph).build();
  }

  @Override
  @NGAccessControlCheck(resourceType = "PIPELINE", permission = PipelineRbacPermissions.PIPELINE_VIEW)
  public Response getRuntimeTemplate(@OrgIdentifier String org, @ProjectIdentifier String project,
      @ResourceIdentifier String pipeline, @AccountIdentifier String account, List<String> stageIds,
      String branchGitX) {
    return null;
  }

  @Override
  @NGAccessControlCheck(resourceType = "PIPELINE", permission = PipelineRbacPermissions.PIPELINE_VIEW)
  public Response listExecutions(@OrgIdentifier String org, @ProjectIdentifier String project,
      @AccountIdentifier String account, Integer page, Integer limit, String searchTerm, String sort, String order,
      String pipelineId, String filterId, List<String> status, Boolean myDeployments, String repositoryCI,
      String branchCI, String tagCI, String prSourceCI, String prTargetCI, List<String> services, List<String> envs,
      List<String> infras, String branchGitX) {
    return null;
  }

  @Override
  public Response registerInterrupt(
      InterruptRequestBody interruptRequestBody, String org, String project, String execution, String account) {
    PipelineExecutionSummaryEntity executionSummaryEntity =
        pmsExecutionService.getPipelineExecutionSummaryEntity(account, org, project, execution, false);
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(account, org, project),
        Resource.of("PIPELINE", executionSummaryEntity.getPipelineIdentifier()),
        PipelineRbacPermissions.PIPELINE_EXECUTE);
    if (interruptRequestBody == null) {
      throw new InvalidRequestException("Interrupt Type not found.");
    }
    InterruptResponseBody interruptResponseBody =
        ExecutionsApiUtils.getInterruptResponse(pmsExecutionService.registerInterrupt(
            PlanExecutionInterruptType.getPipelineExecutionInterrupt(interruptRequestBody.getInterruptType().value()),
            execution, null));
    return Response.status(202).entity(interruptResponseBody).build();
  }
}
