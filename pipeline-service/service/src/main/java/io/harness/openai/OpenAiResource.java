/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.openai;

import static io.harness.logging.CommandExecutionStatus.FAILURE;
import static io.harness.logging.CommandExecutionStatus.SUCCESS;

import io.harness.NGCommonEntityConstants;
import io.harness.accesscontrol.AccountIdentifier;
import io.harness.accesscontrol.OrgIdentifier;
import io.harness.accesscontrol.ProjectIdentifier;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.InvalidRequestException;
import io.harness.logging.CommandExecutionStatus;
import io.harness.ng.core.dto.ErrorDTO;
import io.harness.ng.core.dto.FailureDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.openai.dtos.GoldenPipelineResponse;
import io.harness.openai.dtos.Policy;
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
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
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

    List<ChatCompletionChoice> result = callChatGPT(service, queryForGPT);

    List<String> policyRecs = new ArrayList<>();
    for (ChatCompletionChoice choice : result) {
      String message = choice.getMessage().getContent();
      List<String> policyRec = List.of(message.split(".\\n\\n"));
      policyRecs.addAll(policyRec);
    }

    List<Policy> policiesWithCode = new ArrayList<>();

    for (String policyRec : policyRecs) {
      String regoCode = "";
      if (Character.isDigit(policyRec.charAt(0))) {
        String queryForGPTCode =
            "Create a rego for OPA rule. Format the output to include just the rego and no other text: " + policyRec;
        result = callChatGPT(service, queryForGPTCode);
        regoCode = result.get(0).getMessage().getContent();
      }
      policiesWithCode.add(new Policy(policyRec, regoCode));
    }
    GoldenPipelineResponse response = GoldenPipelineResponse.builder().policyRecommendations(policiesWithCode).build();

    service.shutdownExecutor();
    return ResponseDTO.newResponse(response);
  }

  public List<ChatCompletionChoice> callChatGPT(OpenAiService service, String query) {
    List<ChatMessage> queries = new ArrayList<>();
    queries.add(new ChatMessage("assistant", query));
    ChatCompletionRequest completionRequest =
        ChatCompletionRequest.builder().model("gpt-3.5-turbo").messages(queries).user("testing").build();

    return service.createChatCompletion(completionRequest).getChoices();
  }

  @GET
  @Path("/docs")
  @ApiOperation(value = "Get Docs Query", nickname = "getTemplates")
  public ResponseDTO<String> getDocs(@QueryParam("query") String query) {
    StringBuffer response = new StringBuffer();
    try {
      URL url = new URL("https://flask-docs-query-khaki-rho.vercel.app/docs");
      HttpURLConnection con = (HttpURLConnection) url.openConnection();
      con.setRequestMethod("POST");
      con.setDoOutput(true);
      con.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");

      String encodedQuery = query.replace(" ", "+");

      encodedQuery = "query=" + encodedQuery;
      OutputStream os = con.getOutputStream();
      os.write(encodedQuery.getBytes());
      os.flush();
      os.close();

      int responseCode = con.getResponseCode();
      System.out.println("Response Code: " + responseCode);

      BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
      String inputLine;

      while ((inputLine = in.readLine()) != null) {
        response.append(inputLine);
      }
      in.close();
    } catch (Exception ex) {
      log.error("Error while fetching docs");
    }

    return ResponseDTO.newResponse(response.toString());
  }
}