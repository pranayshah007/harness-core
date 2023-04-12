/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.chatgpt;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import lombok.experimental.UtilityClass;
import org.json.JSONObject;

@UtilityClass
public class ChatGptApi {
  private final int maxTokens = 4000;
  private final double temperature = 1.0;
  private final String apiPath = "/askBot";
  private final String apiKey = "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";
  private final String openAIUrl = "https://api.openai.com/v1/completions";
  private final String model = "text-davinci-003";
  private final String prefixMsg = "resolve helm ";

  public String askChatGpt(String errorMsg) throws IOException {
    // tmp hack to remove special characters and keep prompt short
    String modifiedMsg = prefixMsg + errorMsg.substring(errorMsg.indexOf("error:")).replace('/', '*');
    modifiedMsg = modifiedMsg.replaceAll("must equal \".*\"", "must equal \"rel-\"");
    modifiedMsg = modifiedMsg.replaceAll("current value is \".*\"", "current value is \"r-\"");
    HttpURLConnection con = (HttpURLConnection) new URL(openAIUrl).openConnection();

    con.setRequestMethod("POST");
    con.setRequestProperty("Content-Type", "application/json");
    con.setRequestProperty("Authorization", "Bearer " + apiKey);

    JSONObject data = new JSONObject();
    data.put("model", model);
    data.put("prompt", modifiedMsg);
    data.put("max_tokens", maxTokens);
    data.put("temperature", temperature);

    con.setDoOutput(true);
    con.getOutputStream().write(data.toString().getBytes());

    String output =
        new BufferedReader(new InputStreamReader(con.getInputStream())).lines().reduce((a, b) -> a + b).get();

    return new JSONObject(output).getJSONArray("choices").getJSONObject(0).getString("text");
  }
}
