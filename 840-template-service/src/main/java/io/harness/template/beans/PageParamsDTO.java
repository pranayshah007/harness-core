package io.harness.template.beans;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.OwnedBy;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.annotations.ApiModel;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.Builder;
import lombok.Data;

@OwnedBy(CDC)
@Data
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@ApiModel("PageRequestParams")
@Schema(name = "PageRequestParams", description = "Page Request Params")
public class PageParamsDTO {
  int page;
  int size;
  List<String> sort;
}
