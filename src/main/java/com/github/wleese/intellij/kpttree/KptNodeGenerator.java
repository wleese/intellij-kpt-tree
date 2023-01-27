package com.github.wleese.intellij.kpttree;

import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import org.jetbrains.yaml.YAMLLanguage;

import java.util.List;

public class KptNodeGenerator {
    static void addGeneratedNodes(Project myProject, List<AbstractTreeNode<?>> nodes, VirtualFile file) {

        PsiManager psiManager = PsiManager.getInstance(myProject);
        PsiFile psiFile = psiManager.findFile(file);
        if (psiFile != null && psiFile.getLanguage().is(YAMLLanguage.INSTANCE)) {
            // todo insert virtual files to represent cloud resources
            // LightVirtualFile lightVirtualFile = new LightVirtualFile("testing123");
            // nodes.add(new KptTreeProjectNode(myProject, lightVirtualFile));
        }

        nodes.add(new KptTreeProjectNode(myProject, file));
    }
}
