package com.example.data.firebase

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.model.CaptainId
import com.example.data.model.PlayerState
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PirateRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun ensureUserProfile_authenticatedUser_createsAndReadsProfile() = runBlocking {
        val uid = signInTestUser(ALICE_EMAIL)
        val repository = PirateRepository(firestore)

        val result = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.ensureUserProfile("Captain Alice", CaptainId.MARINA)
        }
        assertTrue(result.isSuccess)
        val profile = result.getOrThrow()
        assertEquals(uid, profile.userId)
        assertEquals("marina", profile.preferredCaptain)

        val observed = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeUserProfile(uid).first { it != null }
        }
        assertNotNull(observed)
        assertEquals("Captain Alice", observed?.displayName)
    }

    @Test
    fun createAndJoinRoom_multiUser_succeedsAndSyncsMessages() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val aliceRepo = PirateRepository(firestore)

        val roomCode = withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.createRoom("Alice", CaptainId.IGNIS, isPublic = true, maxPlayers = 4).getOrThrow()
        }
        assertEquals(6, roomCode.length)

        val bobUid = signInTestUser(BOB_EMAIL)
        val bobRepo = PirateRepository(firestore)
        val joinedRoom = withTimeout(DEFAULT_TIMEOUT_MS) {
            bobRepo.joinRoomByCode(roomCode, "Bob", CaptainId.VANE).getOrThrow()
        }
        assertTrue(bobUid in joinedRoom.memberUids)
        assertTrue(aliceUid in joinedRoom.memberUids)

        // Host Alice starts match
        signInTestUser(ALICE_EMAIL)
        val startPlayers = listOf(
            PlayerState(0, aliceUid, "Alice", CaptainId.IGNIS, isBot = false),
            PlayerState(1, bobUid, "Bob", CaptainId.VANE, isBot = false)
        )
        val startRes = withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.startMatch(roomCode, startPlayers, "Voyage begins!")
        }
        assertTrue(startRes.isSuccess)

        // Send quick chat + sticker
        val msgRes = withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.sendMessage(
                roomCode = roomCode,
                senderName = "Alice",
                captain = CaptainId.IGNIS,
                text = "Ahoy mateys!",
                stickerId = "ahoy",
                memberUids = listOf(aliceUid, bobUid)
            )
        }
        assertTrue(msgRes.isSuccess)

        val msgs = withTimeout(FLOW_TIMEOUT_MS) {
            aliceRepo.observeMessages(roomCode, aliceUid).first { it.isNotEmpty() }
        }
        assertEquals("Ahoy mateys!", msgs.first().text)
    }

    @Test
    fun observeMyRooms_unauthenticatedUser_failsWithPermissionDenied() = runBlocking {
        auth.signOut()
        val repository = PirateRepository(firestore)

        var caught: Throwable? = null
        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                repository.observeMyRooms("fake_uid").first()
            }
        } catch (t: Throwable) {
            caught = t
        }
        assertNotNull("Expected PERMISSION_DENIED error", caught)
        val fsError = caught?.unwrapFirestoreException()
        assertNotNull("Expected nested FirebaseFirestoreException, got $caught", fsError)
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsError?.code)
    }

    @Test
    fun crossUserRoomAccess_nonMemberPlayingRoom_failsWithPermissionDenied() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val aliceRepo = PirateRepository(firestore)
        val roomCode = withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.createRoom("Alice", CaptainId.IGNIS, isPublic = false, maxPlayers = 2).getOrThrow()
        }
        val players = listOf(
            PlayerState(0, aliceUid, "Alice", CaptainId.IGNIS, isBot = false),
            PlayerState(1, "bot_1", "Bot Barnaby", CaptainId.BARNABY, isBot = true)
        )
        withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.startMatch(roomCode, players, "Started!")
        }

        // Bob is NOT a member and room is already playing
        signInTestUser(BOB_EMAIL)
        val bobRepo = PirateRepository(firestore)
        val joinRes = withTimeout(DEFAULT_TIMEOUT_MS) {
            bobRepo.joinRoomByCode(roomCode, "Bob", CaptainId.MARINA)
        }
        assertTrue(joinRes.isFailure)
        val fsErr = joinRes.exceptionOrNull()?.unwrapFirestoreException()
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsErr?.code)
    }

    private companion object {
        const val ALICE_EMAIL = "alice_pirate@test.com"
        const val BOB_EMAIL = "bob_pirate@test.com"
        const val DEFAULT_TIMEOUT_MS = 5000L
        const val FLOW_TIMEOUT_MS = 3000L
    }
}
