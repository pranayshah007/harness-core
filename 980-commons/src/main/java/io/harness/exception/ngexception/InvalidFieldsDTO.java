package io.harness.exception.ngexception;

import com.fasterxml.jackson.annotation.JsonTypeName;
import java.util.Map;
import lombok.Builder;

@Builder
@JsonTypeName(ErrorMetadataConstants.INVALID_FIELDS_ERROR)
public class InvalidFieldsDTO implements ErrorMetadataDTO {
  Map<String, String> expectedValues;
  @Override
  public String getType() {
    return ErrorMetadataConstants.INVALID_FIELDS_ERROR;
  }
}
