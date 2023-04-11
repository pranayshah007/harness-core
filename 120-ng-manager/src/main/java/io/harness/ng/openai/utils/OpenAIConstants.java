/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.openai.utils;

public class OpenAIConstants {
  // The OpenAI model used for generating answers (GPT-3.5-turbo)
  public static final String COMPLETIONS_MODEL = "gpt-3.5-turbo";
  // The OpenAI model used for generating text embeddings (text-embedding-ada-002)
  public static final String EMBEDDING_MODEL = "text-embedding-ada-002";
  // The API key for OpenAI, loaded from an environment variable.
  public static final String API_KEY = System.getenv("OPENAI_API_KEY");
  // A Path object pointing to the directory where the split documentation sections will be saved.
  public static final String DESTINATION_PATH = "docs/";
  // The path to the pickle file used for caching embeddings.
  public static final String EMBEDDINGS_PATH = "docs/embeddings.pickle";
  // The maximum number of matching documents to consider when generating a response.
  public static final int MAX_MATCHING_DOCUMENTS = 3;
}
