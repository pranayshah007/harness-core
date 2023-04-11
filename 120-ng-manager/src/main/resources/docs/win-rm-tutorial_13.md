## Reference hosts in steps using expressions

You can use all of the `<+instance...>` expressions to reference your hosts.

For Microsoft Azure, AWS, or any platform-agnostic Physical Data Center (PDC):

* [<+instance.hostName>](../../../platform/12_Variables-and-Expressions/harness-variables.md#instance-host-name)
* [<+instance.host.hostName>](../../../platform/12_Variables-and-Expressions/harness-variables.md#instance-host-host-name)
* [<+instance.name>](../../../platform/12_Variables-and-Expressions/harness-variables.md#instance-name)

For Microsoft Azure or AWS:

* [<+instance.host.privateIp>](../../../platform/12_Variables-and-Expressions/harness-variables.md#instance-host-private-ip)
* [<+instance.host.publicIp>](../../../platform/12_Variables-and-Expressions/harness-variables.md#instance-host-public-ip)

`instance.name` has the same value as `instance.hostName`. Both are available for backward compatibility.
