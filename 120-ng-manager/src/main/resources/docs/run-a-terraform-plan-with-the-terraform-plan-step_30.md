# Option: Terraform Plan detailed-exitcode

You can use the standard `terraform plan` command option [detailed-exitcode](https://www.terraform.io/cli/commands/plan#other-options) with the Harness Terraform Plan step.

If you use the `-detailed-exitcode` option in a step that follows the Harness Terraform Plan step, such as a [Shell Script](../../cd-execution/cd-general-steps/using-shell-scripts.md) step, Harness will return a detailed exit code:

* `0`: succeeded with empty diff (no changes)
* `1`: error
* `2`: succeeded with non-empty diff (changes present)
