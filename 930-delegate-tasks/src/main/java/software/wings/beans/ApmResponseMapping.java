package software.wings.beans;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ApmResponseMapping {
  private String txnNameFieldValue;
  private String txnNameJsonPath;
  private String txnNameRegex;
  private String metricValueJsonPath;
  private String hostJsonPath;
  private String hostRegex;
  private String timestampJsonPath;
  private String timeStampFormat;
}
