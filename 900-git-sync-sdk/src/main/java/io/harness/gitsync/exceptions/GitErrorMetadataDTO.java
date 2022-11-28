package io.harness.gitsync.exceptions;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.ngexception.ErrorMetadataConstants;
import io.harness.exception.ngexception.ErrorMetadataDTO;

import com.fasterxml.jackson.annotation.JsonTypeName;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@OwnedBy(HarnessTeam.PL)
@JsonTypeName(ErrorMetadataConstants.GIT_ERROR)
public class GitErrorMetadataDTO implements ErrorMetadataDTO {
  String branch;

  @Override
  public String getType() {
    return ErrorMetadataConstants.GIT_ERROR;
  }
}
