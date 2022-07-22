package com.example.androidcpktesting

import android.app.DownloadManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import com.amplifyframework.AmplifyException
import com.amplifyframework.core.Action
import com.amplifyframework.core.Amplify
import com.amplifyframework.core.model.Model
import com.amplifyframework.core.model.query.QueryOptions
import com.amplifyframework.datastore.AWSDataStorePlugin
import com.amplifyframework.core.model.query.Where
import com.amplifyframework.core.model.query.predicate.QueryPredicate
import com.amplifyframework.datastore.DataStoreConfiguration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.*
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

import com.amplifyframework.datastore.generated.model.*
import java.lang.reflect.TypeVariable
import java.util.*
import kotlin.coroutines.resume
import kotlin.reflect.KSuspendFunction0

class MainActivity : AppCompatActivity() {

    private suspend fun test(story: String, action: suspend () -> Unit) {
        Log.i("Story", "PENDING : $story")
        try {
            action()
            Log.i("Story", "MET     : $story")
        } catch (error: Error) {
            Log.e("Story", "FAILED  : $story", error)
        } finally {
            // clear()
        }
    }

    private fun expect(expectation: String, isMet: Boolean) {
        when {
            isMet -> {
                Log.i("Expectation", "MET     : ${expectation}")
            }
            else -> {
                Log.e("Expectation", "FAILED  : ${expectation}")
                throw Error("Expectation failed: ${expectation}")
            }
        }
    }

    private suspend fun <T>save(item: T): T where T : Model {
        /**
         * This construction essentially turns a callback into a `deferred`
         * object, which can be `.await()`-ed in a coroutine.
         *
         * Does DataStore already provide a `deferred` interface?
         * It looks like this should do it:
         *
         * https://ds-custom-pk.d1oosfztpocl9c.amplifyapp.com/lib/project-setup/coroutines/q/platform/android/
         *
         * TODO: Try this out as part of Android CPK testing ... Still exploring.
         */
        Log.i("Tutorial", "inside save ... ${item.toString()}")
        return suspendCoroutine { continuation ->
            Log.i("Tutorial", "inside save continuation ... ${item.toString()}")
            Amplify.DataStore.save(item,
                {
                    Log.i("Tutorial", "saved item: ${item.toString()}")
                    continuation.resume(it.item())
                },
                {
                    Log.e("Tutorial", "Failed to save item: ${item.toString()}")
                    continuation.resumeWithException(it)
                }
            )
            Log.i("Tutorial", "after save continuation ... ${item.toString()}")
        }
    }

    private suspend fun <T>get(model: Class<T>, options: QueryOptions): T? where T : Model {
        return suspendCoroutine { continuation ->
            Amplify.DataStore.query(model, options,
                {
                    val items = it.asSequence().toList()
                    when {
                        items.size == 1 -> {
                            continuation.resume(items.first())
                        }
                        items.isEmpty() -> {
                            continuation.resume(null)
                        }
                        else -> {
                            continuation.resumeWithException(Error("Multiple items found for get()"))
                        }
                    }
                },
                { Log.e("Tutorial", "get() couldn't get the thing", it)}
            )
        }
    }

    /**
     * @param instance An instance to re-fetch from local storage.
     */
    private suspend fun <T>get(instance: T): T? where T : Model {
        return suspendCoroutine { continuation ->
            Amplify.DataStore.query(
                instance::class.java,
                Where.identifier(instance::class.java, instance.resolveIdentifier()),
                {
                    val items = it.asSequence().toList()
                    when {
                        items.size == 1 -> {
                            continuation.resume(items.first())
                        }
                        items.isEmpty() -> {
                            continuation.resume(null)
                        }
                        else -> {
                            continuation.resumeWithException(Error("Multiple items found for get(instance)"))
                        }
                    }
                },
                { Log.e("Tutorial", "get(instance) couldn't get the thing", it)}
            )
        }
    }

    private suspend fun <T>list(model: Class<T>, options: QueryOptions? = null): List<T> where T : Model {
        // val field = model::class.members.find { it -> it.name == "abc"}
        return suspendCoroutine { continuation ->
            Amplify.DataStore.query(
                model,
                options ?: Where.matchesAll(),
                { continuation.resume(it.asSequence().toList()) },
                { Log.e("Tutorial", "couldn't get the thing 6")}
            )
        }
    }

    private suspend fun <T>delete(item: T): T where T : Model {
        return suspendCoroutine { continuation ->
            Amplify.DataStore.delete(
                item,
                { continuation.resume(it.item()) },
                { Log.e("Tutorial", "delete() failure", it)}
            )
        }
    }

    private suspend fun clear(): Boolean {
        return suspendCoroutine { continuation ->
            Amplify.DataStore.clear(
                {
                    Log.i("Tutorial", "DataStore cleared")
                    continuation.resume(true)
                },
                {
                    Log.e("Tutorial", "Failed to clear", it)
                    continuation.resumeWithException(it)
                }
            )
        }
    }

    private suspend fun getProjectByPk(projectId: String, name: String): Project? {
        return get(
            Project::class.java,
            Where.identifier(
                Project::class.java,
                Project.ProjectIdentifier(projectId, name)
            )
        )
    }

    private suspend fun getTeamByPk(teamId: String, name: String): Team? {
        return get(
            Team::class.java,
            Where.identifier(
                Team::class.java,
                Team.TeamIdentifier(teamId, name)
            )
        )
    }

    private suspend fun getTeamByProject(projectId: String, name: String): Team {
        return suspendCoroutine { continuation ->
            Amplify.DataStore.query(
                Team::class.java,
                Where.matches(Team.PROJECT.eq(
                    Project.builder().projectId(projectId).name(name).build()
                )),
                { continuation.resumeWith(Result.success(it.asSequence().first())) },
                { Log.e("Tutorial", "couldn't get the thing 5", it)}
            )
        }
    }

    suspend fun testProjects() = coroutineScope {
        Log.i("Tutorial", "inside testNotes")

        /**
         * the `async {  }` wrapper and subsequent `await()` are very unnecessary
         * for my use-case here. i'm just leaving them in for demonstrative purposes.
         *
         * if i'm seeing this correctly, the `async` keyword is what forces
         * us to operate in `coroutineScope`. without it, i *believe* the async
         * methods simply `async { ... }.await()` automagically under the hood.
         */
        val deferredSavedProject = async { save(
            Project.builder()
                .projectId(UUID.randomUUID().toString())
                .name("some project name")
                .projectTeamName("team name. wat?")
//                .projectTeamTeamId(UUID.randomUUID().toString())
                .build()
        )}
        val savedProject = deferredSavedProject.await();
        Log.i("Tutorial", "saved project full ${savedProject}")

        val savedTeam = save(Team.builder()
            .teamId(UUID.randomUUID().toString())
            .name("some team name")
            .project(savedProject)
            .build()
        )
        val retrievedTeam = getTeamByPk(savedTeam.teamId, savedTeam.name);
        Log.i("Tutorial", "retrieved team ${retrievedTeam}")

//        // given savedProject, can i get team?
//        val teamByProject = getTeamByProject(savedProject.projectId, savedProject.name)
//        Log.i("Tutorial", "retrieved teambyproject ${teamByProject}")

        val retrievedProject = getProjectByPk(savedProject.projectId, savedProject.name)

        Log.i("Tutorial", "retrieved project ${retrievedProject}")
        Log.i("Tutorial", "project team ${retrievedProject?.team}")

        val allProjects = list(Project::class.java)
        for (p in allProjects) {
            Log.i("Tutorial", "listAll project ${p}")
        }
    }

    private suspend fun canCreateAndRetrieve() {
        val savedProject = save(
            Project.builder()
            .projectId(UUID.randomUUID().toString())
            .name("a project that will have a team")
            .build()
        )
        val retrievedProject = get(savedProject);
        expect(
            "Retrieved project (${retrievedProject}) should match saved project (${savedProject}).",
            retrievedProject?.toString() == savedProject.toString()
        )

        val savedTeam = save(
            Team.builder()
                .teamId(UUID.randomUUID().toString())
                .name("a team with a project")
                .project(retrievedProject)
                .build()
        )
        val retrievedTeam = get(savedTeam)
        expect(
            "Retrieved team (${retrievedTeam}) should match saved team (${savedTeam}).",
            retrievedTeam?.toString() == savedTeam.toString()
        )
        expect(
            "The `project` on the retrieved team *with a project* should be populated",
        retrievedTeam?.project != null
        )
    }

    private suspend fun canCreateTeamWithoutProject() {
        val savedTeam = save(
            Team.builder()
                .teamId(UUID.randomUUID().toString())
                .name("a team without a project")
                .build()
        )
        val retrievedTeam = get(savedTeam)
        expect(
            "Retrieved team (${retrievedTeam}) should match saved team (${savedTeam}).",
            retrievedTeam?.toString() == savedTeam.toString()
        )
        expect(
            "The `project` on the retrieved team *without a project* should be `null`",
            retrievedTeam?.project == null
        )
    }

    suspend fun canCreateProjectWithTeamDirectly() {
        val team = save(
            Team.builder()
                .teamId(UUID.randomUUID().toString())
                .name("a team not yet on a project")
                .build()
        )

        expect(
            "I can create a project with a .team(existingTeam) in the builder",

            /*
             * This is what we *want* to run eventually. But, it currently
             * fails the build. Once it's fixed, uncomment and re-test.
             *
            val project = save(
                Project.builder()
                    .projectId(UUID.randomUUID().toString())
                    .name("a project that should get a team")
                    .team(team)
                    .build()
            )
             */

            false
        )
    }

    suspend fun canCreateProjectWithTeam() {
        val team = save(
            Team.builder()
                .teamId(UUID.randomUUID().toString())
                .name("canCreateProjectWithTeam team")
                .build()
        )

        expect(
            "team was created successfully",
            team != null
        )

        val project = save(
            Project.builder()
                .projectId(UUID.randomUUID().toString())
                .name("canCreateProjectWithTeam project")
                .projectTeamTeamId(team.teamId)
                .projectTeamName(team.name)
                .build()
        )
        Log.i("Tutorial", "saved project with team ${project}")

        val retrievedProject = get(project)
        Log.i("Tutorial", "retrieved project with team ${retrievedProject}")

        expect(
            "retrievedProject to have a populated team property",
            retrievedProject?.team != null
        )
        expect(
            "retrievedProject's populated team property matches saved team",
            retrievedProject?.team.toString() == team.toString()
        )
    }

    suspend fun canQueryAll() {
    }

    suspend fun canQueryById() {
    }

    suspend fun canQueryByPredicate() {
    }

    suspend fun canUpdate() {
    }

    suspend fun canDelete() {
    }

    suspend fun canObserve() {
    }

    suspend fun canObserveQuery() {
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        try {
            val config = DataStoreConfiguration.builder()
                .errorHandler {
                    Log.e("Tutorial", "errorHandler caught", it)
                }
                .build()

            val dataStorePlugin = AWSDataStorePlugin.builder().dataStoreConfiguration(config).build()
            Amplify.addPlugin(dataStorePlugin)

            Amplify.configure(applicationContext)
            Log.i("Tutorial", "Initialized amplify app")
        } catch (failure: AmplifyException) {
            Log.e("Tutorial", "Could not initialize the app", failure)
        }

        Log.i("Tutorial", "Before coroutine scope")

        GlobalScope.launch(Dispatchers.Default) {
            Log.i("Tutorial", "at the top of the launch")
            test("can create and retrieve a team with a project", ::canCreateAndRetrieve)
            test("can create a team without a project", ::canCreateTeamWithoutProject)
            test("can create a project with a team 'directly'", ::canCreateProjectWithTeamDirectly)
            test("can create a project with a team", ::canCreateProjectWithTeam)
//            testQueryAll();
//            testQueryById();
//            testUpdate();
//            testDelete();
//            testQueryByPredicate();
//            testObserve();
//            testObserveQuery();
            Log.i("Tutorial", "at the bottom of the launch")
        }

        Log.i("Tutorial", "after scope (will log before coroutine actually starts)")

    }
}