package io.harness.openai.dtos;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@AllArgsConstructor
public class Policy {
    String response;
    String regoCode;
}
