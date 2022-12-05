package io.harness.delegate.beans;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import lombok.Builder;
import lombok.Data;

import java.util.Date;

@Data
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
@OwnedBy(HarnessTeam.DEL)
public class DelegateVersionDTO {
    private String delegateImage;
    private String metaData;
    private Date validUntil;
}
