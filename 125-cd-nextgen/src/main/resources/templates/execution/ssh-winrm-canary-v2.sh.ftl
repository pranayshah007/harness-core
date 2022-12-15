${failureStrategies}
<#list phases as phase>
<#if phase_index=0>
${canarySnippet
?replace("<+start>", 0)
?replace("<+end>", phase)
?replace("<+unit>", unitType)
?replace("<+phase>", (phase_index+1))}
<#assign prevPhase = phase>
<#else>
${canarySnippet
?replace("spec:\n  execution:\n    steps:\n", "")
?replace("<+start>", prevPhase)
?replace("<+end>", phase)
?replace("<+unit>", unitType)
?replace("<+phase>", (phase_index+1))}
<#assign prevPhase = phase>
</#if>
</#list>
    rollbackSteps:
<#list phases as phase>
<#if phase_index=0>
${canaryRollbackSnippet
?replace("spec:\n  execution:\n    rollbackSteps:\n", "")
?replace("<+start>", 0)
?replace("<+end>", phase)
?replace("<+unit>", unitType)
?replace("<+phase>", (phase_index+1))}
<#assign prevPhase = phase>
<#else>
${canaryRollbackSnippet
?replace("spec:\n  execution:\n    rollbackSteps:\n", "")
?replace("<+start>", prevPhase)
?replace("<+end>", phase)
?replace("<+unit>", unitType)
?replace("<+phase>", (phase_index+1))}
<#assign prevPhase = phase>
</#if>
</#list>