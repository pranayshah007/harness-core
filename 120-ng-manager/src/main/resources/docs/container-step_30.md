## Set container resources

1. Set the maximum resources limit values for the resources used by the container at runtime.

- **Limit Memory:** The maximum memory that the container can use. You may express memory as a plain integer or as a fixed-point number using the suffixes G or M. You may also use the power-of-two equivalents Gi and Mi.
- **Limit CPU:** The maximum number of cores that the container can use. CPU limits are measured in cpu units. Fractional requests are allowed; you can specify one hundred millicpu as 0.1 or 100m. 

For more information, go to [Resource units in Kubernetes](https://kubernetes.io/docs/concepts/configuration/manage-resources-containers/#resource-units-in-kubernetes) from Kubernetes.
