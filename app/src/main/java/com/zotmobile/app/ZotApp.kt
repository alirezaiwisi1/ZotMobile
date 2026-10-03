package com.zotmobile.app

import android.app.Application
import com.zotmobile.core.agent.AgentTools
import com.zotmobile.core.ai.DefaultProviderRegistry
import com.zotmobile.core.git.GitRepository
import com.zotmobile.core.github.GitHubClient
import com.zotmobile.core.project.ProjectManager
import com.zotmobile.core.security.SecretStore
import com.zotmobile.core.termux.TermuxCommandRunner
import java.io.File

/** Application-wide singletons (lightweight manual DI — no framework needed). */
class ZotApp : Application() {

    lateinit var secretStore: SecretStore
        private set
    lateinit var projectManager: ProjectManager
        private set
    lateinit var runner: TermuxCommandRunner
        private set
    lateinit var gitHubClient: GitHubClient
        private set
    lateinit var providerRegistry: DefaultProviderRegistry
        private set

    override fun onCreate() {
        super.onCreate()
        secretStore = SecretStore(this)
        runner = TermuxCommandRunner(this) { currentWorkDir }
        projectManager = ProjectManager(this)
        gitHubClient = GitHubClient { secretStore.get(SecretStore.SECRET_GITHUB_TOKEN) }
        providerRegistry = DefaultProviderRegistry()
    }

    /** Directory commands run in; set when a project is opened. */
    @Volatile
    var currentWorkDir: String = File(filesDir, "projects").apply { mkdirs() }.absolutePath

    fun gitFor(dir: File): GitRepository = GitRepository(dir, runner)

    fun agentToolsFor(dir: File): AgentTools = AgentTools(dir, runner)
}
