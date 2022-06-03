package software.wings.beans;

import software.wings.beans.apm.Method;
import software.wings.beans.apm.ResponseType;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LogCollectionInfo {
  private String collectionUrl;
  private String collectionBody;
  private ResponseType responseType;
  private LogResponseMapping responseMapping;
  private Method method;
}
