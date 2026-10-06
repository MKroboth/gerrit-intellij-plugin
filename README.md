gerrit-intellij-plugin
======================

[![Version](http://phpstorm.espend.de/badge/7272/version)](https://plugins.jetbrains.com/plugin/7272)
[![Downloads](http://phpstorm.espend.de/badge/7272/downloads)](https://plugins.jetbrains.com/plugin/7272)

About this fork
---------------

This is a personal fork of [uwolfer/gerrit-intellij-plugin](https://github.com/uwolfer/gerrit-intellij-plugin), kept
by Maximilian Kroboth on the branch `inline-comment-threads`. It changes how review comments are shown and answered in
the diff, and adds ways to group and act on changes in the change list, to suit one particular way of reviewing.
Everything from "Introduction" on is the upstream README and describes the plugin as a whole.

Many thanks to Urs Wolfer, who started this plugin in 2013 and has looked after it ever since, and to everyone who has
contributed to it. This fork is built entirely on their work: the connection to Gerrit, the tool window, the change
list, the diff integration and everything else the changes below rely on are theirs. For the plugin itself, use the
upstream repository and the [JetBrains Marketplace release](https://plugins.jetbrains.com/plugin/7272). Problems with
the changes below belong to this fork, not to the upstream project; please don't report them there.

### Changes

#### Comments in the diff

* Comment threads are shown in the diff itself, as a card below the line they are on, with every comment and reply of
  the thread, the way the GitLab and GitHub review tools in the IDE show them. A resolved thread collapses to one line
  unless it holds a draft. This is on by default; Settings | Version Control | Gerrit | Diff | "Show comments in
  place, below the line they are on" switches back to the upstream gutter icons and popups, which are unchanged.
* Each thread ends in a row of buttons: Previous and Next scroll to the neighbouring thread, Quote starts a reply
  quoting the last comment, Ack and Done answer and resolve the thread, and Reply opens an editor inside the thread.
  Your drafts can be edited and deleted in place.
* A "+" in the gutter, next to the line under the mouse, starts a comment on that line. "C" and the context menu start
  one as well.
* The threads of earlier patch sets are shown on the newer one too, at the line their text is on now according to
  Gerrit's diff between the two patch sets. Each names the patch set it was written on, and says "line changed" when
  its line was rewritten. A reply goes to the patch set the thread is on.
* While the change has unpublished drafts, a bar above the diff says how many and publishes them, those of every patch
  set, with a message, the votes you may give, and the review dialog's notify and submit options. A vote left at 0 is
  not sent.
* Text typed into a reply, an edit or a new comment and not saved yet is kept, per change and thread, until the IDE
  closes; the editor opens again with it when the diff shows that file again.
* Keys as in Gerrit's web UI: N and P go to the next and previous thread, R replies to the thread at the caret, and C
  starts a comment. The bare letters apply only while the keymap has no shortcut of its own for "Next Comment
  Thread", "Previous Comment Thread", "Reply to Comment Thread" or "Add Comment", and not while typing in a comment.
* A comment holding a ```` ```suggestion ```` block, as Gerrit's web UI writes them, or a Gerrit fix suggestion shows
  an "Apply fix" link. It edits the local file as one undoable change and opens the file there, but only when the
  local file is the same as in the comment's patch set; otherwise it says so and changes nothing. A comment on the base
  side of the diff offers no fix.

#### Comment text

* Comments and change messages are rendered as Markdown, as in Gerrit's web UI, with
  [commonmark-java](https://github.com/commonmark/commonmark-java) and GitHub-style tables, strikethrough and bare
  links. Raw HTML is shown as text, and an image as a link to it.
* Each comment shows its author's initials on a coloured circle. Gerrit only sends avatar images when an avatar plugin
  is installed, and the fork does not fetch them from anywhere else.
* Issue ids in comments and change messages are links, from the Gerrit project's `commentlink` sections and the IDE's
  Issue Navigation settings.

#### Change list

* "Group by" in the toolbar shows the listed changes in groups that fold: by topic, by the stack their commits form
  (newest first, each change with its place in the stack, such as "3/8"), by the issue in the commit message's
  `Issue:` trailer, by hashtag (the first one alphabetically) or by owner. While grouped, the list loads further pages,
  up to 500 changes, so that a stack is not split. The choice is kept in the settings.
* A group's row says how many of its changes are approved, how many fail verification, how many sit on an outdated
  patch set of their parent and need a rebase, and how many threads are open.
* Right-clicking a stack's row offers Review Stack (the diff of each change in turn, from the base of the stack, with
  a bar above it to move to the previous or next change), Check Out Top of Stack, and Submit Stack (after asking, it
  submits the top change, which Gerrit merges together with the changes it builds on). Every group row can fold or
  unfold all groups.
* A vote's tooltip in the list also shows what the voter wrote with it, such as a CI server's build result, and a click
  on the vote opens the first link in that message.
* A "Conversations" tab next to the change details lists the threads of the selected change that wait for an answer,
  on every patch set, with their file, line, patch set and last comment. "Whole stack" lists those of every change in
  its stack. Double-click or Enter opens the diff at the thread.

#### Known limitations

* In the side-by-side diff, the two sides can drift out of line around a thread's card.
* Review Stack opens a new diff window for each change.
* The changes are tested in IntelliJ IDEA 2020.3.4 against Gerrit 3.14. The comment threads in the diff were also
  tried in IntelliJ IDEA 2026.2.3, and the plugin verifier reports the build as compatible with it.

### Files

Files changed from upstream carry a "Modified" line in their header that says what changed; new files carry their own
copyright line. Both are under the Apache License 2.0, as the rest of the plugin is. The upstream files changed are:

* `build.gradle`: the Markdown libraries.
* `GerritSettings`, `GerritSettingsConfigurable`, `SettingsPanel` (and its form), `GerritSettingsTest`: the settings
  for comments in place and for grouping changes.
* `GerritUtil`: reports a failed draft save or delete to the caller, and reads the comments of every patch set, file
  diffs, drafts, change messages, comment links and file contents.
* `CommentsDiffTool`, `AddCommentInDiffAction`: the threads in place unless the settings turn them off, and the bars
  above the diff.
* `GerritChangeListPanel`, `GerritToolWindow`: grouping, the group rows' menu, vote messages and the Conversations tab.
* `GerritChangeDetailsPanel`: change messages as Markdown, with issue links.
* `RepositoryChangesBrowserProvider`: opens the diff of a given file of a change, for the Conversations tab.
* `plugin.xml`: the actions that walk and answer threads.

Everything else is in new classes, mostly in `ui/diff`. The fork also bundles
[commonmark-java](https://github.com/commonmark/commonmark-java) (BSD 2-Clause License) and
[autolink-java](https://github.com/robinst/autolink-java) (MIT License), whose licence texts ship inside their jars.

### Building and installing

It is built and installed like the upstream plugin: `./gradlew buildPlugin`, then "Install Plugin from Disk" with the
zip in `build/distributions`. The plugin has the same id as the one on the JetBrains Marketplace, so an update from
there replaces it.

Introduction
-----------

Unofficial [IntelliJ Platform](https://www.jetbrains.com/idea/) plugin for the
[Gerrit Code Review](https://www.gerritcodereview.com/) tool. It supports any product based on the IntelliJ platform:
* IntelliJ IDEA
* IntelliJ IDEA CE
* RubyMine
* WebStorm
* PhpStorm
* PyCharm
* PyCharm CE
* AppCode
* Android Studio
* DataGrip
* CLion
* GoLand
* Rider
* MPS

*Compiled with Java 11*

Only Gerrit 2.8 or newer is supported (missing / incomplete REST API in older versions).

Installation
------------
- Using IDE built-in plugin system (suggested: you'll get notified when an update is available):
  - <kbd>Settings...</kbd> > <kbd>Plugins</kbd> > <kbd>Marketplace</kbd> >
  <kbd>Search for "Gerrit"</kbd> > <kbd>Install</kbd>
- Manually:
  - Download the [release](https://github.com/uwolfer/gerrit-intellij-plugin/releases)
  matching your IntelliJ version and install it manually using
  <kbd>Settings...</kbd> > <kbd>Plugins</kbd> > <kbd>Gear icon</kbd> > <kbd>Install Plugin from Disk</kbd>

Restart your IDE.

Your Support
------------
If you like this plugin, you can support it:
* Spread it: Tell your friends who are using IntelliJ and Gerrit about this plugin (or even encourage them to use these fantastic products!)
* Vote for it: Write your review and vote for it at the [IntelliJ plugin repository](https://plugins.jetbrains.com/plugin/7272-gerrit).
* Star it: [Star it at GitHub](https://github.com/uwolfer/gerrit-intellij-plugin). GitHub account required.
* Improve it: Report bugs or feature requests. Or even fix / implement them by yourself - everything is open source!
* Donate: You can find donation-possibilities at the bottom of this file.

Troubleshooting
---------------
### List of changes is empty
By default, you will only see changes to Git repositories that are configured in the current project of your IntelliJ IDE.
* Make sure that Git repositories are configured in the 'Version Control' settings.
* Make sure that the Git repository remote url (at least one of them) is on the same host as configured in Gerrit plugin settings. Or:
* Set the 'Clone Base URL' if it differs from the Gerrit web url. Or:
* Add a remote whose name equals the Gerrit project name with Gerrit web url as remote url.

### Error-message when clicking a change: "No repository found for Gerrit project"
The list can contain changes of Gerrit projects which are not part of your IDE project, e.g. with the option
"List all Gerrit changes (instead of changes from currently open project only)". Voting and submitting work for these
changes, but showing a change's files and diff, checking it out and cherry-picking it need a local clone: the plugin
fetches the change into the Git repository of its Gerrit project. Clone the project and add it as a Git repository to
your IDE project to see its diff. Reviewing without a local clone is tracked in
[#76](https://github.com/uwolfer/gerrit-intellij-plugin/issues/76).

### Error-message when clicking a change: "Cannot fetch changes"
In Gerrit 2.8, fetch information was pulled out of default functionality into a plugin.
Up to Gerrit 2.10 you need to install the plugin <code>download-commands</code>; newer versions provide the ref of each
patch set without it. When you run the Gerrit update procedure, it asks you to install
this plugin (but it isn't selected by default). Just run the update script again if you have not installed it yet.

When installing Gerrit 2.8 (or newer) from scratch (rather than using the update script) the following command will install the
<code>download-commands</code> plugin (for a new installation or an existing Gerrit instance):

    $ java -jar gerrit.war init -d {gerrit-instance} --install-plugin=download-commands


### Error-message when loading changes: "SSLException: Received fatal alert: bad_record_mac"
There are two workarounds for this issue:
* allow TLSv1 (instead of SSLv3 only) connections in your reverse-proxy in front of Gerrit. SSLv3 is considered insecure, therefore TLS should be the default in any case.
* use a recent Java setup (> 1.6)

### Error-message when loading changes: "Bad Request. Status-Code: 400. Content: too many terms in query."
Open plugin settings and enable the option "List all Gerrit changes (instead of changes from currently open project only)".

### Checking out from VCS with Gerrit plugin does not work
Checking out directly with the Gerrit plugin does not work for some authentication methods. If you get an authentication
error or checking out does not properly finish, you can try to:
* use SSH clone URL in checkout dialog (you can find the SSH URL in the Gerrit Web UI project settings)
* or: check out with the default Git plugin and set up the Gerrit plugin manually afterwards

You can find background information about this issue in a [Gerrit mailing list topic](https://groups.google.com/forum/#!topic/repo-discuss/UnQd3HsL820).

### Loading file-diff-list is slow
Diff viewing is based on Git operations (i.e. it fetches the commit from the Gerrit remote). When loading the file list
takes a lot of time, you can run a local "[git gc](https://www.kernel.org/pub/software/scm/git/docs/git-gc.html)"
and ask your Gerrit administrator to do run a "[gerrit gc](https://gerrit-review.googlesource.com/Documentation/cmd-gc.html)".

### Authenticate against *-review.googlesource.com
It's a bit of manual work to do:
<kbd>Settings</kbd> -> <kbd>HTTP Credentials</kbd> -> <kbd>Obtain password</kbd>

Then search for the line in the text area starting with `*-review.googlesource.com` (e.g. `gerrit-review.googlesource.com`) and extract username and password:

gerrit-review.googlesource.com,FALSE,/,TRUE,12345678,o,**git-username.gmail.com**=**password-until-end-of-line**

Architecture
------------
### IntelliJ Integration
The plugin is integrated into the IntelliJ IDE with a [tool window](https://plugins.jetbrains.com/docs/intellij/tool-windows.html?from=jetbrains.org).
See package <code>com.urswolfer.intellij.plugin.gerrit.ui</code>.

### REST API
Most of the communication between the plugin and a Gerrit instance is based on the [Gerrit REST API](https://gerrit-review.googlesource.com/Documentation/rest-api.html).
The REST specific part is available as [standalone implementation](https://github.com/uwolfer/gerrit-rest-java-client).
See package <code>com.urswolfer.intellij.plugin.gerrit.rest</code>.

### Git
Some actions like comparing and listing files are based on Git operations.
[IntelliJ Git4Idea](https://github.com/JetBrains/intellij-community/tree/master/plugins/git4idea) is used for these operations.
See package <code>com.urswolfer.intellij.plugin.gerrit.git</code>.


Build (and develop!) the Plugin
------------------

It's very easy to set it up as an IntelliJ project.

1. Activate plugins ```Gradle```, ```Plugin DevKit``` and ```UI Designer``` in IntelliJ.
2. ```git clone https://github.com/uwolfer/gerrit-intellij-plugin``` (probably switch to ```intellij{version}``` branch, but keep in mind that pull-requests should be against the default branch ("intellij13" and older are not supported anymore))
3. Open checked out project in IntelliJ ("File" -> "New" -> "Project from Existing Sources" -> select file ```build.gradle``` in ```gerrit-intellij-plugin``` folder and press "OK")
4. Create a new run configuration: "Gradle" -> "Gradle project": select the only project -> "Tasks": "runIde"
5. Press "Debug" button. IntelliJ should start with a clean workspace (development sandbox). You need to checkout a
   project to see changes (it shows only changes for Git repositories that are set up in current workspace by default).

Once ```build.gradle``` gets updated, you need to "Refresh all Gradle projects" in the Gradle panel.


Contributing
------------
Check the [`CONTRIBUTING.md`](./CONTRIBUTING.md) file.


Credits
------
* IntelliJ Github plugin (some code of this plugin is based on its code)

Thanks to [JetBrains](https://www.jetbrains.com/) for providing a free licence for developing this project.

Donations
--------
If you like this work, you can support it with
[this donation link](https://www.paypal.com/webscr?cmd=_s-xclick&hosted_button_id=8F2GZVBCVEDUQ).
If you don't like Paypal (Paypal takes 2.9% plus $0.30 per transaction fee from your donation), please contact me.
Please only use the link from github.com/uwolfer/gerrit-intellij-plugin to verify that it is correct.


Copyright and license
--------------------

Copyright 2013 - 2018 Urs Wolfer

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this work except in compliance with the License.
You may obtain a copy of the License in the LICENSE file, or at:

  [https://www.apache.org/licenses/LICENSE-2.0](https://www.apache.org/licenses/LICENSE-2.0)

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
