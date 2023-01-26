package com.github.wleese.intellij.kpttree;

import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;

import java.util.List;

public class KptNodeGenerator {
    static void addGeneratedNodes(Project myProject, List<AbstractTreeNode<?>> nodes, VirtualFile file) {
        nodes.add(new KptTreeProjectNode(myProject, file));
    }
}
