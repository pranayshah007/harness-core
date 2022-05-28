package io.harness.ng.core.event;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.ng.core.variable.dto.VariableDTO;
import io.harness.ng.core.variable.services.VariableService;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(PL)
@Singleton
@Slf4j
public class VariableEntityCRUDEventHandler {
  private final VariableService variableService;

  @Inject
  public VariableEntityCRUDEventHandler(VariableService variableService) {
    this.variableService = variableService;
  }

  public boolean deleteAssociatedVariables(String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    List<String> variableIdentifiersList =
        fetchAllVariablesInGivenScope(accountIdentifier, orgIdentifier, projectIdentifier);
    variableService.deleteBatch(accountIdentifier, orgIdentifier, projectIdentifier, variableIdentifiersList);
    return true;
  }

  private List<String> fetchAllVariablesInGivenScope(
      String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    List<VariableDTO> variablesList = variableService.list(accountIdentifier, orgIdentifier, projectIdentifier);
    return variablesList.stream().map(VariableDTO::getIdentifier).collect(Collectors.toList());
  }
}
