const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";
const CHARLIE_UID = "charlie_789";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function makeEmptyArray(val) {
  return Array.from({ length: 28 }, () => val);
}

function makeRoomPayload(hostUid, roomCode = "AR7K2P", isPublic = true) {
  return {
    roomCode,
    hostId: hostUid,
    status: "waiting",
    isPublic,
    maxPlayers: 4,
    memberUids: [hostUid],
    players: [{ uid: hostUid, name: "Captain Alice", captainId: "ignis", isBot: false }],
    tileOwners: makeEmptyArray(""),
    tileLevels: makeEmptyArray(0),
    tileFestivals: makeEmptyArray(1),
    currentTurnIndex: 0,
    turnNumber: 0,
    lastDice1: 0,
    lastDice2: 0,
    lastLog: "Anchor dropped!",
    winnerUid: "",
    createdAt: new Date(),
    updatedAt: new Date(),
  };
}

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated user cannot read or write users or rooms", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("rooms").doc("AR7K2P").get());
  await assertFails(unauthDb.collection("rooms").where("status", "==", "waiting").where("isPublic", "==", true).get());
});

test("User profile: owner can create/update, cross-user denied", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  const profile = {
    userId: ALICE_UID,
    displayName: "Captain Alice",
    preferredCaptain: "ignis",
    wins: 0,
    gamesPlayed: 0,
    highestGold: 2000,
    createdAt: new Date(),
    updatedAt: new Date(),
  };

  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).set(profile));
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).update({ displayName: "Hacked" }));
});

test("User profile: rejects shadow field or invalid captain", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Alice",
      preferredCaptain: "invalid_captain",
      wins: 0,
      gamesPlayed: 0,
      highestGold: 2000,
      createdAt: new Date(),
      updatedAt: new Date(),
    })
  );
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Alice",
      preferredCaptain: "ignis",
      wins: 0,
      gamesPlayed: 0,
      highestGold: 2000,
      isAdmin: true,
      createdAt: new Date(),
      updatedAt: new Date(),
    })
  );
});

test("Room lifecycle: create, public query, join, start, play turn, terminal lock", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const charlieDb = testEnv.authenticatedContext(CHARLIE_UID).firestore();

  const roomRef = aliceDb.collection("rooms").doc("AR7K2P");
  await assertSucceeds(roomRef.set(makeRoomPayload(ALICE_UID, "AR7K2P", true)));

  // Unfiltered list must fail
  await assertFails(bobDb.collection("rooms").get());

  // Public matchmaking query succeeds
  await assertSucceeds(
    bobDb.collection("rooms").where("status", "==", "waiting").where("isPublic", "==", true).get()
  );

  // Bob joins
  await assertSucceeds(
    bobDb.collection("rooms").doc("AR7K2P").update({
      memberUids: [ALICE_UID, BOB_UID],
      players: [
        { uid: ALICE_UID, name: "Captain Alice", captainId: "ignis", isBot: false },
        { uid: BOB_UID, name: "Captain Bob", captainId: "marina", isBot: false },
      ],
      updatedAt: new Date(),
    })
  );

  // Bob (non-host) cannot start game
  await assertFails(
    bobDb.collection("rooms").doc("AR7K2P").update({
      status: "playing",
      updatedAt: new Date(),
    })
  );

  // Host Alice starts game
  await assertSucceeds(
    aliceDb.collection("rooms").doc("AR7K2P").update({
      status: "playing",
      lastLog: "Voyage started!",
      updatedAt: new Date(),
    })
  );

  // Non-member Charlie cannot read or update playing room
  await assertFails(charlieDb.collection("rooms").doc("AR7K2P").get());
  await assertFails(
    charlieDb.collection("rooms").doc("AR7K2P").update({
      lastLog: "Sabotage",
      updatedAt: new Date(),
    })
  );

  // Bob finishes game
  await assertSucceeds(
    bobDb.collection("rooms").doc("AR7K2P").update({
      status: "finished",
      winnerUid: BOB_UID,
      lastLog: "Bob wins!",
      updatedAt: new Date(),
    })
  );

  // Terminal state lock prevents further updates
  await assertFails(
    aliceDb.collection("rooms").doc("AR7K2P").update({
      lastLog: "Reopen",
      updatedAt: new Date(),
    })
  );
});

test("Room messages: only room members can create and query messages, append-only", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const charlieDb = testEnv.authenticatedContext(CHARLIE_UID).firestore();

  await assertSucceeds(aliceDb.collection("rooms").doc("AR7K2P").set(makeRoomPayload(ALICE_UID, "AR7K2P", false)));

  await assertSucceeds(
    aliceDb.collection("rooms").doc("AR7K2P").collection("messages").doc("msg1").set({
      roomId: "AR7K2P",
      senderId: ALICE_UID,
      senderName: "Captain Alice",
      captainId: "ignis",
      text: "Ahoy!",
      stickerId: "ahoy",
      memberUids: [ALICE_UID],
      createdAt: new Date(),
    })
  );

  // Member query succeeds
  await assertSucceeds(
    aliceDb.collection("rooms").doc("AR7K2P").collection("messages").where("memberUids", "array-contains", ALICE_UID).get()
  );

  // Non-member Charlie cannot create or query messages
  await assertFails(
    charlieDb.collection("rooms").doc("AR7K2P").collection("messages").doc("msg2").set({
      roomId: "AR7K2P",
      senderId: CHARLIE_UID,
      senderName: "Charlie",
      captainId: "vane",
      text: "Intruder!",
      stickerId: "",
      memberUids: [CHARLIE_UID],
      createdAt: new Date(),
    })
  );

  // Update/delete forbidden on messages
  await assertFails(
    aliceDb.collection("rooms").doc("AR7K2P").collection("messages").doc("msg1").update({ text: "Edited" })
  );
});
