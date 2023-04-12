package software.wings.beans.notification;

import lombok.Data;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@Data
public class BotQuestion {
  String question;

  public BotQuestion(String question) {
    this.question = question;
  }

  public String getQuestion() {
    return question;
  }
}
