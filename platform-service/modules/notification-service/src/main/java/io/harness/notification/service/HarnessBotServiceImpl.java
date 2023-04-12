/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.notification.service;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.notification.service.api.HarnessBotService;

import software.wings.beans.notification.BotQuestion;
import software.wings.beans.notification.BotResponse;

import com.google.inject.Inject;
import com.theokanning.openai.completion.CompletionRequest;
import com.theokanning.openai.embedding.EmbeddingRequest;
import com.theokanning.openai.service.OpenAiService;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
@OwnedBy(PL)
public class HarnessBotServiceImpl implements HarnessBotService {
  private static final OpenAiService service = new OpenAiService("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");
  private static final List<Embedding> embeddings = readEmbeddings("/opt/harness/embeddings.csv");

  private static List<Embedding> readEmbeddings(String filePath) {
    List<Embedding> result = new ArrayList<>();
    try {
      for (String line : Files.readAllLines(Paths.get(filePath))) {
        if (!line.equals("text,n_tokens,embeddings")) {
          int startOfEmbeddingColumn = line.lastIndexOf(",\"");
          int startOfTokenCountColumn = line.substring(0, startOfEmbeddingColumn).lastIndexOf(",");
          result.add(new Embedding(line.substring(0, startOfTokenCountColumn).trim(),
              Long.parseLong(line.substring(startOfTokenCountColumn + 1, startOfEmbeddingColumn)),
              toDoubleArray(line.substring(startOfEmbeddingColumn + 3, line.lastIndexOf("]")))));
        }
      }
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
    return result;
  }

  private static List<Double> toDoubleArray(String s) {
    String[] split = s.split(", ");
    List<Double> result = new ArrayList<>();
    for (String s1 : split) {
      result.add(Double.valueOf(s1));
    }
    return result;
  }

  @Override
  public BotResponse answer(BotQuestion question) {
    return new BotResponse(getAnswer(question.getQuestion()));
  }

  private String getAnswer(String question) {
    String context = createContext(question, embeddings, 3000);

    try {
      String prompt = String.format(
          "Answer the question based on the context below, and if the question can't be answered based on the context, say \"I don't know\"\n\nContext: %s\n\n---\n\nQuestion: %s\nAnswer:",
          context, question);
      CompletionRequest completionRequest = CompletionRequest.builder()
                                                .prompt(prompt)
                                                .model("text-davinci-003")
                                                .topP(1.0)
                                                .temperature(0.0)
                                                .maxTokens(500)
                                                .build();
      return service.createCompletion(completionRequest).getChoices().get(0).getText().trim();
    } catch (Exception ex) {
      log.error("Unexpected error occurred", ex);
      return "";
    }
  }

  private static String createContext(String query, List<Embedding> textEmbeddings, int maxLength) {
    List<Double> queryEmbedding = getEmbedding(query);

    List<String> result = new ArrayList<>();
    long currentLength = 0;

    List<CosineDistance> distances = calculateCosineDistances(queryEmbedding, textEmbeddings);
    distances.sort((o1, o2) -> Double.compare(o2.distance, o1.distance));

    for (CosineDistance distance : distances) {
      currentLength += distance.embedding.numOfTokens + 4;
      if (currentLength > maxLength) {
        break;
      }
      result.add(distance.embedding.text);
    }

    return String.join("\n\n###\n\n", result);
  }

  private static List<CosineDistance> calculateCosineDistances(
      List<Double> queryEmbedding, List<Embedding> textEmbeddings) {
    List<CosineDistance> result = new ArrayList<>();
    for (Embedding textEmbedding : textEmbeddings) {
      double dotProduct = 0.0;
      double normA = 0.0;
      double normB = 0.0;
      for (int i = 0; i < queryEmbedding.size(); i++) {
        dotProduct += queryEmbedding.get(i) * textEmbedding.embedding.get(i);
        normA += Math.pow(queryEmbedding.get(i), 2);
        normB += Math.pow(textEmbedding.embedding.get(i), 2);
      }
      double distance = dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
      result.add(new CosineDistance(textEmbedding, distance));
    }
    return result;
  }

  private static List<Double> getEmbedding(String query) {
    return service
        .createEmbeddings(EmbeddingRequest.builder().model("text-embedding-ada-002").input(List.of(query)).build())
        .getData()
        .get(0)
        .getEmbedding();
  }

  private static class Embedding {
    String text;
    long numOfTokens;
    List<Double> embedding;

    public Embedding(String text, long numOfTokens, List<Double> embedding) {
      this.text = text;
      this.numOfTokens = numOfTokens;
      this.embedding = embedding;
    }
  }

  private static class CosineDistance {
    HarnessBotServiceImpl.Embedding embedding;
    double distance;

    public CosineDistance(HarnessBotServiceImpl.Embedding embedding, double distance) {
      this.embedding = embedding;
      this.distance = distance;
    }
  }
}
