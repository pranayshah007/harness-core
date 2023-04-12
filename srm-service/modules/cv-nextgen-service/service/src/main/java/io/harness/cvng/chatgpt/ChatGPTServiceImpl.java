/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.chatgpt;

import io.harness.cvng.client.ChatGPTClient;

import java.io.IOException;

public class ChatGPTServiceImpl implements ChatGPTService {
  ChatGPTClient chatGPTClient = new ChatGPTClient();

  private final String apiToken = "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";

  @Override
  public String getChatGPTResponse(String apiPath) throws IOException {
    return chatGPTClient.chatGPTResponse(this.apiToken, apiPath);
  }
}
