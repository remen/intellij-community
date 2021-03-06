// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.target.java

import com.intellij.execution.ExecutionBundle
import com.intellij.execution.RunnerAndConfigurationSettings
import com.intellij.execution.target.LanguageRuntimeType
import com.intellij.execution.target.TargetEnvironmentConfiguration
import com.intellij.icons.AllIcons
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.lang.JavaVersion
import com.intellij.util.text.nullize
import org.jetbrains.annotations.Nls
import java.util.concurrent.CompletableFuture

class JavaLanguageRuntimeType : LanguageRuntimeType<JavaLanguageRuntimeConfiguration>(TYPE_ID) {
  override val icon = AllIcons.FileTypes.Java

  @NlsSafe
  override val displayName = "Java"

  @Nls
  override val configurableDescription = ExecutionBundle.message("JavaLanguageRuntimeType.configurable.description.configure.java")

  @Nls
  override val launchDescription = ExecutionBundle.message("JavaLanguageRuntimeType.launch.description.run.java.application")

  override fun isApplicableTo(runConfig: RunnerAndConfigurationSettings) = true

  override fun createDefaultConfig() = JavaLanguageRuntimeConfiguration()

  override fun createSerializer(config: JavaLanguageRuntimeConfiguration): PersistentStateComponent<*> = config

  override fun createConfigurable(project: Project,
                                  config: JavaLanguageRuntimeConfiguration,
                                  target: TargetEnvironmentConfiguration): Configurable {
    return ApplicationManager.getApplication().getService(JavaLanguageRuntimeUIFactory::class.java).create(config, target)
  }

  override fun findLanguageRuntime(target: TargetEnvironmentConfiguration): JavaLanguageRuntimeConfiguration? {
    return target.runtimes.findByType<JavaLanguageRuntimeConfiguration>()
  }

  override fun createIntrospector(config: JavaLanguageRuntimeConfiguration): Introspector<JavaLanguageRuntimeConfiguration>? {
    if (config.homePath.isNotBlank() && config.javaVersionString.isNotBlank()) return null

    return object : Introspector<JavaLanguageRuntimeConfiguration> {
      override fun introspect(subject: Introspectable): CompletableFuture<JavaLanguageRuntimeConfiguration> {
        val javaHomePromise = if (config.homePath.isBlank()) {
          subject.promiseEnvironmentVariable("JAVA_HOME")
            .thenApply { acceptJavaHome(it) }
        }
        else {
          Introspector.DONE
        }

        val versionPromise = if (config.javaVersionString.isBlank()) {
          subject.promiseExecuteScript("java -version")
            .thenApply { acceptJavaVersionOutput(it) }
        }
        else {
          Introspector.DONE
        }

        return CompletableFuture.allOf(javaHomePromise, versionPromise)
          .thenApply {
            config
          }
      }

      private fun acceptJavaHome(javaHome: String?) {
        if (config.homePath.isBlank()) {
          javaHome.nullize(true)?.let {
            config.homePath = it
          }
        }
      }

      private fun acceptJavaVersionOutput(output: String?) {
        output?.let { StringUtil.splitByLines(output, true) }
          ?.firstOrNull()
          ?.let { JavaVersion.parse(it) }
          ?.let { config.javaVersionString = it.toString() }
      }
    }
  }

  override fun volumeDescriptors() = listOf(APPLICATION_FOLDER_VOLUME,
                                            CLASS_PATH_VOLUME,
                                            AGENTS_VOLUME)

  override fun duplicateConfig(config: JavaLanguageRuntimeConfiguration): JavaLanguageRuntimeConfiguration =
    duplicatePersistentComponent(this, config)

  companion object {
    @JvmStatic
    val TYPE_ID = "JavaLanguageRuntime"

    @JvmStatic
    val APPLICATION_FOLDER_VOLUME = VolumeDescriptor(JavaLanguageRuntimeType::class.qualifiedName + ":appFolder",
                                                     ExecutionBundle.message("java.language.runtime.application.folder.label"),
                                                     ExecutionBundle.message("java.language.runtime.application.folder.description"),
                                                     "/app")

    @JvmStatic
    val CLASS_PATH_VOLUME = VolumeDescriptor(JavaLanguageRuntimeType::class.qualifiedName + ":classPath",
                                             ExecutionBundle.message("java.language.runtime.classpath.volume.label"),
                                             ExecutionBundle.message("java.language.runtime.classpath.volume.description"),
                                             "")

    @JvmStatic
    val AGENTS_VOLUME = VolumeDescriptor(JavaLanguageRuntimeType::class.qualifiedName + ":agents",
                                         ExecutionBundle.message("java.language.runtime.agents.volume.label"),
                                         ExecutionBundle.message("java.language.runtime.agents.volume.description"),
                                         "")
  }
}