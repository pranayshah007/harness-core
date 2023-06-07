package io.harness.ccm.remote.resources.governance;

import static io.harness.annotations.dev.HarnessTeam.CE;

import io.harness.NGCommonEntityConstants;
import io.harness.accesscontrol.AccountIdentifier;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ccm.CENextGenConfiguration;
import io.harness.ccm.views.dto.GovernanceAiEngineRequestDTO;
import io.harness.ccm.views.dto.GovernanceAiEngineResponseDTO;
import io.harness.ccm.views.entities.Rule;
import io.harness.ccm.views.service.GovernanceRuleService;
import io.harness.ng.core.dto.ErrorDTO;
import io.harness.ng.core.dto.FailureDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.security.JWTTokenServiceUtils;
import io.harness.security.annotations.NextGenManagerAuth;

import com.google.common.base.CharMatcher;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import javax.validation.Valid;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@Api("governance")
@Path("governance")
@OwnedBy(CE)
@Tag(name = "AiEngine", description = "This contains APIs related to Generative AI Support for Governance ")
@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Bad Request",
    content =
    {
      @Content(mediaType = "application/json", schema = @Schema(implementation = FailureDTO.class))
      , @Content(mediaType = "application/yaml", schema = @Schema(implementation = FailureDTO.class))
    })
@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "Internal server error",
    content =
    {
      @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorDTO.class))
      , @Content(mediaType = "application/yaml", schema = @Schema(implementation = ErrorDTO.class))
    })
@ApiResponses(value =
    {
      @ApiResponse(code = 400, response = FailureDTO.class, message = "Bad Request")
      , @ApiResponse(code = 500, response = ErrorDTO.class, message = "Internal server error")
    })
public class GovernanceAiEngineResource {
  private final CENextGenConfiguration configuration;
  private final GovernanceRuleService governanceRuleService;

  private static final ImmutableMap<String, String> claims = ImmutableMap.of("iss", "Harness Inc", "sub", "CCM");

  @Inject
  public GovernanceAiEngineResource(CENextGenConfiguration configuration, GovernanceRuleService governanceRuleService) {
    this.configuration = configuration;
    this.governanceRuleService = governanceRuleService;
  }

  //@PublicApi
  @Hidden
  @NextGenManagerAuth
  @Path("aiengine")
  @POST
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  @ApiOperation(value = "Get generative ai generated yaml for governance", nickname = "aiengine")
  @Operation(operationId = "aiengine", description = "Get ai generated yaml for governance",
      summary = "Get ai generated yaml for governance",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(description = "Schema", content = { @Content(mediaType = MediaType.APPLICATION_JSON) })
      })
  public ResponseDTO<GovernanceAiEngineResponseDTO>
  aiengine(@Parameter(required = true, description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @QueryParam(
               NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier @Valid String accountIdentifier,
      @RequestBody(required = true, description = "Request body for queuing the governance job")
      @Valid GovernanceAiEngineRequestDTO governanceAiEngineRequestDTO) throws IOException {
    String PROMPT_PRIMING = "";
    String finalPrompt = "";
    String error = "";
    String prompt = governanceAiEngineRequestDTO.getPrompt();
    // TODO: Need not generate new token in every request.
    String jwtToken = JWTTokenServiceUtils.generateJWTToken(claims, TimeUnit.MILLISECONDS.convert(10, TimeUnit.MINUTES),
        configuration.getAiEngineConfig().getGenAIServiceConfig().getServiceSecret());
    // log.info("DEBUG: generated jwt {}", jwtToken);
    log.info("Using internal genai service");
    String API_ENDPOINT = configuration.getAiEngineConfig().getGenAIServiceConfig().getApiEndpoint();
    HttpURLConnection con = (HttpURLConnection) new URL(API_ENDPOINT).openConnection();
    con.setRequestMethod("POST");
    con.setRequestProperty("Content-Type", "application/json");
    con.setRequestProperty("Authorization", "Bearer " + jwtToken);

    if (!governanceAiEngineRequestDTO.getIsExplain()) {
      PROMPT_PRIMING =
          "You are a cloud custodian YAML generator, who generates Cloud custodian YAML outputs based on user inputs. "
          + "Do not respond to questions not related to cloud custodian.\n"
          + "\n"
          + "Refer to the online documentation and examples here : https://cloudcustodian.io/docs/ "
          + "\n "
          + "input: Delete unattached EBS Volumes\n"
          + "output: policies:\n"
          + "  - name: delete-unattached-volumes\n"
          + "    resource: ebs\n"
          + "    filters:\n"
          + "      - Attachments: []\n"
          + "      - State: available\n"
          + "    actions:\n"
          + "      - delete"
          + "\n\ninput: ";
      finalPrompt = PROMPT_PRIMING.concat(CharMatcher.is('\"').trimFrom(prompt)) + "\n output: \n";
    } else {
      PROMPT_PRIMING = "Explain this cloud custodian yaml policy."
          + "\n ";
      finalPrompt = PROMPT_PRIMING.concat(CharMatcher.is('\"').trimFrom(prompt));
    }

    log.info("input prompt is: {}", CharMatcher.is('\"').trimFrom(prompt));
    log.info("finalPrompt prompt is: {}", finalPrompt);
    // Prep request body for internalGenAIService

    JSONObject data = new JSONObject();
    JSONObject parameters = new JSONObject();
    parameters.put("temperature", configuration.getAiEngineConfig().getGenAIServiceConfig().getTemperature());
    parameters.put("max_output_tokens", configuration.getAiEngineConfig().getGenAIServiceConfig().getMaxDecodeSteps());
    parameters.put("top_p", configuration.getAiEngineConfig().getGenAIServiceConfig().getTopP());
    parameters.put("top_k", configuration.getAiEngineConfig().getGenAIServiceConfig().getTopK());
    data.put("prompt", finalPrompt);
    data.put("provider", "vertexai");
    data.put("model_name", configuration.getAiEngineConfig().getGenAIServiceConfig().getModel());
    data.put("model_parameters", parameters);

    log.info("request payload:  {}", data.toString());

    con.setDoOutput(true);
    con.getOutputStream().write(data.toString().getBytes());
    String output =
        new BufferedReader(new InputStreamReader(con.getInputStream())).lines().reduce((a, b) -> a + b).get();
    log.info("output:  {}", output);
    JSONObject responseObject = new JSONObject(output);
    String result = responseObject.getString("text");
    log.info("result:  {}", result);
    if (governanceAiEngineRequestDTO.getIsExplain()) {
      return ResponseDTO.newResponse(GovernanceAiEngineResponseDTO.builder().text(result).build());
    }
    Rule validateRule = Rule.builder()
                            .rulesYaml(CharMatcher.is('\"').trimFrom(result))
                            .name(UUID.randomUUID().toString().replace("-", ""))
                            .accountId(accountIdentifier)
                            .build();
    try {
      governanceRuleService.custodianValidate(validateRule);
    } catch (Exception ex) {
      log.error("Error: ", ex);
      error = ex.getMessage();
      return ResponseDTO.newResponse(
          GovernanceAiEngineResponseDTO.builder().text(result).isValid(false).error(error).build());
    }
    return ResponseDTO.newResponse(
        GovernanceAiEngineResponseDTO.builder().text(result).isValid(true).error("").build());
  }
}
