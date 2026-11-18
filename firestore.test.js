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

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

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

test("Unauthenticated user: denied on all collections", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  const past = new Date(Date.now() - 2000);
  await assertFails(
    unauthDb.collection("public_profiles").where("discoverable", "==", true).get()
  );
  await assertFails(
    unauthDb.collection("videos").where("published", "==", true).get()
  );
  await assertFails(
    unauthDb.collection("public_profiles").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Alice",
      username: "alice",
      bio: "",
      discoverable: true,
      createdAt: past,
      updatedAt: past,
    })
  );
});

test("Public profiles & private account isolation: owner writes succeed, cross-user writes and PII reads fail", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const past = new Date(Date.now() - 5000);

  await assertSucceeds(
    aliceDb.collection("public_profiles").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Alice Creator",
      username: "alice_creates",
      bio: "Short films & reels",
      photoUrl: "https://example.com/alice.jpg",
      discoverable: true,
      createdAt: past,
      updatedAt: past,
    })
  );

  // Partial update on profile succeeds
  await assertSucceeds(
    aliceDb.collection("public_profiles").doc(ALICE_UID).update({
      displayName: "Alice Vlogs",
      bio: "Updated bio",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Cross-user update rejected
  await assertFails(
    bobDb.collection("public_profiles").doc(ALICE_UID).update({
      bio: "Hacked bio",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Unfiltered public_profiles list rejected; filtered query succeeds
  await assertFails(bobDb.collection("public_profiles").get());
  await assertSucceeds(
    bobDb.collection("public_profiles").where("discoverable", "==", true).get()
  );

  // Private account PII isolation
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("private")
      .doc("account")
      .set({
        userId: ALICE_UID,
        email: "alice@example.com",
        createdAt: past,
        updatedAt: past,
      })
  );
  await assertFails(
    bobDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("private")
      .doc("account")
      .get()
  );
});

test("Videos: creator create/update/delete, filtered list, and rejection of tampered or cross-user edits", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const past = new Date(Date.now() - 5000);

  await assertSucceeds(
    aliceDb.collection("videos").doc("vid_1").set({
      creatorId: ALICE_UID,
      creatorName: "Alice Creator",
      creatorUsername: "alice_creates",
      title: "Sunset Time Lapse",
      caption: "Shot on phone #nature",
      videoUrl: "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4",
      videoType: "SHORT",
      durationSec: 15,
      viewsCount: 0,
      published: true,
      createdAt: past,
      updatedAt: past,
    })
  );

  // Filtered list succeeds; unfiltered list fails
  await assertSucceeds(
    bobDb.collection("videos").where("published", "==", true).get()
  );
  await assertFails(bobDb.collection("videos").get());

  // Cross-user title update fails; owner update succeeds; +1 view increment succeeds; +50 view inflation fails
  await assertFails(
    bobDb.collection("videos").doc("vid_1").update({
      title: "Stolen",
      updatedAt: new Date(Date.now() - 1000),
    })
  );
  await assertSucceeds(
    bobDb.collection("videos").doc("vid_1").update({
      viewsCount: 1,
      updatedAt: new Date(Date.now() - 1000),
    })
  );
  await assertFails(
    bobDb.collection("videos").doc("vid_1").update({
      viewsCount: 50,
      updatedAt: new Date(Date.now() - 1000),
    })
  );
  await assertSucceeds(
    aliceDb.collection("videos").doc("vid_1").update({
      title: "Golden Sunset Reel",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Ghost field & future timestamp rejected
  await assertFails(
    aliceDb.collection("videos").doc("vid_2").set({
      id: "vid_2",
      creatorId: ALICE_UID,
      creatorName: "Alice",
      creatorUsername: "alice",
      title: "Bad payload",
      caption: "",
      videoUrl: "https://example.com/v.mp4",
      videoType: "SHORT",
      durationSec: 10,
      viewsCount: 0,
      published: true,
      createdAt: past,
      updatedAt: past,
    })
  );
  await assertFails(
    aliceDb.collection("videos").doc("vid_3").set({
      creatorId: ALICE_UID,
      creatorName: "Alice",
      creatorUsername: "alice",
      title: "Future time",
      caption: "",
      videoUrl: "https://example.com/v.mp4",
      videoType: "FULL",
      durationSec: 60,
      viewsCount: 0,
      published: true,
      createdAt: new Date(Date.now() + 60000),
      updatedAt: past,
    })
  );
});

test("Social graph (follows, subscriptions, video_likes): strict identity enforcement", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const past = new Date(Date.now() - 3000);

  await assertSucceeds(
    aliceDb.collection("follows").doc(`${ALICE_UID}_${BOB_UID}`).set({
      followerId: ALICE_UID,
      targetUserId: BOB_UID,
      visible: true,
      createdAt: past,
    })
  );
  // Cannot spoof someone else's follow
  await assertFails(
    bobDb.collection("follows").doc(`${ALICE_UID}_${CHARLIE_UID}`).set({
      followerId: ALICE_UID,
      targetUserId: CHARLIE_UID,
      visible: true,
      createdAt: past,
    })
  );
  // Cannot follow yourself
  await assertFails(
    aliceDb.collection("follows").doc(`${ALICE_UID}_${ALICE_UID}`).set({
      followerId: ALICE_UID,
      targetUserId: ALICE_UID,
      visible: true,
      createdAt: past,
    })
  );

  await assertSucceeds(
    aliceDb.collection("subscriptions").doc(`${ALICE_UID}_${BOB_UID}`).set({
      subscriberId: ALICE_UID,
      creatorId: BOB_UID,
      visible: true,
      createdAt: past,
    })
  );
  await assertSucceeds(
    aliceDb.collection("video_likes").doc(`${ALICE_UID}_vid_1`).set({
      userId: ALICE_UID,
      videoId: "vid_1",
      visible: true,
      createdAt: past,
    })
  );

  // Filtered queries matching Kotlin repository
  await assertSucceeds(
    bobDb.collection("follows").where("visible", "==", true).get()
  );
  await assertSucceeds(
    bobDb.collection("subscriptions").where("visible", "==", true).get()
  );
  await assertSucceeds(
    bobDb.collection("video_likes").where("visible", "==", true).get()
  );
});

test("Direct messages (chat_threads & messages): participants allowed, third-party denied", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const charlieDb = testEnv.authenticatedContext(CHARLIE_UID).firestore();
  const threadId = `${ALICE_UID}_${BOB_UID}`;
  const past = new Date(Date.now() - 3000);

  await assertSucceeds(
    aliceDb.collection("chat_threads").doc(threadId).set({
      participantIds: [ALICE_UID, BOB_UID],
      lastMessage: "Hey Bob!",
      lastSenderId: ALICE_UID,
      createdAt: past,
      updatedAt: past,
    })
  );

  await assertSucceeds(
    aliceDb
      .collection("chat_threads")
      .doc(threadId)
      .collection("messages")
      .doc("msg_1")
      .set({
        senderId: ALICE_UID,
        recipientId: BOB_UID,
        participantIds: [ALICE_UID, BOB_UID],
        text: "Hey Bob!",
        createdAt: past,
      })
  );

  // Bob can query threads & messages where participantIds contains Bob
  await assertSucceeds(
    bobDb
      .collection("chat_threads")
      .where("participantIds", "array-contains", BOB_UID)
      .get()
  );
  await assertSucceeds(
    bobDb
      .collection("chat_threads")
      .doc(threadId)
      .collection("messages")
      .where("participantIds", "array-contains", BOB_UID)
      .get()
  );

  // Charlie cannot read or send into Alice-Bob chat
  await assertFails(
    charlieDb.collection("chat_threads").doc(threadId).get()
  );
  await assertFails(
    charlieDb
      .collection("chat_threads")
      .doc(threadId)
      .collection("messages")
      .doc("msg_2")
      .set({
        senderId: CHARLIE_UID,
        recipientId: BOB_UID,
        participantIds: [CHARLIE_UID, BOB_UID],
        text: "Intruder",
        createdAt: past,
      })
  );
});

test("Stories: 24h story create/list and owner-only delete", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const past = new Date(Date.now() - 2000);
  const expiresAt = Date.now() + 24 * 60 * 60 * 1000;

  await assertSucceeds(
    aliceDb.collection("stories").doc("story_1").set({
      creatorId: ALICE_UID,
      creatorName: "Alice",
      creatorUsername: "alice",
      mediaType: "IMAGE",
      mediaUrl: "data:image/jpeg;base64,abc123",
      caption: "Behind the scenes!",
      expiresAtEpochMs: expiresAt,
      active: true,
      createdAt: past,
    })
  );

  await assertSucceeds(
    bobDb.collection("stories").where("active", "==", true).get()
  );
  await assertFails(bobDb.collection("stories").get());
  await assertFails(bobDb.collection("stories").doc("story_1").delete());
  await assertSucceeds(aliceDb.collection("stories").doc("story_1").delete());
});

test("Live streams & live comments: host controls live state, viewers can join and chat", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const past = new Date(Date.now() - 2000);

  await assertSucceeds(
    aliceDb.collection("live_streams").doc("live_1").set({
      hostId: ALICE_UID,
      hostName: "Alice",
      hostUsername: "alice",
      title: "Q&A With Followers!",
      viewerCount: 1,
      live: true,
      createdAt: past,
      updatedAt: past,
    })
  );

  await assertSucceeds(
    bobDb.collection("live_streams").where("live", "==", true).get()
  );

  // Bob can increment viewerCount by 1, cannot end Alice's stream
  await assertSucceeds(
    bobDb.collection("live_streams").doc("live_1").update({
      viewerCount: 2,
      updatedAt: new Date(Date.now() - 1000),
    })
  );
  await assertFails(
    bobDb.collection("live_streams").doc("live_1").update({
      live: false,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Bob sends a live comment
  await assertSucceeds(
    bobDb
      .collection("live_streams")
      .doc("live_1")
      .collection("comments")
      .doc("c_1")
      .set({
        streamId: "live_1",
        senderId: BOB_UID,
        senderName: "Bob",
        text: "Hello from the live stream!",
        visible: true,
        createdAt: past,
      })
  );

  await assertSucceeds(
    aliceDb
      .collection("live_streams")
      .doc("live_1")
      .collection("comments")
      .where("visible", "==", true)
      .get()
  );

  // Host ends live stream
  await assertSucceeds(
    aliceDb.collection("live_streams").doc("live_1").update({
      live: false,
      updatedAt: new Date(Date.now() - 500),
    })
  );
});

test("Gaming live streams: screen/PC mode with gameTitle and agoraChannel allowed, invalid mode denied", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const past = new Date(Date.now() - 2000);

  await assertSucceeds(
    aliceDb.collection("live_streams").doc("live_game_1").set({
      hostId: ALICE_UID,
      hostName: "Alice Gamer",
      hostUsername: "alicegamer",
      title: "Ranked Push BGMI Solo vs Squad",
      streamMode: "MOBILE_GAME_SCREEN",
      gameTitle: "BGMI",
      agoraChannel: "live_game_1",
      viewerCount: 1,
      live: true,
      createdAt: past,
      updatedAt: past,
    })
  );

  await assertSucceeds(
    bobDb.collection("live_streams").where("live", "==", true).get()
  );

  await assertFails(
    aliceDb.collection("live_streams").doc("live_game_bad").set({
      hostId: ALICE_UID,
      hostName: "Alice Gamer",
      hostUsername: "alicegamer",
      title: "Invalid Mode",
      streamMode: "UNKNOWN_MODE",
      viewerCount: 1,
      live: true,
      createdAt: past,
      updatedAt: past,
    })
  );
});

test("Video thumbnails: custom data-URI thumbnails allowed on Short and Full videos", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const past = new Date(Date.now() - 2000);
  const sampleThumb = "data:image/jpeg;base64,/9j/4AAQSkZJRgABAQAAAQABAAD/";

  await assertSucceeds(
    aliceDb.collection("videos").doc("vid_short_thumb").set({
      creatorId: ALICE_UID,
      creatorName: "Alice",
      creatorUsername: "alice",
      title: "Short With Frame Cover",
      caption: "#shorts",
      videoUrl: "https://example.com/short.mp4",
      thumbnailUrl: sampleThumb,
      videoType: "SHORT",
      durationSec: 15,
      viewsCount: 0,
      published: true,
      createdAt: past,
      updatedAt: past,
    })
  );

  await assertSucceeds(
    aliceDb.collection("videos").doc("vid_full_thumb").set({
      creatorId: ALICE_UID,
      creatorName: "Alice",
      creatorUsername: "alice",
      title: "Full Video With Custom Cover",
      caption: "HD Tutorial",
      videoUrl: "https://example.com/full.mp4",
      thumbnailUrl: sampleThumb,
      videoType: "FULL",
      durationSec: 320,
      viewsCount: 0,
      published: true,
      createdAt: past,
      updatedAt: past,
    })
  );
});

test("Withdrawal requests: owner-only UPI and Bank payouts enforced, cross-user & forged status denied", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const past = new Date(Date.now() - 2000);

  // UPI withdrawal succeeds
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("withdrawal_requests")
      .doc("wd_upi_1")
      .set({
        userId: ALICE_UID,
        amountUsd: 25.5,
        payoutMethod: "UPI",
        accountHolderName: "Alice Creator",
        upiId: "alice@okaxis",
        status: "PENDING",
        createdAt: past,
        updatedAt: past,
      })
  );

  // Bank transfer withdrawal succeeds
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("withdrawal_requests")
      .doc("wd_bank_1")
      .set({
        userId: ALICE_UID,
        amountUsd: 50.0,
        payoutMethod: "BANK_TRANSFER",
        accountHolderName: "Alice Creator",
        bankName: "State Bank of India",
        accountNumber: "123456789012",
        ifscCode: "SBIN0001234",
        status: "PENDING",
        createdAt: past,
        updatedAt: past,
      })
  );

  // Cross-user read & write denied
  await assertFails(
    bobDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("withdrawal_requests")
      .get()
  );
  await assertFails(
    bobDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("withdrawal_requests")
      .doc("wd_forged")
      .set({
        userId: BOB_UID,
        amountUsd: 10.0,
        payoutMethod: "UPI",
        accountHolderName: "Bob",
        upiId: "bob@upi",
        status: "PENDING",
        createdAt: past,
        updatedAt: past,
      })
  );

  // Cannot create directly as COMPLETED or below minimum ($1.00) or update status client-side
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("withdrawal_requests")
      .doc("wd_completed")
      .set({
        userId: ALICE_UID,
        amountUsd: 10.0,
        payoutMethod: "UPI",
        accountHolderName: "Alice Creator",
        upiId: "alice@okaxis",
        status: "COMPLETED",
        createdAt: past,
        updatedAt: past,
      })
  );
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("withdrawal_requests")
      .doc("wd_tiny")
      .set({
        userId: ALICE_UID,
        amountUsd: 0.25,
        payoutMethod: "UPI",
        accountHolderName: "Alice Creator",
        upiId: "alice@okaxis",
        status: "PENDING",
        createdAt: past,
        updatedAt: past,
      })
  );
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("withdrawal_requests")
      .doc("wd_upi_1")
      .update({
        status: "COMPLETED",
        updatedAt: new Date(Date.now() - 1000),
      })
  );

  // Owner can list & cancel pending withdrawal
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("withdrawal_requests")
      .get()
  );
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("withdrawal_requests")
      .doc("wd_upi_1")
      .delete()
  );
});

