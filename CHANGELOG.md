# 3.2.1 (Jan 13th, 2025)

### Improvement

* Upgrade toolset.
* Update class dependency.
* Some document refinements.

# 3.2.0 (Sep 5th, 2023)

### Improvement

* Support opentelemeter.
* Upgrade to Jenkins 2.346.3 baseline.

# 3.1.6.4 (Sep 5th, 2023)

### Bug fixes

* When enhancedLogging is set and remote log is trimmed. It adds a last poll for logs after the remote build finished.

# 3.1.6.3 (July 22th, 2022)

### Bug fixes

* Fix pipeline generation error
* Fix pipeline unable to parse

# 3.1.6.2 (July 18th, 2022)

### Improvement

* Mark breaking changes for 3.1.6.x

# 3.1.6.1 (Jun 14th, 2022)

### Improvement

* Complete the PR [f1eb49cf](https://github.com/jenkinsci/parameterized-remote-trigger-plugin/commit/f1eb49cf6294b77dae9361a4176d60d8591f40b7) in 3.1.6. . ([5033a88](https://github.com/jenkinsci/parameterized-remote-trigger-plugin/commit/5033a881308f18d416c340c969b36ec2902bb6d8))

#### Breaking changes for the parameters (multiple lines, read from files)

# 3.1.6 (Jun 12th, 2022)

### Improvement

* Improve parameters handling. ([f1eb49cf](https://github.com/jenkinsci/parameterized-remote-trigger-plugin/commit/f1eb49cf6294b77dae9361a4176d60d8591f40b7))
* Allow use `triggerRemoteJob` step with `agent none` ([fef4451](https://github.com/jenkinsci/parameterized-remote-trigger-plugin/commit/fef4451ad0c743d47f3282e75dfb4c9d1ffbb3c3))

#### Breaking changes for the parameters (multiple lines, read from files)

# 3.1.5.1 (Oct 6th, 2020)

### Improvement

* The plain text tokens are not allowed to set from pipeline directly since 3.1.4, mark builds after 3.1.4 for breaking changes. Remove old pom settings.

# 3.1.5 (Oct 5th, 2020)

### Improvement

* [JENKINS-63819] Add maxConn default value & value checking
* The plain text tokens are not allowed to set from pipeline directly since 3.1.4, mark builds after 3.1.4 for breaking changes.

# 3.1.4 (Aug 20th, 2020)

### Improvement

* Update git repo in pom ([2b35305](https://github.com/jenkinsci/parameterized-remote-trigger-plugin/pull/72))
* Retrieve only needed "doGetting" build fields. ([33065c3](https://github.com/jenkinsci/parameterized-remote-trigger-plugin/pull/74))
* Fix https://issues.jenkins-ci.org/browse/SECURITY-1625

# 3.1.3 (May 10th, 2020)

### Improvement

* Support case insensitive HTTP header  ([a7e69eb](https://github.com/jenkinsci/parameterized-remote-trigger-plugin/pull/68))
* Token-macro shouldn't work on Jenkins credential auth. ([bf25943](https://github.com/jenkinsci/parameterized-remote-trigger-plugin/pull/71))
* Check null json object by isNullObject instead of null checking. ([f414c32](https://github.com/jenkinsci/parameterized-remote-trigger-plugin/pull/70))


# 3.1.2 (Apr 13th, 2020)
### New feature

* Support customization of HTTP read timeout for GET & POST ([b9b566a](https://github.com/jenkinsci/parameterized-remote-trigger-plugin/commit/b3841c54571dedee67c6a4a08e1cefcebd5c8760))

### Improvement

* Add cookie handler (Jenkins 2.176.2) ([aeac218](https://github.com/jenkinsci/parameterized-remote-trigger-plugin/commit/aeac218e2360c79107456b13e2c9a74ccc44ebcd))
* Documentation: Add handler.updateBuildStatus() to non-blocking example ([8268b12](https://github.com/jenkinsci/parameterized-remote-trigger-plugin/commit/8268b1276d1368f5eafb7d5a611588a9100e2eb8))

### Bug fixes

* Keep remote Jenkins URL scheme ([38e2699](https://github.com/jenkinsci/parameterized-remote-trigger-plugin/commit/38e2699e0e725b6ff0d176cf25d8203811c8260d))
* Set the uplimit when polling queue item information. ([ec05bba](https://github.com/jenkinsci/parameterized-remote-trigger-plugin/commit/ec05bbad0b47135c3f88ad051da34766bea9ff33))

# 3.1.1 (Jan 4th, 2020)
### New feature

* Support barrier token authentication ([2b067fe](https://github.com/jenkinsci/parameterized-remote-trigger-plugin/commit/2b067fe963d4d68cef496062b5191e4017b715fd))

### Improvement

* Enhance exception handling of json output ([4642fc6](https://github.com/jenkinsci/parameterized-remote-trigger-plugin/commit/4642fc6d1ee6a09de552a83ae598d0bdfecf6d41))
* Remove semaphore from serialization ([83a8c0a](https://github.com/jenkinsci/parameterized-remote-trigger-plugin/commit/83a8c0a386838718b5af72c4ff67c8c0efadab3d))

### Bug fixes

* None


# 3.1.0 (Oct 3rd, 2019)
### New feature

* None

### Improvement

* Support "view" in job's URL ([f0b36fa](https://github.com/jenkinsci/parameterized-remote-trigger-plugin/commit/f0b36fa13900da889ebe846d6daeb978e6d7d5f6))

### Bug fixes

* [JENKINS-59456](https://issues.jenkins-ci.org/browse/JENKINS-59456)


# 3.0.9 (Aug 17th, 2019)
### New feature

* None

### Improvement

* Stream output rather than dump it all out at the end ([a625e4b](https://github.com/jenkinsci/parameterized-remote-trigger-plugin/commit/a625e4b7df875a11a2e8310c2aee781e61626f06))
* Added the ability to trust untrusted certificates ([08ec3ec](https://github.com/jenkinsci/parameterized-remote-trigger-plugin/commit/08ec3ec779839716fd080f93de03a9432f6c05fe))

### Bug fixes

* None


# 3.0.8 (Mar 27th, 2019)
### New feature

* None

### Improvement

* Java doc refinement: Handle.getBuildStatus, Handle.updateBuildStatus ([541365a](https://github.com/jenkinsci/parameterized-remote-trigger-plugin/commit/541365a0740f1e5b17f2615076249c4da33c34bc))
* Extend POST timeout & avoid re-POST after timeout ([97de437](https://github.com/jenkinsci/parameterized-remote-trigger-plugin/commit/97de437b98bec1cd9d46b78047886809c1e110d2))
* Handle proxy host to avoid fail in subsequent requests ([285d657](https://github.com/jenkinsci/parameterized-remote-trigger-plugin/commit/285d6573107789f3480d5a7fbc726d94a93cb917))

### Bug fixes

* None


# 3.0.7 (Dec 2nd, 2018)
### New feature

* None

### Improvement

* None

### Bug fixes

* [JENKINS-55038](https://issues.jenkins-ci.org/browse/JENKINS-55038)


# 3.0.6 (Sep 18th, 2018)
### New feature

* Disable remote trigger job step instead of removing it

### Improvement

* None

### Bug fixes

* [JENKINS-52810](https://issues.jenkins-ci.org/browse/JENKINS-52810)


# 3.0.5 (Aug 20th, 2018)
### New feature

* None

### Improvement

* None

### Bug fixes

* [PR #46](https://github.com/jenkinsci/parameterized-remote-trigger-plugin/pull/46)
* [JENKINS-53125](https://issues.jenkins-ci.org/browse/JENKINS-53125)


# 3.0.4 (Jul 30th, 2018)
### New feature

* Support to abort remote job

### Improvement

* None

### Bug fixes

* [PR #45](https://github.com/jenkinsci/parameterized-remote-trigger-plugin/pull/45)


# 3.0.3 (Jul 23th, 2018)
### New feature

* None

### Improvement

* Add concurrent connection restriction to prevent remote servers from blocking 
* Add job info. & crumb cache to reduce the dummy inquiries when parallel triggering

### Bug fixes

* [JENKINS-52673](https://issues.jenkins-ci.org/browse/JENKINS-52673)

### Important change

* jdk version must be at least v1.8


# 3.0.2 (Jul 18th, 2018)
### New feature

* None

### Improvement

* HTTP utility reorganized
   * post with form-data

### Bug fixes

* Fix parameters are too long (HTTP status 414)


# 3.0.1 (Jul 9th, 2018)
### New feature
* Support triggering remote jobs via Jenkins proxy 

### Improvement
- code refinement

### Bug fixes
- [JENKINS-47919 ](https://issues.jenkins-ci.org/browse/JENKINS-47919) (clarified & fixed)


# 3.0.0 (May 17th, 2018)
### New feature
* Pipeline support

### Improvement
- [JENKINS-24240](https://issues.jenkins-ci.org/browse/JENKINS-24240)
- [JENKINS-29219](https://issues.jenkins-ci.org/browse/JENKINS-29219)
- [JENKINS-29220](https://issues.jenkins-ci.org/browse/JENKINS-29220)
- [JENKINS-29222](https://issues.jenkins-ci.org/browse/JENKINS-29222)

### Bug fixes
- [JENKINS-29381](https://issues.jenkins-ci.org/browse/JENKINS-29381)
- [JENKINS-30962](https://issues.jenkins-ci.org/browse/JENKINS-30962)
- [JENKINS-32462](https://issues.jenkins-ci.org/browse/JENKINS-32462)
- [JENKINS-32671](https://issues.jenkins-ci.org/browse/JENKINS-32671)
- [JENKINS-33269](https://issues.jenkins-ci.org/browse/JENKINS-33269)
- [JENKINS-47919 ](https://issues.jenkins-ci.org/browse/JENKINS-47919)


# 2.2.2 (Aug 16th, 2015)
### Misc:
- require Jenkins 1.580+

### Bug fixes:
- 2.2.0 didn't make it to the update center


# 2.2.0 (May 12th, 2015)
### New Feature/Enhancement:
- Ability to debug connection errors with (optional) enhanced console output ([pull request](https://github.com/jenkinsci/parameterized-remote-trigger-plugin/pull/9)) 

### Bug fixes:
- fixing [JENKINS-23748](https://issues.jenkins-ci.org/browse/JENKINS-23748) - Better error handleing for console output and logs to display info about the failure ([pull request](https://github.com/jenkinsci/parameterized-remote-trigger-plugin/pull/10))
- Don't fail build on 404 ([pull request](https://github.com/jenkinsci/parameterized-remote-trigger-plugin/pull/8))
- Fixed unhandled NPE ([pull request](https://github.com/jenkinsci/parameterized-remote-trigger-plugin/pull/7))
- Hand-full of other bugs ([pull request](https://github.com/jenkinsci/parameterized-remote-trigger-plugin/pull/5/commits)):
	* fixing [JENKINS-26127](https://issues.jenkins-ci.org/browse/JENKINS-26127)
	* fixing [JENKINS-24872](https://issues.jenkins-ci.org/browse/JENKINS-24872)
	* fixing [JENKINS-25366](https://issues.jenkins-ci.org/browse/JENKINS-25366)


# 2.1.3 (July 6th, 2014)
### Bug fixes:
- merging [pull request #4](https://github.com/jenkinsci/parameterized-remote-trigger-plugin/pull/4)


# 2.1.2 (April 26th, 2014)
### Bug fixes:
- fixing [JENKINS-22325](https://issues.jenkins-ci.org/browse/JENKINS-22325) - local job fails when not sending any parameters to remote job
- fixing [JENKINS-21470](https://issues.jenkins-ci.org/browse/JENKINS-21470) - UI does not display that a build is using a file to get the parameter list
- fixing [JENKINS-22493](https://issues.jenkins-ci.org/browse/JENKINS-22493) - 400 when remote job has default parameters and parameters are not explicitly list them
- fixing [JENKINS-22427](https://issues.jenkins-ci.org/browse/JENKINS-22427) - fails when remote job waits for available executor


# 2.1 (Feb 17th, 2014)
### New Feature/Enhancement:
- ability to specify the list of remote parameters from a file ([request ticket](https://issues.jenkins-ci.org/browse/JENKINS-21470))
- optionally block the local build until remote build is complete ([request ticket](https://issues.jenkins-ci.org/browse/JENKINS-20828))

### Misc:
- the console output has also been cleansed of displaying any URLs, since this could pose a security risk for public CI environemnts. 
- special thanks to [@tombrown5](https://github.com/timbrown5) for his contributions to the last item mentioned above


#2.0 ( Dec 25th, 2013)

Lots of refactoring and addition of some major new features.

### New Feature/Enhancement:
- integration with the 'Credentials' plugin ([request ticket](https://issues.jenkins-ci.org/browse/JENKINS-20826))
- able to override global credentials at a job-level ([request ticket](https://issues.jenkins-ci.org/browse/JENKINS-20829))
- support for 'Token Macro' plugin ([request ticket](https://issues.jenkins-ci.org/browse/JENKINS-20827))
- support for traditional Jenkins environment variables ([request ticket](https://issues.jenkins-ci.org/browse/JENKINS-21125))

### Misc:
Special thanks to [@elksson](https://github.com/elksson) for his contributions to the last 2 items mentioned above and to [@imod](https://github.com/imod) for his awesome feedback and feature suggestions.


# 1.1 ( Nov. 30th, 2013)

### Bug fixes:
- closing potential security gap for public-read environments
    
    
### New Feature/Enhancement:
- ability to not mark the build as failed if the remote build fails
    
    
### Misc:
- General code clean-up


# 1.0 
Initial release

### Available features:
- Trigger parameterized build on a remote Jenkins server
- Trigger non-parameterized build on a remote Jenkins server
- Authentication via username + API token
- Support for "build token root" plugin
