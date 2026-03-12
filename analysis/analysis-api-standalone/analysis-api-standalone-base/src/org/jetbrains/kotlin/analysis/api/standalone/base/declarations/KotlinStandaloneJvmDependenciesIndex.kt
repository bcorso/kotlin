/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.analysis.api.standalone.base.declarations

import com.github.benmanes.caffeine.cache.Caffeine
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.kotlin.cli.jvm.index.JavaFileExtension
import org.jetbrains.kotlin.cli.jvm.index.JavaRoot
import org.jetbrains.kotlin.cli.jvm.index.JvmDependenciesIndexBase
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.utils.SmartList

// TODO (marco): Document.

class KotlinStandaloneJvmDependenciesIndex(
    roots: List<JavaRoot>,
) : JvmDependenciesIndexBase(roots, shouldOnlyFindFirstClass = false) {
    private val virtualFileCache =
        Caffeine.newBuilder()
            .maximumSize(5000)
            .build<FqName, Map<String, List<VirtualFile>>>()

    override fun findClassVirtualFiles(
        classId: ClassId,
        acceptedExtensions: Collection<JavaFileExtension>,
    ): Collection<VirtualFile> {
        val packageFiles = virtualFileCache.get(classId.packageFqName, ::computePackageFiles)!!
        val files = packageFiles[classId.relativeClassName.asString()] ?: return emptyList()

        // We don't need to filter the files if all extensions are requested.
        if (acceptedExtensions.size == JavaFileExtension.entries.size) {
            return files
        }

        // While this is technically quadratic, the list of files should be very small (usually 1 element). I believe this will be faster
        // for most cases compared to building a hash set of accepted extensions.
        return files.filter { file ->
            val extension = file.extension ?: return@filter false
            acceptedExtensions.any { it.extension == extension }
        }
    }

    private fun computePackageFiles(packageFqName: FqName): Map<String, List<VirtualFile>> {
        val result = HashMap<String, SmartList<VirtualFile>>()
        traverseVirtualFilesInPackage(packageFqName, ALL_ROOT_TYPES) { virtualFile, _ ->
            val extension = virtualFile.extension
            if (extension != null && extension in CACHED_EXTENSIONS && !virtualFile.isDirectory) {
                val relativeClassName = virtualFile.nameWithoutExtension.replace('$', '.')
                result.getOrPut(relativeClassName, ::SmartList).add(virtualFile)
            }
            true // continue
        }
        return result
    }

    companion object {
        private val ALL_ROOT_TYPES = JavaRoot.RootType.entries.toSet()
        private val CACHED_EXTENSIONS = JavaFileExtension.entries.mapTo(HashSet()) { it.extension }
    }
}
