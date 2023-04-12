package io.harness.openai.service;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.theokanning.openai.completion.chat.ChatCompletionChoice;
import com.theokanning.openai.completion.chat.ChatCompletionRequest;
import com.theokanning.openai.completion.chat.ChatCompletionResult;
import com.theokanning.openai.completion.chat.ChatMessage;
import com.theokanning.openai.completion.chat.ChatMessageRole;
import io.harness.openai.dtos.SimilarityResponse;

import com.google.inject.Singleton;
import io.harness.pms.pipeline.PipelineEntity;
import io.harness.pms.pipeline.service.PMSPipelineService;

import java.util.ArrayList;
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
    messageList.add(new ChatMessage(ChatMessageRole.SYSTEM.value(), "Tell me the structural similarity of the 2 yamls in percentage?"));
    ChatCompletionResult result = openAiService.createChatCompletion(ChatCompletionRequest.builder().model("gpt-3.5-turbo")
        .messages(messageList).temperature(0d).build());

    List<String> responses = new ArrayList<>();
    for (ChatCompletionChoice choice : result.getChoices()) {
      System.out.println(choice.getMessage().getContent());
      responses.add(choice.getMessage().getContent());
    }
    return SimilarityResponse.builder().response(responses).build();
  }
}
