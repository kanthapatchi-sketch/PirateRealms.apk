package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.data.firebase.PirateRepository
import com.example.ui.PirateViewModel
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.GameBoardScreen
import com.example.ui.screens.LobbyScreen
import com.example.ui.theme.PirateRealmsTheme
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

const val ROUTE_LOBBY = "lobby"
const val ROUTE_BOARD = "board"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PirateRealmsTheme {
                AppNavigation()
            }
        }
    }
}

internal fun FirebaseAuth.authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
    val listener = FirebaseAuth.AuthStateListener { auth ->
        trySend(auth.currentUser)
    }
    addAuthStateListener(listener)
    awaitClose { removeAuthStateListener(listener) }
}

@Composable
fun AppNavigation(auth: FirebaseAuth = Firebase.auth) {
    val currentUser by auth.authStateFlow()
        .collectAsStateWithLifecycle(initialValue = auth.currentUser)

    val user = currentUser
    if (user == null) {
        AuthScreen()
    } else {
        key(user.uid) {
            AuthenticatedPirateApp(
                currentUserId = user.uid,
                displayName = user.displayName ?: "Captain"
            )
        }
    }
}

@Composable
private fun AuthenticatedPirateApp(
    currentUserId: String,
    displayName: String
) {
    val viewModel: PirateViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                PirateViewModel(
                    repository = PirateRepository(db),
                    currentUserId = currentUserId,
                    initialDisplayName = displayName
                )
            }
        }
    )

    val lobbyState by viewModel.lobbyState.collectAsStateWithLifecycle()
    val profile by viewModel.userProfile.collectAsStateWithLifecycle()
    val waitingRoom by viewModel.waitingRoom.collectAsStateWithLifecycle()
    val gameState by viewModel.gameState.collectAsStateWithLifecycle()
    val selectedDiceMode by viewModel.selectedDiceMode.collectAsStateWithLifecycle()
    val diceRollAnimState by viewModel.diceRollAnimState.collectAsStateWithLifecycle()
    val stepMoveState by viewModel.stepMoveState.collectAsStateWithLifecycle()
    val isOnlineMatch by viewModel.isOnlineMatch.collectAsStateWithLifecycle()
    val activeRoomCode by viewModel.activeRoomCode.collectAsStateWithLifecycle()
    val stickerPopups by viewModel.stickerPopups.collectAsStateWithLifecycle()

    val navController = rememberNavController()

    val currentGame = gameState
    if (currentGame != null) {
        GameBoardScreen(
            viewModel = viewModel,
            snapshot = currentGame,
            selectedDiceMode = selectedDiceMode,
            diceRollAnimState = diceRollAnimState,
            stepMoveState = stepMoveState,
            isOnlineMatch = isOnlineMatch,
            activeRoomCode = activeRoomCode,
            stickerPopups = stickerPopups,
            onExitToLobby = {
                viewModel.exitMatchToLobby()
                navController.navigate(ROUTE_LOBBY) {
                    popUpTo(ROUTE_LOBBY) { inclusive = true }
                }
            }
        )
    } else {
        NavHost(
            navController = navController,
            startDestination = ROUTE_LOBBY
        ) {
            composable(ROUTE_LOBBY) {
                LobbyScreen(
                    viewModel = viewModel,
                    lobbyState = lobbyState,
                    profile = profile,
                    waitingRoom = waitingRoom,
                    currentUserId = currentUserId,
                    fallbackName = displayName
                )
            }
            composable(ROUTE_BOARD) {
                // Handled above whenever gameState != null
            }
        }
    }
}
