# Dry Run steps and Skip Dry Run settings

The Apply, Rolling, Canary, and Blue Green deployment steps include a **Skip Dry Run** setting.

By default, Harness uses the `--dry-run` flag on the `kubectl apply` command for all these steps. If the **Skip Dry Run** setting is selected, Harness will not use the `--dry-run` flag.

The **Skip Dry Run** setting is different than the Dry Run step. The Dry Run step only performs a dry run. The Dry Run step does not impact whether or not a deployment step performs a dry run.

