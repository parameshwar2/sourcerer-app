// Copyright 2018 Sourcerer Inc. All Rights Reserved.
// Author: Liubov Yaronskaya (lyaronskaya@sourcerer.io)

package test.tests.hashers

import app.FactCodes
import app.api.MockApi
import app.hashers.AuthorDistanceHasher
import app.hashers.CommitCrawler
import app.model.Author
import app.model.Fact
import app.model.Repo
import org.eclipse.jgit.api.Git
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.given
import org.jetbrains.spek.api.dsl.it
import test.utils.TestRepo
import java.io.File
import java.util.*
import kotlin.test.assertTrue
import kotlin.test.fail

class AuthorDistanceHasherTest : Spek({
    given("repo with a file") {
        val testRepoPath = "../author_distance_hasher"
        val testRepo = TestRepo(testRepoPath)
        val serverRepo = Repo(rehash = "test_repo_rehash")
        val api = MockApi(mockRepo = serverRepo)
        val fileName = "test1.txt"
        val author1 = Author("First Author", "first.author@gmail.com")
        val author2 = Author("Second Author", "second.author@gmail.com")
        val author3 = Author("Third Author", "third.author@gmail.com")
        val emails = hashSetOf(author1.email, author2.email, author3.email)

        testRepo.createFile(fileName, listOf("line1", "line2"))
        testRepo.commit(message = "initial commit",
                author = author1,
                date = Calendar.Builder().setDate(2017, 1, 1).setTimeOfDay
                (0, 0, 0).build().time)

        testRepo.deleteLines(fileName, 1, 1)
        testRepo.commit(message = "delete second line",
                author = author2,
                date = Calendar.Builder().setDate(2017, 1, 1).setTimeOfDay
                (0, 1, 0).build().time)

        testRepo.deleteLines(fileName, 0, 0)
        testRepo.commit(message = "delete first line",
                author = author1,
                date = Calendar.Builder().setDate(2018, 1, 1).setTimeOfDay
                (0, 1, 0).build().time)
        testRepo.insertLines(fileName, 0, listOf("line1"))
        testRepo.commit(message = "add first line",
                author = author3,
                date = Calendar.Builder().setDate(2019, 1, 1).setTimeOfDay
                (0, 1, 0).build().time)

        val gitHasher = Git.open(File(testRepoPath))
        it("extracts colleagues") {
            val observable = CommitCrawler.getJGitPathsObservable(gitHasher)
            AuthorDistanceHasher(serverRepo, api, emails,
                    hashSetOf(author2.email)).updateFromObservable(observable,
                    onError = { _ -> fail("exception") })

            assertTrue(api.receivedFacts.contains(
                    Fact(repo = serverRepo,
                            code = FactCodes.COLLEAGUES,
                            author = author2,
                            value = author1.email,
                            value2 = (1.0).toString())
            ))

            assertTrue(api.receivedFacts.contains(
                    Fact(repo = serverRepo,
                            code = FactCodes.COLLEAGUES,
                            author = author2,
                            value = author3.email,
                            value2 = (0.0).toString())
            ))
        }

        afterGroup {
            testRepo.destroy()
        }
    }
})
