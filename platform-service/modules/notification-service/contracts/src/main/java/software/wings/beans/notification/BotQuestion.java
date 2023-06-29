/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans.notification;

public class BotQuestion {
  String model;
  String question;

  public BotQuestion() {}

  public BotQuestion(String model, String question) {
    this.model = model;
    this.question = question;
  }

  public String getQuestion() {
    return question;
  }

  public String getModel() {
    return model;
  }
}
