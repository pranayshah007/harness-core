package software.wings.service.impl;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@AllArgsConstructor
public class ExecutionCount {
  public Integer count;
  public DayOfYear _id;
  public long createdAt;

  @Data
  @Builder
  @AllArgsConstructor
  public static class DayOfYear {
    int number;
  }
}