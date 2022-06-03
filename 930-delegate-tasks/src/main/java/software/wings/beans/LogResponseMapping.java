package software.wings.beans;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LogResponseMapping {
  private String logMessageJsonPath;
  private String hostJsonPath;
  private String hostRegex;
  private String timestampJsonPath;
  @Deprecated private String timeStampFormat;
  private String timestampFormat;

  public void setTimestampFormat(String format) {
    this.timestampFormat = format;
  }

  public String getTimestampFormat() {
    return isNotEmpty(timestampFormat) ? timestampFormat : timeStampFormat;
  }
}