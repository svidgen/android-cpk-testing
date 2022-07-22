package com.example.androidcpktesting

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import com.amplifyframework.AmplifyException
import com.amplifyframework.core.Amplify
import com.amplifyframework.core.model.Model
import com.amplifyframework.datastore.AWSDataStorePlugin
import com.amplifyframework.core.model.query.Where
import com.amplifyframework.datastore.DataStoreConfiguration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.*
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

import com.amplifyframework.datastore.generated.model.*
import java.util.*

class MainActivity : AppCompatActivity() {

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
            Log.i("Tutorial", "inside save coroutine ... ${item.toString()}")
            val x = Amplify.DataStore.save(item,
                {
                    Log.i("Tutorial", "saved item: ${item.toString()}")
                    continuation.resumeWith(Result.success(it.item()))
                },
                {
                    Log.e("Tutorial", "Failed to save item: ${item.toString()}")
                    continuation.resumeWithException(it)
                }
            )
            Log.i("Tutorial", "save return value: ${x.toString()}")
        }
    }

    private suspend fun <T>getById(model: Class<T>, id: String): T where T : Model {
        // val field = model::class.members.find { it -> it.name == "abc"}
        return suspendCoroutine { continuation ->
            Amplify.DataStore.query(model, Where.id(id),
                { continuation.resumeWith(Result.success(it.asSequence().first())) },
                { Log.e("Tutorial", "couldn't get the thing")}
            )
        }
    }

    private suspend fun <T>getByPk(model: Class<T>, pk: String): T where T : Model {
        // val field = model::class.members.find { it -> it.name == "abc"}
        return suspendCoroutine { continuation ->
            Amplify.DataStore.query(model, Where.identifier(model, pk),
                { continuation.resumeWith(Result.success(it.asSequence().first())) },
                { Log.e("Tutorial", "couldn't get the thing")}
            )
        }
    }

    private suspend fun getProjectByPk(projectId: String, name: String): Project {
        return suspendCoroutine { continuation ->
            Amplify.DataStore.query(
                Project::class.java,
                Where.identifier(
                    Project::class.java,
                    Project.ProjectIdentifier(projectId, name)
                ),
                { continuation.resumeWith(Result.success(it.asSequence().first())) },
                { Log.e("Tutorial", "couldn't get the thing", it)}
            )
        }
    }

    private suspend fun <T>listAll(model: Class<T>): List<T> where T : Model {
        // val field = model::class.members.find { it -> it.name == "abc"}
        return suspendCoroutine { continuation ->
            Amplify.DataStore.query(model,
                { continuation.resumeWith(Result.success(it.asSequence().toList())) },
                { Log.e("Tutorial", "couldn't get the thing")}
            )
        }
    }

    private suspend fun testProjects() = coroutineScope {
        Log.i("Tutorial", "inside testNotes")

        /**
         * the `async {  }` wrapper and subsequent `await()` are very unnecessary
         * for my use-case here. i'm just leaving them in for demonstrative purposes.
         */
        val deferredSavedProject = async { save(
            Project.builder()
                 .projectId(UUID.randomUUID().toString())
                 .name("some project name")
                .build()
        )}
        val savedProject = deferredSavedProject.await();
        Log.i("Tutorial", "awaited saved project ${savedProject.projectId} -> ${savedProject.name}")

        val savedTeam = save(Team.builder()
            .teamId(UUID.randomUUID().toString())
            .name("some team name")
            .project(savedProject)
            .build()
        )
        Log.i("Tutorial", "awaited saved team ${savedTeam.teamId} -> ${savedTeam.name}")

        /**
         * when we're dealing with suspended functions (deferred values), we can
         * just call them like normal functions in the scope of a coroutine.
         */
//        val retrievedProject = getByPk(
//            Project::class.java,
//            savedProject.projectId
//        )
//        Log.i("Tutorial", "awaited retrieved note ${retrievedProject.projectId} -> ${retrievedProject.name}")

        val retrievedProject = getProjectByPk(savedProject.projectId, savedProject.name)
        Log.i("Tutorial", "awaited retrieved project ${retrievedProject.projectId} -> ${retrievedProject.name}")

        val allProjects = listAll(Project::class.java)
        for (p in allProjects) {
            Log.i("Tutorial", "listAll project ${p.projectId} -> ${p.name}")
        }
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
            testProjects()
            Log.i("Tutorial", "at the bottom of the launch")
        }

        Log.i("Tutorial", "after scope (will log before coroutine actually starts)")

    }
}