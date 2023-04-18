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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.theokanning.openai.OpenAiApi;
import com.theokanning.openai.completion.chat.ChatCompletionChoice;
import com.theokanning.openai.completion.chat.ChatCompletionRequest;
import com.theokanning.openai.completion.chat.ChatMessage;
import com.theokanning.openai.completion.chat.ChatMessageRole;
import com.theokanning.openai.service.OpenAiService;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
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
      "/Users/aleksabuha/repos2/harness-core/pipeline-service/service/src/main/resources/openai/yamlsamples/%s";
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
      "Follwing messages are pipelines samples. Your task will arrive after the samples.";
  public static final String PROMPT_2 =
      "When user specifes a deployment, it must be accompanied by appropriate steps under executions.\n"
      + "Additional steps will be added according to the user input. If the user did not specify where, you will add it at the end of steps.";
  public static final String PROMPT_3 =
      "You are an DevOps helper that handles deployment YAML files. You received pipeline samples in YAML format that can be used to create a new deployment.\n"
      + "You NEED to create a new deployment YAML based on the samples and the user input. Parse the user input and create YAML output based on it. Do not try to modify the existing samples, but fit the user\n"
      + "input into the samples. Please do not provide any explanations or other messages, reply with ONLY the YAML output.";
  public static final String PROMPT_4 =
      "Pipeline name : %s\nPipeline ID : %s\nPipeline Project: %s\n Pipeline Org : %s\n %s";

  public static final String DESCRIBE_PROMPT =
      "Can you explain this pipeline to me and please go into details? Reply with just an explanation without acknowledging it.\n %s";

  public static final String TOKEN = "";
  public String createPipelineWithAi(
      String accountId, String orgId, String projectId, String pipelineIdentifier, String pipelineName, String prompt) {
    OpenAiService service = getApiService();
    final List<ChatMessage> messages = new ArrayList<>();
    ChatMessage samplePrompts = new ChatMessage(ChatMessageRole.SYSTEM.value(), format(PROMPT_0, prompt));
    messages.add(samplePrompts);
    ChatCompletionRequest chatCompletionRequest =
        ChatCompletionRequest.builder().model("gpt-3.5-turbo").messages(messages).temperature(0.5).topP(0.7).build();
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

    ChatCompletionRequest chatCompletionRequest2 =
        ChatCompletionRequest.builder().model("gpt-3.5-turbo").messages(messages).temperature(0.5).topP(0.7).build();

    String content = service.createChatCompletion(chatCompletionRequest2).getChoices().get(0).getMessage().getContent();
    cleanYaml(content);

    messages.clear();
    messages.add(new ChatMessage(ChatMessageRole.SYSTEM.value(), format(DESCRIBE_PROMPT, content)));

    ChatCompletionRequest chatCompletionRequest3 =
        ChatCompletionRequest.builder().model("gpt-3.5-turbo").messages(messages).temperature(0.5).topP(0.7).build();
    String description =
        service.createChatCompletion(chatCompletionRequest3).getChoices().get(0).getMessage().getContent();
    System.out.println("DESCRIPTION: " + description);
    return content;
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
    OpenAiService service = new OpenAiService(api);
    return service;
  }

  private void cleanYaml(String content) {}
}
