# Job setup options

Select `Build` > `Add build step` > `Trigger a remote parameterized job`

![select from drop-down](screenshots/2-build-configuration-1.png)

You can select a globally configured remote server and only specify a job name here.
The full URL is calculated based on the remote server, the authentication is taken from the global configuration.
However it is possible to override the Jenkins base URL (or set the full Job URL) and override credentials used for authentication.

![Job setup options](screenshots/3-build-configuration-2.png)

You can also specify the full job URL and use only the authentication from the global configuration or specify the authentication per job.

![Job setup options](screenshots/3-build-configuration-2b.png)


# Support of Folders on Remote Jenkins
[See here for more information](README_PipelineConfiguration.md#user-content-folders)
