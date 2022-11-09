package io.harness.delegate.beans;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.SerializationFormat;

import java.util.Map;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldNameConstants;

@Data
@Builder
@FieldNameConstants(innerTypeName = "TaskDataV2Keys")
@TargetModule(HarnessModule._955_DELEGATE_BEANS)
@OwnedBy(HarnessTeam.DEL)
public class TaskDataV2 {
  public static final long DEFAULT_SYNC_CALL_TIMEOUT = 60 * 1000L;
  public static final long DEFAULT_ASYNC_CALL_TIMEOUT = 10 * 60 * 1000L;

  private boolean parked;
  private boolean async;
  @NotNull private String taskType;
  private Object[] parameters;
  private long timeout;
  private int expressionFunctorToken;
  Map<String, String> expressions;

  @Builder.Default SerializationFormat serializationFormat = SerializationFormat.KRYO;
  private byte[] data;
}
