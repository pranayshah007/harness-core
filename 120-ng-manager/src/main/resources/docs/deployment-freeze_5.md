## What about pipelines already running?

If a pipeline is running and a freeze happens, the pipeline will continue to run until the current stage of the pipeline has executed. Once that stage executes, the freeze is implemented and no further stages will execute.

If the freeze happens to a running pipeline and it is unable to complete all stages, the status of the pipeline execution is listed as **Aborted By Freeze**.
