package io.harness.ng.openai;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DocsQueryResponseDTO {
    String result;
}
