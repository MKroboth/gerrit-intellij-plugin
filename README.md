# Gerrit for IntelliJ

[![Version](https://img.shields.io/jetbrains/plugin/v/7272)](https://plugins.jetbrains.com/plugin/7272)
[![Downloads](https://img.shields.io/jetbrains/plugin/d/7272)](https://plugins.jetbrains.com/plugin/7272)

## About this fork

This is a personal fork of
[uwolfer/gerrit-intellij-plugin](https://github.com/uwolfer/gerrit-intellij-plugin),
kept by Maximilian Kroboth on the branch `inline-comment-threads`. It adds to
how review comments are shown and answered, and adds ways to group and act on
changes in the change list, to suit one particular way of reviewing. Everything
after the rule which ends this section is the upstream README and describes the
plugin as a whole.

Many thanks to Urs Wolfer, who started this plugin in 2013 and has looked after
it ever since, and to everyone who has contributed to it. This fork is built
entirely on their work: the connection to Gerrit, the tool window, the change
list, the comment threads in the diff and the editor, and everything else the
changes below rely on are theirs. For the plugin itself, use the upstream
repository and the
[JetBrains Marketplace release](https://plugins.jetbrains.com/plugin/7272).
Problems with the changes below belong to this fork, not to the upstream
project; please don't report them there.

### Changes

#### Comments

* Under each thread, Quote starts a reply quoting the last comment, next to
  Reply, Ack and Done, and Previous and Next take the caret to the comment
  above or below. Quote is in the menu of a comment's gutter icon as well.
* A "+" in the gutter, next to the line under the mouse, starts a comment on
  that line, in a diff and in the editor of a checked-out change. "C" and the
  context menu start one as well.
* The threads of earlier patch sets are shown on the newer one too, at the line
  their text is on now according to Gerrit's diff between the two patch sets.
  Each names the patch set it was written on, and says "line changed" when its
  line was rewritten. A reply goes to the patch set the thread is on.
* While the change has unpublished drafts, a bar above the diff says how many
  and publishes them, those of every patch set, with a message, the votes you
  may give, and the review dialog's notify and submit options. A vote left at
  0 is not sent.
* R, as in Gerrit's web UI, replies to the thread at the caret or the next one
  below; on a thread which ends in your draft it edits that draft. The bare
  letter applies only while the keymap has no shortcut of its own for "Reply to
  Gerrit Comment Thread", and not in a thread.
* A comment holding a ```` ```suggestion ```` block, as Gerrit's web UI writes
  them, or a Gerrit fix suggestion shows an "Apply fix" link. It edits the
  local file as one undoable change and opens the file there, but only when the
  local file is the same as in the comment's patch set; otherwise it says so
  and changes nothing. A comment on the base side of the diff offers no fix.

#### Comment text

* Comments and change messages are rendered as Markdown, as in Gerrit's web
  UI, with [commonmark-java](https://github.com/commonmark/commonmark-java) and
  GitHub-style tables, strikethrough and bare links: in the threads, the
  tooltips of their icons, the preview of a comment being written and the
  change details. Raw HTML is shown as text, and an image as a link to it.
* Issue ids in comments and change messages are links, from the Gerrit
  project's `commentlink` sections and the IDE's Issue Navigation settings.

#### Change list

* "Group by" in the toolbar shows the listed changes in groups that fold: by
  topic, by the stack their commits form (newest first, each change with its
  place in the stack, such as "3/8"), by the issue in the commit message's
  `Issue:` trailer, by hashtag (the first one alphabetically) or by owner.
  While grouped, the list loads further pages, up to 500 changes, so that a
  stack is not split. The choice is kept in the settings.
* A group's row says how many of its changes are approved, how many fail
  verification, how many sit on an outdated patch set of their parent and need
  a rebase, and how many threads are open.
* Right-clicking a stack's row offers Review Stack (the diff of each change in
  turn, from the base of the stack, with a bar above it to move to the previous
  or next change), Check Out Top of Stack, and Submit Stack (after asking, it
  submits the top change, which Gerrit merges together with the changes it
  builds on). Every group row can fold or unfold all groups.
* A vote's tooltip in the list also shows what the voter wrote with it, such as
  a CI server's build result, and a click on the vote opens the first link in
  that message.
* A "Conversations" tab next to the change details lists the threads of the
  selected change that wait for an answer, on every patch set, with their file,
  line, patch set and last comment. "Whole stack" lists those of every change
  in its stack. Double-click or Enter opens the diff at the thread.

#### Known limitations

* Review Stack opens a new diff window for each change.
* The texts the fork adds are in English only; the translations of the upstream
  plugin show them in English.

### Files

Files changed from upstream carry a "Modified" line in their header that says
what changed; new files carry their own copyright line. Both are under the
Apache License 2.0, as the rest of the plugin is. The upstream files changed
are:

* `build.gradle`: the Markdown libraries.
* `GerritSettings`, `GerritSettingsTest`: the setting for grouping changes.
* `GerritUtil`: asks the change list for the current commit, and reads the
  comments and drafts of every patch set, file diffs, change messages, comment
  links and file texts.
* `GerritCommentsDiffExtension`, `DiffComments`, `EditorComments`,
  `CommentThreadPanel`, `CommentThreads`, `CommentGutterIconRenderer`,
  `CommentForm`, `AddCommentAction`, `AddCommentActionBuilder`,
  `AddCommentInDiffAction`, `CommentFormTest`, `CommentThreadsTest`: the
  threads of earlier patch sets, the bars above the diff, Quote, Previous and
  Next, the "+" in the gutter, R, Markdown, issue links and suggested fixes.
* `SafeHtmlTextEditor`: the preview as Markdown.
* `GerritChangeListPanel`, `GerritChangeColumns`, `GerritToolWindow`:
  grouping, the group rows' menu, vote messages and the Conversations tab.
* `GerritChangeDetailsPanel`: change messages as Markdown, with issue links.
* `RepositoryChangesBrowserProvider`: opens the diff of a given file of a
  change, for the Conversations tab.
* `plugin.xml`, `GerritBundle.properties`: the action that answers a thread,
  and the texts of the fork's additions.

Everything else is in new classes, mostly in `ui/diff`. The fork also bundles
[commonmark-java](https://github.com/commonmark/commonmark-java) (BSD 2-Clause
License) and [autolink-java](https://github.com/robinst/autolink-java) (MIT
License), whose licence texts ship inside their jars.

### Building and installing

It is built and installed like the upstream plugin: `./gradlew buildPlugin`,
then "Install Plugin from Disk" with the zip in `build/distributions`. The
plugin has the same id as the one on the JetBrains Marketplace, so an update
from there replaces it.

---

Unofficial [IntelliJ Platform](https://www.jetbrains.com/idea/) plugin for
[Gerrit Code Review](https://www.gerritcodereview.com/): review, fetch and push
changes without leaving your IDE.

## Features

* Review changes in the IDE: vote, reply and comment on files and lines
* See and answer the comments of a checked out change in the editor, on their
  lines as you edit the file
* Diff changes against your local clone, with the IDE's syntax highlighting and
  navigation
* List and query changes, and get notified of new changes waiting for your
  review
* Push to Gerrit from the push dialog: target branch, topic, reviewers and CC
  with account suggestions, and on Gerrit 2.15+ a hashtag, WIP and private
  changes
* Check out, cherry-pick and reset to changes in your local clone
* Submit, abandon, star changes and add reviewers; publish and delete draft
  changes (Gerrit older than 2.15)
* Run the IDE's inspections on the files a change touches
* Clone Gerrit projects from the IDE, with the commit-msg hook installed
* Open files and commits in Gitiles, and the Gerrit changes of commits selected
  in the VCS log
* Several Gerrit accounts, one per project

## Requirements

* Gerrit 2.9 or newer (older versions lack parts of the REST API)
* Any IDE based on the IntelliJ Platform 2020.3 or newer:
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

## Installation

* From the IDE (recommended, you get notified of updates):
  <kbd>Settings</kbd> > <kbd>Plugins</kbd> > <kbd>Marketplace</kbd>, search for
  "Gerrit" and <kbd>Install</kbd>.
* Manually: download the [release](https://github.com/uwolfer/gerrit-intellij-plugin/releases)
  matching your IDE and install it with <kbd>Settings</kbd> > <kbd>Plugins</kbd> >
  <kbd>⚙</kbd> > <kbd>Install Plugin from Disk...</kbd>.

Then restart the IDE.

## Getting started

1. Open <kbd>Settings</kbd> > <kbd>Version Control</kbd> > <kbd>Gerrit</kbd> and
   add an account with the web URL of your Gerrit, your login and your HTTP
   password (in the Gerrit web UI under <kbd>Settings</kbd> >
   <kbd>HTTP Credentials</kbd>, or <kbd>HTTP Password</kbd> in older versions). <kbd>Test</kbd> checks the connection.
2. Open the <kbd>Gerrit</kbd> tool window. It lists the changes of the Gerrit
   projects whose Git repositories are part of your IDE project.
3. Push as usual: the push dialog has a "Push to Gerrit" section for the
   Gerrit options. "Push commits to Gerrit by default" in the settings ticks it
   in new push dialogs; after that, each project keeps your last choice.

With accounts on several Gerrit instances, add all of them and pick the one a
project talks to with <kbd>Use for This Project</kbd>. A project whose remotes
point at only one of them picks it by itself.

To clone a project from Gerrit, use <kbd>Get from VCS</kbd> and pick
<kbd>Gerrit</kbd> as version control.

## Troubleshooting

### List of changes is empty

By default, the list only shows changes of the Git repositories in your IDE
project.

* Check that the repositories are registered under <kbd>Settings</kbd> >
  <kbd>Version Control</kbd>.
* Check that at least one remote of each repository points at the host of your
  Gerrit account.
* If Gerrit is cloned from another host than its web UI, set the "Clone base
  URL" of the account.
* Or add a remote named like the Gerrit project, with the Gerrit web URL as its
  URL.

### Error-message when clicking a change: "No repository found for Gerrit project"

With "List all Gerrit changes (instead of changes from the currently open
project only)", the list also shows changes of projects that are not part of
your IDE project. You can vote on and submit these, but showing the files and
diff of a change, checking it out and cherry-picking it fetch the change into
the local clone of its project. Clone the project and add it to your IDE
project to see its diff. Reviewing without a local clone is tracked in
[#76](https://github.com/uwolfer/gerrit-intellij-plugin/issues/76).

### Error-message when clicking a change: "Cannot fetch changes"

Gerrit 2.9 and 2.10 tell the plugin where to fetch a change from only with the
`download-commands` plugin installed; newer versions do without it. The Gerrit
update procedure offers to install it, but it isn't selected by default, so run
the update again if you skipped it. For an existing or new instance, this
installs it:

    java -jar gerrit.war init -d {gerrit-instance} --install-plugin=download-commands

### Error-message when loading changes: "Bad Request. Status-Code: 400. Content: too many terms in query."

Enable "List all Gerrit changes (instead of changes from the currently open
project only)" in the plugin settings.

### Error-message when loading changes: "SSLException: Received fatal alert: bad_record_mac"

Your Gerrit (or the reverse proxy in front of it) only accepts SSLv3. Enable
TLS there; SSLv3 is insecure anyway.

### Checking out from VCS with Gerrit plugin does not work

Cloning from the plugin does not work with every authentication method. If it
fails to authenticate or does not finish:

* use the SSH clone URL in the clone dialog (the Gerrit web UI shows it in the
  project settings), or
* clone with the Git plugin and add the Gerrit account afterwards.

Background is in this
[Gerrit mailing list topic](https://groups.google.com/forum/#!topic/repo-discuss/UnQd3HsL820).

### Loading file-diff-list is slow

The file list and diffs come from Git: the plugin fetches the change from
Gerrit. Running [`git gc`](https://git-scm.com/docs/git-gc) locally and
[`gerrit gc`](https://gerrit-review.googlesource.com/Documentation/cmd-gc.html)
on the server (ask your Gerrit administrator) usually helps.

### Authenticate against *-review.googlesource.com

In the Gerrit web UI, open <kbd>Settings</kbd> > <kbd>HTTP Credentials</kbd> >
<kbd>Obtain password</kbd>. In the text shown, find the line starting with your
host (e.g. `gerrit-review.googlesource.com`) and take login and password from
it:

    gerrit-review.googlesource.com,FALSE,/,TRUE,12345678,o,git-username.gmail.com=password-until-end-of-line

Here the login is `git-username.gmail.com` and the password is everything after
the `=`.

## How it works

* **IDE integration:** a [tool window](https://plugins.jetbrains.com/docs/intellij/tool-windows.html)
  lists the changes, see `com.urswolfer.intellij.plugin.gerrit.ui`. The push
  dialog has no extension point, so the plugin adds its options to Git's push
  dialog at startup, see `com.urswolfer.intellij.plugin.gerrit.push`.
* **REST API:** most of the communication with Gerrit uses the
  [Gerrit REST API](https://gerrit-review.googlesource.com/Documentation/rest-api.html),
  through the standalone [gerrit-rest-java-client](https://github.com/uwolfer/gerrit-rest-java-client),
  see `com.urswolfer.intellij.plugin.gerrit.rest`.
* **Git:** listing the files of a change, diffs, checkout and cherry-pick run on
  the local clone with the IDE's [Git plugin](https://github.com/JetBrains/intellij-community/tree/master/plugins/git4idea),
  see `com.urswolfer.intellij.plugin.gerrit.git`.

## Development

Open the project in IntelliJ IDEA with the Gradle, Plugin DevKit and UI
Designer plugins enabled. [`CONTRIBUTING.md`](./CONTRIBUTING.md#building-and-running)
explains how to build, test and run it in a sandbox IDE. Pull requests go to the
default branch `intellij2020.3`, which targets the oldest supported IDE, so a
change reaches every newer one.

## Support the project

If you like this plugin, you can support it:

* Spread it: tell your friends who use IntelliJ and Gerrit about it.
* Rate it on the [JetBrains Marketplace](https://plugins.jetbrains.com/plugin/7272-gerrit).
* [Star it on GitHub](https://github.com/uwolfer/gerrit-intellij-plugin).
* Improve it: report bugs and request features, or fix and build them
  yourself, everything is open source.
* Donate, see below.

### Donations

You can support this work with
[this donation link](https://www.paypal.com/webscr?cmd=_s-xclick&hosted_button_id=8F2GZVBCVEDUQ).
If you would rather not use PayPal (it takes 2.9% plus $0.30 of each donation),
please contact me. Only trust the link on
github.com/uwolfer/gerrit-intellij-plugin.

## Credits

* Parts of this plugin are based on the code of the IntelliJ GitHub plugin.

Thanks to [JetBrains](https://www.jetbrains.com/) for providing a free license
for developing this project.

## Copyright and license

Copyright 2013 - 2026 Urs Wolfer

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this work except in compliance with the License.
You may obtain a copy of the License in the LICENSE file, or at:

  [https://www.apache.org/licenses/LICENSE-2.0](https://www.apache.org/licenses/LICENSE-2.0)

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
