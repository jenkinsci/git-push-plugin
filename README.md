# git-push

[![Build Status](https://ci.jenkins.io/job/Plugins/job/git-push-plugin/job/master/badge/icon)](https://ci.jenkins.io/job/Plugins/job/git-push-plugin/job/master/)
[![Contributors](https://img.shields.io/github/contributors/jenkinsci/git-push-plugin.svg)](https://github.com/jenkinsci/git-push-plugin/graphs/contributors)
[![Jenkins Plugin](https://img.shields.io/jenkins/plugin/v/git-push.svg)](https://plugins.jenkins.io/git-push)
[![GitHub release](https://img.shields.io/github/release/jenkinsci/git-push-plugin.svg?label=changelog)](https://github.com/jenkinsci/git-push-plugin/releases/latest)
[![Jenkins Plugin Installs](https://img.shields.io/jenkins/plugin/i/git-push.svg?color=blue)](https://plugins.jenkins.io/git-push)

## Introduction

This plugin allows to perform a git push as a post build step via [Git Plugin](https://plugins.jenkins.io/git)

In details, the plugin will pull then push (tags included) to the selected remote branch.

## Getting started

The instructions below are based on a Jenkins job created as a Freestyle project.

Open the Jenkins job configuration.

Make sure the job uses git `Source Code Management`.
For example, a basic git configuration could look like this:

![alt text](doc/git-plugin-configuration.png "Git configuration")


Add `Git Push` as a post build action:

![alt text](doc/add-post-build-action.png "Add the post build action")

Configure the action:

![alt text](doc/configure-action.png "Configure the action")

## Issues

Report issues and enhancements in the [Issue tracker](https://github.com/jenkinsci/git-push-plugin/issues).

## Contributing

Refer to our [contribution guidelines](https://github.com/jenkinsci/.github/blob/master/CONTRIBUTING.md)

## LICENSE

Licensed under MIT, see [LICENSE](LICENSE.md)

