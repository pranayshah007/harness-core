package io.harness.openai.service;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.harness.openai.dtos.SimilarityResponse;
import io.harness.openai.dtos.TemplateResponse;
import io.harness.pms.pipeline.PipelineEntity;
import io.harness.pms.pipeline.service.PMSPipelineService;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.theokanning.openai.completion.chat.ChatCompletionChoice;
import com.theokanning.openai.completion.chat.ChatCompletionRequest;
import com.theokanning.openai.completion.chat.ChatCompletionResult;
import com.theokanning.openai.completion.chat.ChatMessage;
import com.theokanning.openai.completion.chat.ChatMessageRole;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@Singleton
public class IntelligenceService {
  @Inject PMSPipelineService pmsPipelineService;
  @Inject @Named("openAiService") com.theokanning.openai.service.OpenAiService openAiService;

  public SimilarityResponse getStructureSimilarity(
      String accountId, String orgId, String projectId, String pipelineIdentifier1, String pipelineIdentifier2) {
    Optional<PipelineEntity> pipeline1 =
        pmsPipelineService.getPipeline(accountId, orgId, projectId, pipelineIdentifier1, false, false);
    Optional<PipelineEntity> pipeline2 =
        pmsPipelineService.getPipeline(accountId, orgId, projectId, pipelineIdentifier2, false, false);
    String yaml1 = pipeline1.get().getYaml();
    String yaml2 = pipeline2.get().getYaml();

    List<ChatMessage> messageList = new ArrayList<>();
    messageList.add(new ChatMessage(ChatMessageRole.SYSTEM.value(), "Providing 2 yamls:"));
    messageList.add(new ChatMessage(ChatMessageRole.SYSTEM.value(), yaml1));
    messageList.add(new ChatMessage(ChatMessageRole.SYSTEM.value(), yaml2));
    messageList.add(new ChatMessage(
        ChatMessageRole.SYSTEM.value(), "Tell me the structural similarity of the 2 yamls in percentage?"));
    ChatCompletionResult result = openAiService.createChatCompletion(
        ChatCompletionRequest.builder().model("gpt-3.5-turbo").messages(messageList).temperature(0d).build());

    List<String> responses = new ArrayList<>();
    for (ChatCompletionChoice choice : result.getChoices()) {
      System.out.println(choice.getMessage().getContent());
      responses.add(choice.getMessage().getContent());
    }
    return SimilarityResponse.builder().response(responses).build();
  }

  public TemplateResponse getTemplates(
      String accountId, String orgId, String projectId, String pipelineIdentifier1, String pipelineIdentifier2) throws IOException {
    Optional<PipelineEntity> pipeline1 =
        pmsPipelineService.getPipeline(accountId, orgId, projectId, pipelineIdentifier1, false, false);
    Optional<PipelineEntity> pipeline2 =
        pmsPipelineService.getPipeline(accountId, orgId, projectId, pipelineIdentifier2, false, false);
    String yaml1 = pipeline1.get().getYaml();
    String yaml2 = pipeline2.get().getYaml();

    List<ChatMessage> messageList = new ArrayList<>();
    messageList.add(new ChatMessage(ChatMessageRole.SYSTEM.value(), "Providing 2 yamls:"));
    messageList.add(new ChatMessage(ChatMessageRole.SYSTEM.value(), yaml1));
    messageList.add(new ChatMessage(ChatMessageRole.SYSTEM.value(), yaml2));
    messageList.add(new ChatMessage(ChatMessageRole.SYSTEM.value(),
        "For the 2 yamls, if some steps are similar, just give me the template for these steps"));
    messageList.add(new ChatMessage(ChatMessageRole.SYSTEM.value(),
        "Also give me the pipeline yamls with templates linked"));
    messageList.add(new ChatMessage(ChatMessageRole.SYSTEM.value(),
        "Provide the response in Json format so I can parse easily. It should contain 3 fields - template, pipeline1, pipeline2"));
    messageList.add(new ChatMessage(ChatMessageRole.SYSTEM.value(),
        "Note that pipeline1 and pipeline2 should have templates linked"));
    ChatCompletionResult result = openAiService.createChatCompletion(
        ChatCompletionRequest.builder().model("gpt-3.5-turbo").messages(messageList).temperature(0d).build());

    List<String> templates = new ArrayList<>();
    for (ChatCompletionChoice choice : result.getChoices()) {
      System.out.println(choice.getMessage().getContent());
      templates.add(choice.getMessage().getContent());
    }

    ChatCompletionChoice chatCompletionChoice = result.getChoices().get(0);
    String content = chatCompletionChoice.getMessage().getContent();
    Gson gson = new Gson();
    JsonObject jsonObject = gson.fromJson(content, JsonObject.class);
    JsonElement template = jsonObject.get("template");
    JsonElement linkedPipeline1 = jsonObject.get("pipeline1");
    JsonElement linkedPipeline2 = jsonObject.get("pipeline2");

    return TemplateResponse.builder()
        .templates(Arrays.asList(String.valueOf(template)))
        .pipelineYaml1(String.valueOf(linkedPipeline1))
        .pipelineYaml2(String.valueOf(linkedPipeline2))
        .build();
  }
}
