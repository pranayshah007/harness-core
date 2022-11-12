package software.wings.delegatetasks.validation.core;

import static io.harness.annotations.dev.HarnessTeam.DEL;

import io.harness.annotations.dev.OwnedBy;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonTypeName;
import java.time.OffsetDateTime;
import java.util.Date;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@JsonTypeName("DelegateConnectionResult")
@JsonIgnoreProperties(ignoreUnknown = true)
@Data
@Builder
@AllArgsConstructor
@OwnedBy(DEL)
public class DelegateConnectionResult {
  private String uuid;
  private String accountId;
  private String delegateId;
  private String criteria;
  private boolean validated;
  private long duration;
  private long lastUpdatedAt;
  private Date validUntil = Date.from(OffsetDateTime.now().plusDays(30).toInstant());
}
