package com.github.wleese.intellij.kpttree;

import com.intellij.icons.AllIcons;
import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.projectView.ProjectView;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.vfs.*;
import com.intellij.util.Alarm;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.*;

import static com.intellij.util.SlowOperations.allowSlowOperations;

/* Extend AbstractTreeNode
   On initialization triggers a read of all files under ./infra/kpt
   Populate tree via overriding getChildren()
   Which then filters based on what we're interested in
 */
public class KptTreeProjectNode extends AbstractTreeNode<VirtualFile> {

    private static final Key<Set<VirtualFile>> KPT_PROJECT_DIRS = Key.create("kpt.files.or.directories");
    public static final String INFRA_KPT_KPTFILE = "./infra/kpt/Kptfile";

    public KptTreeProjectNode(final Project project) {
        // second argument sets our view to the "kpt" directory in "infra"
        // todo: move some of this work to KptTreeProjectViewPane
        super(project, ProjectUtil.guessProjectDir(project).findFileByRelativePath(INFRA_KPT_KPTFILE).getParent());
        scanAndFilterInfraKpt(project);

        subscribeToVFS(project);
    }

    public KptTreeProjectNode(Project project, VirtualFile file) {
        super(project, file);
    }

    // Creates a collection of files we're interested in, async
    private void scanAndFilterInfraKpt(Project project) {
        Set<VirtualFile> allFiles = getFilesOrSetupUserData(project);
        VirtualFile projectDir = ProjectUtil.guessProjectDir(project);
        if (projectDir == null) {
            return;
        }

        VirtualFile projectKptFile = projectDir.findFileByRelativePath(INFRA_KPT_KPTFILE);
        if (projectKptFile == null) {
            return;
        }

        try {
            ThrowableComputable<Collection<VirtualFile>, RuntimeException> collectionRuntimeExceptionThrowableComputable = () -> allowSlowOperations(() -> VfsUtil.collectChildrenRecursively(projectKptFile.getParent()));
            Collection<VirtualFile> files = ReadAction.compute(collectionRuntimeExceptionThrowableComputable);

            for (VirtualFile file : files) {
                while (isRelevant(file) && !file.equals(projectKptFile)) {
                    allFiles.add(file);
                    file = file.getParent();
                }
            }
        } catch (Throwable throwable) {
            throwable.printStackTrace();
        }
    }

    private static boolean isRelevant(VirtualFile file) {
        return file != null && (
                file.isDirectory() && (!file.getName().startsWith(".")) ||
                        file.getName().endsWith(".yml") || file.getName().endsWith(".yaml") ||
                        file.getName().equals("Kptfile")
        );
    }

    // Mark the files we're interested in for performance purposes
    private Set<VirtualFile> getFilesOrSetupUserData(Project project) {
        Set<VirtualFile> files = project.getUserData(KPT_PROJECT_DIRS);
        if (files == null) {
            files = new HashSet<>();
            project.putUserData(KPT_PROJECT_DIRS, files);
        }
        return files;
    }

    @Override
    protected VirtualFile getVirtualFile() {
        return getValue();
    }

    @NotNull
    @Override
    public Collection<? extends AbstractTreeNode<?>> getChildren() {
        final List<VirtualFile> files = new ArrayList<>(0);
        for (VirtualFile file : getValue().getChildren()) {
            // Determine if we want to show this file or not using our cache
            if (getFilesOrSetupUserData(myProject).contains(file)) {
                files.add(file);
            }
        }

        if (files.isEmpty()) {
            return Collections.emptyList();
        }
        final List<AbstractTreeNode<?>> nodes = new ArrayList<>(files.size());
        for (VirtualFile file : files) {
            nodes.add(new KptTreeProjectNode(myProject, file));
        }

        // todo insert virtual files to represent cloud resources
        // LightVirtualFile lightVirtualFile = new LightVirtualFile("testing123");
        // nodes.add(new KptTreeProjectNode(myProject, lightVirtualFile));
        
        return nodes;
    }

    @Override
    protected void update(PresentationData data) {
        data.setIcon(getValue().isDirectory() ? AllIcons.Nodes.Folder : getValue().getFileType().getIcon());
        data.setPresentableText(getValue().getName());
    }

    @Override
    public boolean canNavigate() {
        return !getValue().isDirectory();
    }

    @Override
    public boolean canNavigateToSource() {
        return canNavigate();
    }

    @Override
    public void navigate(boolean requestFocus) {
        FileEditorManager.getInstance(myProject).openFile(getValue(), false);
    }

    private void subscribeToVFS(final Project project) {
        final Alarm alarm = new Alarm(Alarm.ThreadToUse.POOLED_THREAD, project);
        LocalFileSystem.getInstance().addVirtualFileListener(new VirtualFileListener() {
            {
                final VirtualFileListener me = this;
                Disposer.register(project, () -> LocalFileSystem.getInstance().removeVirtualFileListener(me));
            }

            @Override
            public void fileCreated(@NotNull VirtualFileEvent event) {
                handle(event);
            }

            @Override
            public void fileDeleted(@NotNull VirtualFileEvent event) {
                handle(event);
            }

            void handle(VirtualFileEvent event) {
                if (isRelevant(event.getFile())) {
                    alarm.cancelAllRequests();
                    alarm.addRequest(() -> {
                        getFilesOrSetupUserData(project).clear();
                        scanAndFilterInfraKpt(project);
                        SwingUtilities.invokeLater(() -> ProjectView.getInstance(myProject)
                                .getProjectViewPaneById(KptTreeProjectViewPane.ID)
                                .updateFromRoot(true));
                    }, 1000);
                }
            }
        });
    }

}
