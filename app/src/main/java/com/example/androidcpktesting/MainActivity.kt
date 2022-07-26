package com.example.androidcpktesting

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.amplifyframework.AmplifyException
import com.amplifyframework.core.Amplify
import com.amplifyframework.core.model.Model
import com.amplifyframework.core.model.query.ObserveQueryOptions
import com.amplifyframework.core.model.query.QueryOptions
import com.amplifyframework.core.model.query.Where
import com.amplifyframework.core.model.query.predicate.QueryPredicate
import com.amplifyframework.datastore.AWSDataStorePlugin
import com.amplifyframework.datastore.DataStoreConfiguration
import com.amplifyframework.datastore.generated.model.Project
import com.amplifyframework.datastore.generated.model.Team
import kotlinx.coroutines.*
import java.lang.Exception
import java.util.*
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

class MainActivity : AppCompatActivity() {

    /**
     * Executes a given story and logs output about whether the case succeeded or failed.
     *
     * @param story A description of the use-case.
     * @param action The code to execute to stress the testing story.
     * @param fastFollow Whether the test is expected to pass *right now*. "Fast follow"
     * essentially indicates that the failure is known, so the failure doesn't appear in
     * the logs with a full a trace. These failures are also marked as "KNOWN" errors
     * instead of "FAILED" stories.
     */
    private suspend fun test(
        story: String,
        action: suspend () -> Unit,
        isKnown: Boolean = false
    ) = coroutineScope {
        Log.i("Verbose", "PENDING : $story")
        try {
            action()
            Log.i("Story", "MET     : $story")
        } catch (error: Exception) {
            if (isKnown) {
                Log.e("Story", "KNOWN   : $story")
            } else {
                Log.e("Story", "FAILED  : $story", error)
            }
        } finally {
            // clear()
            Log.i("Verbose", "FINALLY : $story")
        }
    }

    private fun expect(expectation: String, isMet: Boolean) {
        when {
            isMet -> {
                Log.i("Expectation", "MET     : ${expectation}")
            }
            else -> {
                Log.e("Expectation", "FAILED  : ${expectation}")
                throw Exception("Expectation failed: ${expectation}")
            }
        }
    }

    private fun setTimeout(time: Long, f: () -> Unit) {
        GlobalScope.launch {
            delay(time)
            f()
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
                {
                    Log.e("Tutorial", "get() couldn't get the thing", it)
                    continuation.resumeWithException(it)
                }
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
                {
                    Log.e("Tutorial", "couldn't get the thing 6", it)
                    continuation.resumeWithException(it)
                }
            )
        }
    }

    private suspend fun <T>delete(item: T): T where T : Model {
        return suspendCoroutine { continuation ->
            Amplify.DataStore.delete(
                item,
                { continuation.resume(it.item()) },
                {
                    Log.e("Tutorial", "delete() failure", it)
                    continuation.resumeWithException(it)
                }
            )
        }
    }

    private suspend fun <T>delete(model: Class<T>, predicate: QueryPredicate): Unit where T : Model {
        return suspendCoroutine { continuation ->
            Amplify.DataStore.delete(
                model,
                predicate,
                { continuation.resume(Unit) },
                {
                    Log.e("Tutorial", "delete() failure", it)
                    continuation.resumeWithException(it)
                }
            )
        }
    }

    private suspend fun <T>waitForObservedRecord(model: Class<T>, predicate: QueryPredicate):
            T where T : Model = suspendCoroutine { continuation ->

        var unsubscribe = {
            Log.e("Tutorial", "subscription NOT canceled")
        }

        Amplify.DataStore.observe(
            model,
            predicate,

            // on start -> Cancelable
            {
                unsubscribe = {
                    it.cancel()
                    Log.i("Tutorial", "unsubscribed")
                }
                Log.i("Tutorial", "subscription established")
            },

            // on item change
            {
                Log.i("Tutorial", "on item change $it")
                unsubscribe()
                continuation.resume(it.item())
            },

            // on failure
            {
                Log.e("Tutorial", "on failure", it)
                unsubscribe()
                continuation.resumeWithException(it)
            },

            // on complete
            { Log.i("Tutorial", "on failure")},
        )
    }

    private suspend fun <T>observeQueryForTime(
        model: Class<T>,
        predicate: QueryPredicate,
        time: Long
    ): List<List<T>> where T : Model = suspendCoroutine { continuation ->
        var unsubscribe = {
            Log.e("Tutorial", "subscription NOT canceled")
        }

        var snapshots = ArrayList<List<T>>();

        setTimeout(time) {
            unsubscribe()
            continuation.resume(snapshots)
        }

        Amplify.DataStore.observeQuery(
            model,
            ObserveQueryOptions().matches(predicate),

            // on start -> Cancelable
            {
                unsubscribe = {
                    it.cancel()
                    Log.i("Tutorial", "unsubscribed")
                }
                Log.i("Tutorial", "subscription established")
            },

            // on snapshot
            {
                Log.i("Tutorial", "on items received $it")
                snapshots.add(it.items)
            },

            // on failure
            {
                Log.e("Tutorial", "on failure", it)
                unsubscribe()
                continuation.resumeWithException(it)
            },

            // on complete
            { Log.i("Tutorial", "on failure")},
        )
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
        // used to isolate entries from this test run.
        val isolationKey = UUID.randomUUID().toString();

        val itemsToCreate = 5;

        for (i in 1..itemsToCreate) {
            save(Project.builder()
                .projectId(UUID.randomUUID().toString())
                .name("project canQueryAll $i ($isolationKey)")
                .build()
            )
        }

        val projects = list(Project::class.java);
        expect(
            "There are at least $itemsToCreate total projects (there may be more)",
            projects.size >= itemsToCreate
        )

        val canQueryAllProjets = projects.filter {
            it.name.contains("($isolationKey)")
        }
        expect(
            "All $itemsToCreate of our canQueryAll projects are in the collection",
            canQueryAllProjets.size == itemsToCreate
        )
    }

    suspend fun canQueryProjectBySimplePredicate() {
        // used to isolate entries from this test run.
        val isolationKey = UUID.randomUUID().toString();

        val itemsToCreate = 5;

        for (i in 1..itemsToCreate) {
            save(Project.builder()
                .projectId(UUID.randomUUID().toString())
                .name("project canQueryProjectBySimplePredicate $i ($isolationKey)")
                .build()
            )
        }

        val projects = list(
            Project::class.java,
            Where.matches(
                Project.NAME.contains("canQueryProjectBySimplePredicate")
                .and(Project.NAME.contains(isolationKey))
            )
        );

        expect(
            "There are exactly $itemsToCreate matching projects",
            projects.size == itemsToCreate
        )
    }

    suspend fun canDeleteProject() {
        val project = save(Project.builder()
            .projectId(UUID.randomUUID().toString())
            .name("canDeleteProjectByTeamFK project")
            .build()
        )

        val deleted = delete(project)
        expect(
            "deleted item is for the initial project",
            deleted.projectId == project.projectId
        )

        val nothing = get(project);
        expect(
            "after deletion, the project can no longer be retrieved",
            nothing == null
        )
    }

    suspend fun canQueryProjectByTeamFKPredicate() {
        // used to isolate entries from this test run.
        val isolationKey = UUID.randomUUID().toString();

        val team = save(Team.builder()
            .teamId(UUID.randomUUID().toString())
            .name("team name canQueryProjectByTeamFKPredicate ($isolationKey)")
            .build()
        )

        val itemsToCreate = 5;

        for (i in 1..itemsToCreate) {
            save(Project.builder()
                .projectId(UUID.randomUUID().toString())
                .name("project canQueryProjectBySimplePredicate $i ($isolationKey)")
                .projectTeamTeamId(team.teamId)
                .projectTeamName(team.name)
                .build()
            )
        }

        val projects = list(
            Project::class.java,
            Where.matches(
                Project.PROJECT_TEAM_TEAM_ID.eq(team.teamId)
                    .and(Project.PROJECT_TEAM_NAME.eq(team.name))
            )
        );

        expect(
            "There are exactly $itemsToCreate matching projects",
            projects.size == itemsToCreate
        )
    }

    suspend fun canUpdateProjectFKFields() {
        val teamA = save(Team.builder()
            .teamId(UUID.randomUUID().toString())
            .name("canUpdateProjectFKFields team A")
            .build()
        )

        val teamB = save(Team.builder()
            .teamId(UUID.randomUUID().toString())
            .name("canUpdateProjectFKFields team B")
            .build()
        )

        val project = save(Project.builder()
            .projectId(UUID.randomUUID().toString())
            .name("canUpdateProjectFKFields project")
            .projectTeamTeamId(teamA.teamId)
            .projectTeamName(teamA.name)
            .build()
        )

        val retrievedProject = get(project)
        val updatedProject = save(retrievedProject!!.copyOfBuilder()
            .projectTeamTeamId(teamB.teamId)
            .projectTeamName(teamB.name)
            .build()
        )

        val retrievedUpdatedProject = get(updatedProject)
        expect(
            "the project's team ID points to team B",
            retrievedUpdatedProject?.projectTeamTeamId == teamB.teamId
        )
        expect(
            "the project's team name points to team B",
            retrievedUpdatedProject?.projectTeamName == teamB.name
        )
    }

    suspend fun canDeleteProjectByTeamFK() {
        val team = save(Team.builder()
            .teamId(UUID.randomUUID().toString())
            .name("canDeleteProjectByTeamFK team")
            .build()
        )

        val project = save(Project.builder()
            .projectId(UUID.randomUUID().toString())
            .name("canDeleteProjectByTeamFK project")
            .projectTeamTeamId(team.teamId)
            .projectTeamName(team.name)
            .build()
        )

        delete(
            project.javaClass,
            Project.PROJECT_TEAM_TEAM_ID.eq(team.teamId).and(
                Project.PROJECT_TEAM_NAME.eq(team.name)
            )
        );

        val retrieved = list(
            Project::class.java,
            Where.matches(Project.PROJECT_TEAM_TEAM_ID.eq(team.teamId).and(
                Project.PROJECT_TEAM_NAME.eq(team.name)
            ))
        )
        expect(
            "there should be no team-associated projects left",
            retrieved.isEmpty()
        )
    }

    suspend fun canObserveProjectCreateByFK() = coroutineScope {
        val team = save(Team.builder()
            .teamId(UUID.randomUUID().toString())
            .name("canObserveProjectCreateByFK team name")
            .build()
        )

        // this isn't quite right either is it ...
        val updated = async { waitForObservedRecord(
            Project::class.java,
            Project.PROJECT_TEAM_TEAM_ID.eq(team.teamId).and(
                Project.PROJECT_TEAM_NAME.eq(team.name)
            )
        )}

        val saved = save(Project.builder()
            .projectId(UUID.randomUUID().toString())
            .name("canObserveProjectCreateByFK project")
            .projectTeamTeamId(team.teamId)
            .projectTeamName(team.name)
            .build()
        )

        expect(
            "observer message arrives for PK",
            updated.await() != null
        )
        expect(
            "observed project matches",
            updated.await().projectId == saved.projectId
        )
    }

    suspend fun canObserveProjectUpdateByFK() = coroutineScope {
        val teamA = save(Team.builder()
            .teamId(UUID.randomUUID().toString())
            .name("canObserveProjectUpdateByFK team A name")
            .build()
        )

        val teamB = save(Team.builder()
            .teamId(UUID.randomUUID().toString())
            .name("canObserveProjectUpdateByFK team B name")
            .build()
        )

        val saved = save(Project.builder()
            .projectId(UUID.randomUUID().toString())
            .name("canObserveProjectUpdateByFK project")
            .projectTeamTeamId(teamA.teamId)
            .projectTeamName(teamA.name)
            .build()
        )

        val observedUpdate = async { waitForObservedRecord(
            Project::class.java,
            Project.PROJECT_TEAM_TEAM_ID.eq(teamB.teamId).and(
                Project.PROJECT_TEAM_NAME.eq(teamB.name)
            )
        )}

        val updated = save(saved.copyOfBuilder()
            .projectTeamTeamId(teamB.teamId)
            .projectTeamName(teamB.name)
            .build()
        )

        expect(
            "observer message arrives for PK",
            observedUpdate.await() != null
        )
        expect(
            "observed project matches",
            observedUpdate.await().projectId == saved.projectId
        )
    }

    suspend fun canObserveQueryProjectCreateByFK() = coroutineScope {
        val team = save(Team.builder()
            .teamId(UUID.randomUUID().toString())
            .name("canObserveQueryProjectCreateByFK team")
            .build()
        )

        val snapshots = async { observeQueryForTime(
            Project::class.java,
            Project.PROJECT_TEAM_TEAM_ID.eq(team.teamId).and(
                Project.PROJECT_TEAM_NAME.eq(team.name)
            ),
            3000
        )}

        val saved = save(Project.builder()
            .projectId(UUID.randomUUID().toString())
            .name("canObserveQueryProjectCreateByFK")
            .projectTeamTeamId(team.teamId)
            .projectTeamName(team.name)
            .build()
        )

        Log.i("Verbose", "snapshots ${snapshots.await()}")

        // this isn't super deterministic, but we have some invariants we can check.

        expect(
            "a single snapshot is received",
            snapshots.await().size >= 1
        )

        var haveSeenPopulatedSnapshot = false

        for (snapshot in snapshots.await()) {
            if (snapshot.size > 0) {
                haveSeenPopulatedSnapshot = true
                for (item in snapshot) {
                    expect(
                        "the snapshot's item matches our saved project",
                        item.projectId == saved.projectId
                    )
                }
            }
        }

        expect(
            "at least one snapshot was populated",
            haveSeenPopulatedSnapshot
        )

    }

    suspend fun canObserveQueryProjectUpdateByFK() = coroutineScope {
        val teamA = save(Team.builder()
            .teamId(UUID.randomUUID().toString())
            .name("canObserveQueryProjectCreateByFK team A")
            .build()
        )

        val teamB = save(Team.builder()
            .teamId(UUID.randomUUID().toString())
            .name("canObserveQueryProjectCreateByFK team B")
            .build()
        )

        val saved = save(Project.builder()
            .projectId(UUID.randomUUID().toString())
            .name("canObserveQueryProjectCreateByFK")
            .projectTeamTeamId(teamA.teamId)
            .projectTeamName(teamA.name)
            .build()
        )

        val snapshots = async { observeQueryForTime(
            Project::class.java,
            Project.PROJECT_TEAM_TEAM_ID.eq(teamB.teamId).and(
                Project.PROJECT_TEAM_NAME.eq(teamB.name)
            ),
            3000
        )}

        var updated = save(saved.copyOfBuilder()
            .projectTeamTeamId(teamB.teamId)
            .projectTeamName(teamB.name)
            .build()
        )

        Log.i("Verbose", "snapshots ${snapshots.await()}")

        expect(
            "a single snapshot is received",
            snapshots.await().size >= 1
        )

        var haveSeenPopulatedSnapshot = false

        for (snapshot in snapshots.await()) {
            if (snapshot.size > 0) {
                haveSeenPopulatedSnapshot = true
                for (item in snapshot) {
                    expect(
                        "the snapshot's item matches our saved project",
                        item.projectId == updated.projectId
                    )
                }
            }
        }

        expect(
            "at least one snapshot was populated",
            haveSeenPopulatedSnapshot
        )
    }

    suspend fun canQueryTeamBySimplePredicate() = coroutineScope {
        // used to isolate entries from this test run.
        val isolationKey = UUID.randomUUID().toString();

        val itemsToCreate = 5;

        for (i in 1..itemsToCreate) {
            save(Team.builder()
                .teamId(UUID.randomUUID().toString())
                .name("team canQueryTeamBySimplePredicate $i ($isolationKey)")
                .build()
            )
        }

        val teams = list(
            Team::class.java,
            Where.matches(
                Team.NAME.contains("canQueryTeamBySimplePredicate")
                    .and(Team.NAME.contains(isolationKey))
            )
        );

        expect(
            "There are exactly $itemsToCreate matching teams (there may be more)",
            teams.size == itemsToCreate
        )
    }

    suspend fun canQueryTeamByProjectFKPredicate() = coroutineScope {
        // used to isolate entries from this test run.
        val isolationKey = UUID.randomUUID().toString();

        val project = save(Project.builder()
            .projectId(UUID.randomUUID().toString())
            .name("project name canQueryTeamByProjectFKPredicate ($isolationKey)")
            .build()
        )

        val itemsToCreate = 5;

        for (i in 1..itemsToCreate) {
            save(Team.builder()
                .teamId(UUID.randomUUID().toString())
                .name("team canQueryTeamByProjectFKPredicate $i ($isolationKey)")
                .project(project)
                .build()
            )
        }

        val teams = async (
            SupervisorJob()) {
            list(
                Team::class.java,
                Where.matches(Team.PROJECT.eq(project))
            )
        };

        expect(
            "There are exactly $itemsToCreate matching projects",
            teams.await().size == itemsToCreate
        )
    }

    suspend fun canUpdateTeamFKFields() = coroutineScope {
        val projectA = save(Project.builder()
            .projectId(UUID.randomUUID().toString())
            .name("canUpdateTeamFKFields project A")
            .build()
        )

        val projectB = save(Project.builder()
            .projectId(UUID.randomUUID().toString())
            .name("canUpdateTeamFKFields project B")
            .build()
        )

        val team = save(Team.builder()
            .teamId(UUID.randomUUID().toString())
            .name("canUpdateTeamFKFields team")
            .project(projectA)
            .build()
        )

        val retrievedTeam = get(team)
        val updatedTeam = save(retrievedTeam!!.copyOfBuilder()
            .project(projectB)
            .build()
        )

        val retrievedUpdatedTeam = get(updatedTeam)
        expect(
            "the team's team ID points to project B",
            retrievedUpdatedTeam?.project?.projectId == projectB.projectId
        )
        expect(
            "the team's team name points to project B",
            retrievedUpdatedTeam?.project?.name == projectB.name
        )
    }

    suspend fun canDeleteTeamByTeamFK() = coroutineScope {
        val project = save(Project.builder()
            .projectId(UUID.randomUUID().toString())
            .name("canDeleteTeamByTeamFK team")
            .build()
        )

        val team = save(Team.builder()
            .teamId(UUID.randomUUID().toString())
            .name("canDeleteTeamByTeamFK project")
            .project(project)
            .build()
        )

        delete(
            team.javaClass,
            Team.PROJECT.eq(project)
        );

        val singleRetrieved = get(team)
        expect(
            "the re-retrieved team should no longer exist",
            singleRetrieved == null
        )

        // KNOWN bug on selection ... i think ...
//        val retrieved = list(
//            Team::class.java,
//            Where.matches(Team.PROJECT.eq(project))
//        )
//        expect(
//            "there should be no team-associated projects left",
//            retrieved.isEmpty()
//        )
    }

    suspend fun canObserveTeamCreateByFK() = coroutineScope {
        val team = save(Team.builder()
            .teamId(UUID.randomUUID().toString())
            .name("canObserveProjectCreateByFK team name")
            .build()
        )

        // this isn't quite right either is it ...
        val updated = async { waitForObservedRecord(
            Project::class.java,
            Project.PROJECT_TEAM_TEAM_ID.eq(team.teamId).and(
                Project.PROJECT_TEAM_NAME.eq(team.name)
            )
        )}

        val saved = save(Project.builder()
            .projectId(UUID.randomUUID().toString())
            .name("canObserveProjectCreateByFK project")
            .projectTeamTeamId(team.teamId)
            .projectTeamName(team.name)
            .build()
        )

        expect(
            "observer message arrives for PK",
            updated.await() != null
        )
        expect(
            "observed project matches",
            updated.await().projectId == saved.projectId
        )
    }

    suspend fun canObserveTeamUpdateByFK() = coroutineScope {

    }

    suspend fun canObserveQueryTeamCreateByFK() = coroutineScope {

    }

    suspend fun canObserveQueryTeamUpdateByFK() = coroutineScope {

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

        // GlobalScope.launch(Dispatchers.Default) {
        GlobalScope.async {
            Log.i("Tutorial", "at the top of the launch")
            test("can create and retrieve a team with a project", ::canCreateAndRetrieve)
            test("can create a team without a project", ::canCreateTeamWithoutProject)
            test("can create a project with a team 'directly'", ::canCreateProjectWithTeamDirectly, true)
            test("can create a project with a team", ::canCreateProjectWithTeam, true)
            test("can query for all created projects", ::canQueryAll)
            test("can delete a project", ::canDeleteProject)

            // Project
            test("can query for created projects by predicate", ::canQueryProjectBySimplePredicate)
            test("can query for created projects by FK fields", ::canQueryProjectByTeamFKPredicate)
            test("can update created project FK fields", ::canUpdateProjectFKFields)
            test("can delete created project by FK fields", ::canDeleteProjectByTeamFK)
            test("can observe a project creation by team FK", ::canObserveProjectCreateByFK)
            test("can observe a project update by team FK", ::canObserveProjectUpdateByFK)
            test("can observeQuery a project create by team FK", ::canObserveQueryProjectCreateByFK)
            test("can observeQuery a project update by team FK", ::canObserveQueryProjectUpdateByFK)

            // Team
            test("can query for created teams by predicate", ::canQueryTeamBySimplePredicate)
            test("can query for created teams by FK fields", ::canQueryTeamByProjectFKPredicate, true)
            test("can update created team FK fields", ::canUpdateTeamFKFields)
            test("can delete created team by FK fields", ::canDeleteTeamByTeamFK, true)
//            test("can observe a team creation by team FK", ::canObserveTeamCreateByFK)
//            test("can observe a team update by team FK", ::canObserveTeamUpdateByFK)
//            test("can observeQuery a team create by team FK", ::canObserveQueryTeamCreateByFK)
//            test("can observeQuery a team update by team FK", ::canObserveQueryTeamUpdateByFK)


//            testUpdate();
//            testDelete();
//            testQueryByPredicate();
//            testObserve();
//            testObserveQuery();

            // already covered by `canCreateAndRetrieve`
            // test("can query by PK" ... )

            Log.i("Tutorial", "at the bottom of the launch")
        }

        Log.i("Tutorial", "after scope (will log before coroutine actually starts)")

    }
}