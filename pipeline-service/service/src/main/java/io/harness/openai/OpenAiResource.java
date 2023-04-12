/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.openai;

import io.harness.NGCommonEntityConstants;
import io.harness.accesscontrol.AccountIdentifier;
import io.harness.accesscontrol.OrgIdentifier;
import io.harness.accesscontrol.ProjectIdentifier;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.dto.ErrorDTO;
import io.harness.ng.core.dto.FailureDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.openai.dtos.GoldenPipelineResponse;
import io.harness.openai.dtos.SimilarityResponse;
import io.harness.openai.dtos.TemplateResponse;
import io.harness.pms.pipeline.PipelineEntity;
import io.harness.pms.pipeline.service.PMSPipelineService;

import com.google.inject.Inject;
import com.theokanning.openai.completion.chat.ChatCompletionChoice;
import com.theokanning.openai.completion.chat.ChatCompletionRequest;
import com.theokanning.openai.completion.chat.ChatCompletionResult;
import com.theokanning.openai.completion.chat.ChatMessage;
import com.theokanning.openai.service.OpenAiService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.validation.constraints.NotNull;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Hidden
@Api("/openai")
@Path("/openai")
@Produces({"application/json", "application/yaml"})
@Consumes({"application/json", "application/yaml"})
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@ApiResponses(value =
    {
      @ApiResponse(code = 400, response = FailureDTO.class, message = "Bad Request")
      , @ApiResponse(code = 500, response = ErrorDTO.class, message = "Internal server error")
    })
@OwnedBy(HarnessTeam.CDC)
@Slf4j
public class OpenAiResource {
  private final PMSPipelineService pmsPipelineService;

  @GET
  @ApiOperation(value = "Get similarity", nickname = "structureSimilarity")
  ResponseDTO<SimilarityResponse> getStructureSimilarity(
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountId,
      @NotNull @QueryParam(NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgId,
      @NotNull @QueryParam(NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectId,
      @QueryParam("identifier1") String pipelineIdentifier1, @QueryParam("identifier2") String pipelineIdentifier2) {
    return ResponseDTO.newResponse(SimilarityResponse.builder().pipelineSimilarityPercentage(80).build());
  }

  @GET
  @ApiOperation(value = "Get Templates", nickname = "getTemplates")
  ResponseDTO<TemplateResponse> getTemplates(
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountId,
      @NotNull @QueryParam(NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgId,
      @NotNull @QueryParam(NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectId,
      @QueryParam("identifier1") String pipelineIdentifier1, @QueryParam("identifier2") String pipelineIdentifier2) {
    return ResponseDTO.newResponse(
        TemplateResponse.builder().templateYaml("").pipelineYaml1("sample").pipelineYaml2("sample").build());
  }

  @GET
  @Path("/goldenPipeline")
  @ApiOperation(value = "Select Golden Pipeline to get Policies", nickname = "openaiGoldenPipeline")
  @Operation(operationId = "openaiGoldenPipeline", description = "Select Golden Pipeline",
      summary = "Select Golden Pipeline",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Select Golden Pipeline")
      })
  public ResponseDTO<GoldenPipelineResponse>
  goldenPipelinePolicies(@NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountId,
      @NotNull @QueryParam(NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgId,
      @NotNull @QueryParam(NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectId,
      @QueryParam("goldenPipelineId") String goldenPipelineIdentifier) {
    OpenAiService service = new OpenAiService(OpenAiConstants.openAIKey, Duration.ofMinutes(10L));

    Optional<PipelineEntity> goldenPipelineEntity =
        pmsPipelineService.getPipeline(accountId, orgId, projectId, goldenPipelineIdentifier, false, false);

    if (goldenPipelineEntity.isEmpty()) {
      throw new InvalidRequestException("Golden Pipeline Identifier not found");
    }

    String queryForGPT =
        "Given the following pipeline YAML as reference, provide verbal OPA policies that can be recommended to other similar Pipelines. \n Cover OPA policies such as manager approval, build environment, security checks, deployment type, and rollback strategy. \n"
        + goldenPipelineEntity.get().getYaml();

    System.out.println("\nCreating completion...");

    List<ChatMessage> queries = new ArrayList<>();
    queries.add(new ChatMessage("assistant", queryForGPT));
    ChatCompletionRequest completionRequest =
        ChatCompletionRequest.builder().model("gpt-3.5-turbo").messages(queries).user("testing").build();

    ChatCompletionResult result = service.createChatCompletion(completionRequest);

    List<String> policyRecs = new ArrayList<>();
    for (ChatCompletionChoice choice : result.getChoices()) {
      String message = choice.getMessage().getContent();
      List<String> policyRec = List.of(message.split(".\\n\\n"));
      policyRecs.addAll(policyRec);
    }

    GoldenPipelineResponse response = GoldenPipelineResponse.builder().policyRecommendations(policyRecs).build();

    service.shutdownExecutor();
    return ResponseDTO.newResponse(response);
  }
}