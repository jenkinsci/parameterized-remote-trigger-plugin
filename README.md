Parameterized-Remote-Trigger-Plugin
===================================

A plugin for Jenkins CI  that gives you the ability to trigger parameterized builds on a **remote** Jenkins server as part of your build.

Similar to the [Parameterized Trigger Plugin](https://wiki.jenkins-ci.org/display/JENKINS/Parameterized+Trigger+Plugin), but for remote servers.

This is done by calling the ```/buildWithParameters``` URL on the remote server. (or the ```/build``` URL, if you don't specify any parameters)

This plugin also has support for build authorization tokens (as defined [here](https://wiki.jenkins-ci.org/display/JENKINS/Quick+and+Simple+Security) ), and plays nicely with these other guys:
- [Build Token Root Plugin](https://wiki.jenkins-ci.org/display/JENKINS/Build+Token+Root+Plugin)
- [Credentials Plugin](https://wiki.jenkins-ci.org/display/JENKINS/Credentials+Plugin)
- [Token Macro Plugin](https://wiki.jenkins-ci.org/display/JENKINS/Token+Macro+Plugin)

Please take a look at the [change log](CHANGELOG.md) for a complete list of features and what not.

## Usage
1. [System configuration options](README_SystemConfiguration.md)<br>
2. [Job setup options](README_JobConfiguration.md)<br>
3. [Pipeline setup options](README_PipelineConfiguration.md)
