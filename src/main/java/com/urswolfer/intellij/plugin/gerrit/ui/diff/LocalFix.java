/*
 * Copyright 2026 Maximilian Kroboth
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.urswolfer.intellij.plugin.gerrit.ui.diff;

import com.google.gerrit.extensions.common.ChangeInfo;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.ReadonlyStatusHandler;
import com.intellij.openapi.vfs.VirtualFile;
import com.urswolfer.intellij.plugin.gerrit.GerritBundle;
import com.urswolfer.intellij.plugin.gerrit.git.GerritGitUtil;
import com.urswolfer.intellij.plugin.gerrit.rest.GerritUtil;
import git4idea.repo.GitRepository;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * A fix a comment suggests, applied to the local copy of the files it touches.
 *
 * Each file has to be as it is in the comment's patch set: elsewhere the fix's ranges may point at other text. Then
 * the fix is one command, undone at once, and the first file opens where it changed.
 */
final class LocalFix {
    private final Project project;
    private final ChangeInfo changeInfo;
    private final String revision;
    private final Map<String, List<Fixes.Replacement>> byPath = new LinkedHashMap<>();
    private final Map<Document, List<Fixes.Replacement>> documents = new LinkedHashMap<>();

    private LocalFix(Project project, ChangeInfo changeInfo, String revision, Fixes.Fix fix) {
        this.project = project;
        this.changeInfo = changeInfo;
        this.revision = revision;
        for (Fixes.Replacement replacement : fix.replacements) {
            byPath.computeIfAbsent(replacement.path, path -> new ArrayList<>()).add(replacement);
        }
    }

    /**
     * @param revision the patch set of the comment which suggests the fix
     */
    static void apply(@NotNull Project project, @NotNull ChangeInfo changeInfo, @NotNull String revision,
                      @NotNull Fixes.Fix fix) {
        Optional<GitRepository> repository = GerritGitUtil.getInstance().getRepositoryForChange(project, changeInfo);
        if (!repository.isPresent()) {
            Messages.showWarningDialog(project, GerritBundle.message("fix.noRepository"), GerritBundle.message("fix.title"));
            return;
        }
        LocalFix localFix = new LocalFix(project, changeInfo, revision, fix);
        localFix.checkAndApply(repository.get(), new ArrayList<>(localFix.byPath.keySet()), 0);
    }

    private void checkAndApply(GitRepository repository, List<String> paths, int index) {
        if (index == paths.size()) {
            List<VirtualFile> files = documents.keySet().stream()
                .map(document -> FileDocumentManager.getInstance().getFile(document))
                .collect(Collectors.toList());
            if (ReadonlyStatusHandler.getInstance(project).ensureFilesWritable(files).hasReadonlyFiles()) return;
            WriteCommandAction.runWriteCommandAction(project, GerritBundle.message("fix.title"), null, () -> {
                for (Map.Entry<Document, List<Fixes.Replacement>> entry : documents.entrySet()) {
                    entry.getKey().setText(Fixes.apply(entry.getKey().getText(), entry.getValue()));
                }
            });
            Fixes.Replacement first = byPath.get(paths.get(0)).get(0);
            VirtualFile file = repository.getRoot().findFileByRelativePath(paths.get(0));
            if (file != null) {
                new OpenFileDescriptor(project, file, Math.max(first.startLine - 1, 0), first.startCharacter)
                    .navigate(true);
            }
            return;
        }
        String path = paths.get(index);
        VirtualFile file = repository.getRoot().findFileByRelativePath(path);
        Document document = file != null ? FileDocumentManager.getInstance().getDocument(file) : null;
        if (document == null) {
            Messages.showWarningDialog(project, GerritBundle.message("fix.notLocal", path),
                GerritBundle.message("fix.title"));
            return;
        }
        GerritUtil.getInstance().getFileTextQuietly(changeInfo._number, revision, path, file.getCharset(), project,
            content -> {
                if (content == null) {
                    Messages.showWarningDialog(project, GerritBundle.message("fix.notLoaded", path),
                        GerritBundle.message("fix.title"));
                    return;
                }
                if (!content.equals(document.getText())) {
                    Messages.showWarningDialog(project, GerritBundle.message("fix.differs", path),
                        GerritBundle.message("fix.title"));
                    return;
                }
                documents.put(document, byPath.get(path));
                checkAndApply(repository, paths, index + 1);
            });
    }
}
