/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.helpers;

import static com.theokanning.openai.service.OpenAiService.defaultClient;
import static com.theokanning.openai.service.OpenAiService.defaultObjectMapper;
import static com.theokanning.openai.service.OpenAiService.defaultRetrofit;
import static java.lang.String.format;

import com.fasterxml.jackson.databind.JsonNode;
import io.harness.environment.remote.EnvironmentResourceClient;
import io.harness.infrastructure.InfrastructureResourceClient;
import io.harness.plancreator.pipeline.PipelineConfig;
import io.harness.plancreator.stages.StageElementWrapperConfig;
import io.harness.pms.pipeline.PipelineAiGenerationResponseDTO;
import io.harness.pms.pipeline.mappers.PipelineYamlDtoMapper;
import io.harness.pms.yaml.YamlUtils;
import io.harness.service.remote.ServiceResourceClient;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.theokanning.openai.OpenAiApi;
import com.theokanning.openai.completion.chat.ChatCompletionRequest;
import com.theokanning.openai.completion.chat.ChatMessage;
import com.theokanning.openai.completion.chat.ChatMessageRole;
import com.theokanning.openai.service.OpenAiService;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import retrofit2.Retrofit;

@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@Slf4j
@Singleton
public class PipelineAIHelper {
  private static final String SAMPLE_PATH =
      "/Users/markomilic/Projects/harness-core/pipeline-service/service/src/main/resources/openai/yamlsamples/%s";
  public static final String PROMPT_0 =
      "Based on the following input you need to figure out what deployment type user wants to use.\n"
      + "Supported deployment types are:\n"
      + "Kubernetes\n"
      + "Helm\n"
      + "User might also want to run Build in that case respond with Build + Deployment Type.\n"
      + "Respond only with BUILD + DEPLOYMENT TYPE or just DEPLOYMENT TYPE.\n"
      + "Input :\n"
      + "%s";
  public static final String PROMPT_1 =
      "Following messages are pipelines samples. Your task will arrive after the samples.";
  public static final String PROMPT_2 =
      "When user specifes a deployment, it must be accompanied by appropriate steps under executions.\n"
      + "Additional steps will be added according to the user input. If the user did not specify where, you will add it at the end of steps."
      + "No stages or steps will have the same identifier. Steps can have same names if they are under different stages.";
  public static final String PROMPT_3 =
      "You are a DevOps helper that handles deployment YAML files. You received pipeline samples in YAML format that can be used to create a new deployment.\n"
      + "You NEED to create a new deployment YAML based on the samples and the user input. Parse the user input and create YAML output based on it. Do not try to modify the existing samples, but fit the user\n"
      + "input into the samples. Please do not provide any explanations or other messages, reply with ONLY the YAML output.";
  public static final String PROMPT_4 =
      "Pipeline name : %s\nPipeline ID : %s\nPipeline Project: %s\n Pipeline Org : %s\n %s";

  public static final String DESCRIBE_PROMPT =
      "Explain the following yaml by going through each 'step' and please go into great detail explaining those steps and what they will do. Do not include any additional details. Return a JSON object that will have parent stage and then step description. For JSON object use identifier and not name. Please follow this sample output :\n%s\n YAML to describe and explain:\n %s";

  public static final String EXAMPLE_FOR_DESCRIPTION = "{\n"
      + "  \"Stage_Identifier\": {\n"
      + "    \"Step_Identifier\": \"This step will deploy the Kubernetes deployment for the nginx service. It will roll out the deployment in a rolling fashion. It will wait for the specified timeout duration before marking the deployment as successful.\",\n"
      + "    \"Step_Identifier\": \"This step will execute a shell script on the delegate. The shell script will simply print the string 'test' to the console.\"\n"
      + "  },\n"
      + "  \"Stage_Identifier\": {\n"
      + "    \"Step_Identifier\": \"This step will deploy the Kubernetes deployment for the busybox service. It will roll out the deployment in a rolling fashion. It will wait for the specified timeout duration before marking the deployment as successful.\"\n"
      + "  }\n"
      + "}";
  public static final String GPT = "gpt-3.5-turbo";

  public static final String TOKEN = "toDo";

  public static JsonNode DESCRIPTION_JSON = null;

  @Inject private ServiceResourceClient serviceResourceClient;
  @Inject private EnvironmentResourceClient environmentClient;
  @Inject private InfrastructureResourceClient infraClient;

  public PipelineAiGenerationResponseDTO createPipelineWithAi(
      String accountId, String orgId, String projectId, String pipelineIdentifier, String pipelineName, String prompt) {
    OpenAiService service = getApiService();
    final List<ChatMessage> messages = new ArrayList<>();
    ChatMessage samplePrompts = new ChatMessage(ChatMessageRole.SYSTEM.value(), format(PROMPT_0, prompt));
    messages.add(samplePrompts);
    ChatCompletionRequest chatCompletionRequest =
        ChatCompletionRequest.builder().model(GPT).messages(messages).temperature(0.5).topP(0.7).build();
    String deploymentType =
        service.createChatCompletion(chatCompletionRequest).getChoices().get(0).getMessage().getContent();

    String folderPath = null;
    if (deploymentType.toUpperCase().contains("KUBERNETES")) {
      if (deploymentType.toUpperCase().contains("BUILD")) {
        folderPath = "kubernetes_cicd";
      } else {
        folderPath = "kubernetes";
      }
    }
    messages.clear();

    messages.add(new ChatMessage(ChatMessageRole.SYSTEM.value(), PROMPT_1));
    messages.addAll(getSamplePipelines(folderPath)
                        .stream()
                        .map(yaml -> new ChatMessage(ChatMessageRole.SYSTEM.value(), "SAMPLE YAML\n" + yaml))
                        .collect(Collectors.toList()));
    messages.add(new ChatMessage(ChatMessageRole.SYSTEM.value(), PROMPT_2));
    messages.add(new ChatMessage(ChatMessageRole.SYSTEM.value(), PROMPT_3));
    messages.add(new ChatMessage(
        ChatMessageRole.SYSTEM.value(), format(PROMPT_4, pipelineName, pipelineIdentifier, projectId, orgId, prompt)));

    String content = service.createChatCompletion(chatCompletionRequest).getChoices().get(0).getMessage().getContent();
    String cleanedYam = cleanYaml(content);

    messages.clear();
    messages.add(
        new ChatMessage(ChatMessageRole.SYSTEM.value(), format(DESCRIBE_PROMPT, EXAMPLE_FOR_DESCRIPTION, cleanedYam)));

    String description =
        service.createChatCompletion(chatCompletionRequest).getChoices().get(0).getMessage().getContent();

    processDescription(description);
    System.out.println("DESCRIPTION: " + description);

    //    final var pipelineEntity = PMSPipelineDtoMapper.toPipelineEntity(accountId, orgId, projectId, cleanedYam);
    log.info("Acc/org/proj: {}/{}/{}", accountId, orgId, projectId);
    final var pipelineConfig = PipelineYamlDtoMapper.toDto(cleanedYam);

    final var missingServices = getMissingServices(pipelineConfig, accountId, orgId, projectId);
    final var missingEnvironments = getMissingEnvironments(pipelineConfig, accountId, orgId, projectId);
    final var missingInfra = getMissingInfra(pipelineConfig, accountId, orgId, projectId);

    return new PipelineAiGenerationResponseDTO(cleanedYam, missingServices, missingEnvironments, missingInfra);
  }

  List<String> getSamplePipelines(String folderName) {
    List<String> yamls = new ArrayList<>();
    File folder = new File(format(SAMPLE_PATH, folderName));
    File[] listOfFiles = folder.listFiles();

    for (File file : listOfFiles) {
      if (file.isFile()) {
        try {
          yamls.add(Files.readString(file.toPath()));
        } catch (IOException e) {
          e.printStackTrace();
        }
      }
    }
    return yamls;
  }

  private OpenAiService getApiService() {
    ObjectMapper mapper = defaultObjectMapper();
    Retrofit retrofit = defaultRetrofit(defaultClient(TOKEN, Duration.ofSeconds(60)), mapper);
    OpenAiApi api = retrofit.create(OpenAiApi.class);
    return new OpenAiService(api);
  }

  private String cleanYaml(String content) {
    if (content.contains("```yaml")) {
      content = content.split("```yaml")[1].split("```")[0];
      return content.lines().filter(s -> !s.isEmpty()).collect(Collectors.joining("\n"));
    } else if (content.contains("```")) {
      content = content.split("```")[1].split("```")[0];
      return content.lines().filter(s -> !s.isEmpty()).collect(Collectors.joining("\n"));
    } else {
      return content.lines().filter(s -> !s.isEmpty()).collect(Collectors.joining("\n"));
    }
  }

  private static void processDescription(String description) {
    try {
      ObjectMapper objectMapper = new ObjectMapper();
      DESCRIPTION_JSON = objectMapper.readValue(description, JsonNode.class);
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  public String getDescription(String stepId) {
    return DESCRIPTION_JSON.findValuesAsText(stepId).get(0);

  }

  private List<String> getMissingServices(
      final PipelineConfig pipelineConfig, final String accountId, final String orgId, final String projectId) {
    return pipelineConfig.getPipelineInfoConfig()
        .getStages()
        .stream()
        .filter(stage -> {
          final var generatedServiceRef = stage.getStage().get("spec").get("service").get("serviceRef").asText();
          return !doesServiceExist(generatedServiceRef, accountId, orgId, projectId);
        })
        .map(stage -> stage.getStage().get("spec").get("service").get("serviceRef").asText())
        .collect(Collectors.toList());
  }

  private List<String> getMissingEnvironments(
      final PipelineConfig pipelineConfig, final String accountId, final String orgId, final String projectId) {
    return pipelineConfig.getPipelineInfoConfig()
        .getStages()
        .stream()
        .filter(stage -> {
          final var generateEnvironmentRef =
              stage.getStage().get("spec").get("environment").get("environmentRef").asText();
          return !doesEnvironmentExist(generateEnvironmentRef, accountId, orgId, projectId);
        })
        .map(stage -> stage.getStage().get("spec").get("environment").get("environmentRef").asText())
        .collect(Collectors.toList());
  }

  private Map<String, List<String>> getMissingInfra(
      final PipelineConfig pipelineConfig, final String accountId, final String orgId, final String projectId) {
    return pipelineConfig.getPipelineInfoConfig()
                            .getStages()
                            .stream()
                            .filter(stage -> stage.getStage().get("spec").get("environment").has("environmentRef"))
                            .filter(stage -> {
                              final var environmentRef =
                                  stage.getStage().get("spec").get("environment").get("environmentRef").asText();
                              final var generatedInfraRefs = stage.getStage()
                                                                 .get("spec")
                                                                 .get("environment")
                                                                 .get("infrastructureDefinitions")
                                                                 .findValuesAsText("identifier");
                              return !doesInfraExist(generatedInfraRefs, environmentRef, accountId, orgId, projectId);
                            })
                            .collect(Collectors.groupingBy(stage
                                -> stage.getStage().get("spec").get("environment").get("environmentRef").asText(),
                                Collectors.mapping(stage
                                    -> stage.getStage()
                                           .get("spec")
                                           .get("environment")
                                           .get("infrastructureDefinitions")
                                           .findValuesAsText("identifier"),
                                    Collectors.flatMapping(Collection::stream, Collectors.toList()))));
  }

  private void normalizeService(
      final PipelineConfig pipelineConfig, final String accountId, final String orgId, final String projectId) {
    pipelineConfig.getPipelineInfoConfig()
        .getStages()
        .stream()
        .filter(stage -> {
          final var generatedServiceRef = stage.getStage().get("spec").get("service").get("serviceRef").asText();
          return !doesServiceExist(generatedServiceRef, accountId, orgId, projectId);
        })
        .forEach(stage -> {
          log.warn("Service with ref {} doesn't exist, removing it from yaml",
              stage.getStage().get("spec").get("service").get("serviceRef").asText());
          ((ObjectNode) stage.getStage().get("spec").get("service")).removeAll();
        });
  }

  private void normalizeEnvironment(
      final PipelineConfig pipelineConfig, final String accountId, final String orgId, final String projectId) {
    pipelineConfig.getPipelineInfoConfig()
        .getStages()
        .stream()
        .filter(stage -> {
          final var generateEnvironmentRef =
              stage.getStage().get("spec").get("environment").get("environmentRef").asText();
          return !doesEnvironmentExist(generateEnvironmentRef, accountId, orgId, projectId);
        })
        .forEach(stage -> {
          log.warn("Environment with ref {} doesn't exist, removing it from yaml",
              stage.getStage().get("spec").get("environment").get("environmentRef").asText());
          ((ObjectNode) stage.getStage().get("spec").get("environment")).removeAll();
        });
  }

  private void normalizeInfra(
      final PipelineConfig pipelineConfig, final String accountId, final String orgId, final String projectId) {
    pipelineConfig.getPipelineInfoConfig()
        .getStages()
        .stream()
        .filter(stage -> stage.getStage().get("spec").get("environment").has("environmentRef"))
        .filter(stage -> {
          final var environmentRef = stage.getStage().get("spec").get("environment").get("environmentRef").asText();
          final var generatedInfraRefs = stage.getStage()
                                             .get("spec")
                                             .get("environment")
                                             .get("infrastructureDefinitions")
                                             .findValuesAsText("identifier");
          return !doesInfraExist(generatedInfraRefs, environmentRef, accountId, orgId, projectId);
        })
        .forEach(stage -> {
          log.warn("Infra for env {} doesn't exist removing it from yaml",
              stage.getStage().get("spec").get("environment").get("environmentRef").asText());
          ((ObjectNode) stage.getStage().get("spec").get("environment").get("infrastructureDefinitions")).removeAll();
        });
  }

  private boolean doesServiceExist(
      final String serviceRef, final String accountId, final String orgId, final String projectId) {
    try {
      final var exists =
          serviceResourceClient.getService(serviceRef, accountId, orgId, projectId).execute().isSuccessful();
      log.info("Service {} exists {}", serviceRef, exists);
      return exists;
    } catch (IOException e) {
      log.error("Failed to get the service by ref {}", serviceRef, e);
      return false;
    }
  }

  private boolean doesEnvironmentExist(
      final String environmentRef, final String accountId, final String orgId, final String projectId) {
    try {
      final var exists =
          environmentClient.getEnvironment(environmentRef, accountId, orgId, projectId).execute().isSuccessful();
      log.info("Environment {} exists {}", environmentRef, exists);
      return exists;
    } catch (IOException e) {
      log.error("Failed to get the environment by ref {}", environmentRef, e);
      return false;
    }
  }

  private boolean doesInfraExist(final List<String> infraRefs, final String environmentRef, final String accountId,
      final String orgId, final String projectId) {
    try {
      for (final String infraRef : infraRefs) {
        if (!infraClient.getInfra(infraRef, accountId, orgId, projectId, environmentRef).execute().isSuccessful()) {
          log.info("infraRef doesn't exist {}", infraRef);
          return false;
        }
      }
      log.info("All infra exists");
      return true;
    } catch (IOException e) {
      log.error("Failed to get the environment by ref {}", environmentRef, e);
      return false;
    }
  }

  private boolean doesInfraExist(final String infraRef, final String environmentRef, final String accountId,
      final String orgId, final String projectId) {
    try {
      final var exists =
          infraClient.getInfra(infraRef, accountId, orgId, projectId, environmentRef).execute().isSuccessful();
      log.info("InfraRef {} exists {}", environmentRef, exists);
      return exists;
    } catch (IOException e) {
      log.error("Failed to get the environment by ref {}", environmentRef, e);
      return false;
    }
  }
}
