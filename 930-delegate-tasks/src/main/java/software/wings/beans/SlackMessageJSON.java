package software.wings.beans;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class SlackMessageJSON {
  private String outgoingWebhookUrl;
  private String message;
}
