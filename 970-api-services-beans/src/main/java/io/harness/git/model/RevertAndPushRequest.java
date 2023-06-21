package io.harness.git.model;

import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class RevertAndPushRequest extends GitBaseRequest {
  private boolean pushOnlyIfHeadSeen;
  private boolean forcePush;
  private String commitMessage;
  private String authorName;
  private String authorEmail;
}