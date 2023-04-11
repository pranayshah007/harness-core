## Freeze windows only apply to CD stages

Deployment Freeze is a CD feature only. It does not apply to other module stages like CI and Feature Flags.

If a pipeline includes a CD stage and other module stages, like CI and Feature Flags, the freeze window is applied to the CD stage(s) **only**. The other stages in the pipeline will continue to run.
