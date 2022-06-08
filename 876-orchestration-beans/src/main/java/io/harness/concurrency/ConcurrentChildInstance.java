package io.harness.concurrency;

import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ConcurrentChildInstance {
  private List<String> childrenNodeExecutionIds;
  private int cursor; // the pointer to which node we should start from childrenNodeExecutionIds
}
