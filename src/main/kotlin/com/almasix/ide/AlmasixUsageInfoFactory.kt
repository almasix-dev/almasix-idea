package com.almasix.ide

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.PsiManager
import com.intellij.usageView.UsageInfo

/** PSI bridge for Find Usages — excluded from the coverage gate. */
object AlmasixUsageInfoFactory {
    fun usageInfos(project: Project, occurrences: List<AlmasixCallSiteSearcher.Occurrence>): List<UsageInfo> {
        val psiManager = PsiManager.getInstance(project)
        val lfs = LocalFileSystem.getInstance()
        val infos = mutableListOf<UsageInfo>()
        for (occ in occurrences) {
            val vFile = lfs.findFileByNioFile(occ.path)
                ?: lfs.findFileByPath(occ.path.toString())
                ?: continue
            val psiFile = psiManager.findFile(vFile) ?: continue
            infos.add(UsageInfo(psiFile, occ.range.startOffset, occ.range.endOffset, false))
        }
        return infos
    }
}
