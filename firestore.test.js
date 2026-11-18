const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_uid_123";
const BOB_UID = "bob_uid_456";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function validProjectPayload(uid, overrides = {}) {
  const now = new Date(Date.now() - 2000);
  return {
    userId: uid,
    title: "Inception Scene 1 Hindi Dub",
    sourceLanguage: "English",
    targetLanguage: "Hindi",
    videoFileName: "inception_clip.mp4",
    videoUriString: "content://media/picker/video/101",
    durationMs: 24500,
    defaultGender: "MALE",
    defaultAgeGroup: "ADULT",
    defaultEffect: "HERO_DEEP",
    defaultPitch: 0.92,
    defaultSpeed: 1.0,
    matchOriginalEmotion: true,
    originalAudioVolume: 0.15,
    lineCount: 2,
    createdAt: now,
    updatedAt: now,
    ...overrides,
  };
}

function validLinePayload(uid, projectId, overrides = {}) {
  const now = new Date(Date.now() - 2000);
  return {
    userId: uid,
    projectId: projectId,
    orderIndex: 0,
    speakerName: "Hero",
    startMs: 500,
    endMs: 3800,
    englishText: "We need to go deeper into the dream.",
    hindiText: "हमें सपने के और भी भीतर जाना होगा।",
    emotion: "SUSPENSE",
    emotionIntensity: 0.85,
    voiceGender: "MALE",
    ageGroup: "ADULT",
    voiceEffect: "HERO_DEEP",
    pitchMultiplier: 0.9,
    speedMultiplier: 0.95,
    matchOriginalEmotion: true,
    createdAt: now,
    updatedAt: now,
    ...overrides,
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

test("Unauthenticated users cannot read or write projects or lines", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("projects").get()
  );
  await assertFails(
    unauthDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("projects")
      .doc("proj_1")
      .set(validProjectPayload(ALICE_UID))
  );
  await assertFails(
    unauthDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("projects")
      .doc("proj_1")
      .collection("lines")
      .doc("line_1")
      .set(validLinePayload(ALICE_UID, "proj_1"))
  );
});

test("Owner can create, list, update, and delete dubbing project and lines (single + batch)", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const projectRef = aliceDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("projects")
    .doc("proj_1");
  const lineRef = projectRef.collection("lines").doc("line_1");

  // Batch create project + line together (mirrors Repository batch creation)
  const batch = aliceDb.batch();
  batch.set(projectRef, validProjectPayload(ALICE_UID));
  batch.set(lineRef, validLinePayload(ALICE_UID, "proj_1"));
  await assertSucceeds(batch.commit());

  // List queries succeed on user subcollections
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("projects").get()
  );
  await assertSucceeds(projectRef.collection("lines").get());

  // Partial update on project voice settings succeeds
  await assertSucceeds(
    projectRef.update({
      defaultGender: "FEMALE",
      defaultAgeGroup: "YOUNG_ADULT",
      defaultEffect: "SWEET_BRIGHT",
      defaultPitch: 1.15,
      matchOriginalEmotion: false,
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Partial update on line Hindi script and voice style succeeds
  await assertSucceeds(
    lineRef.update({
      hindiText: "हमें सपने की गहराई में उतरना ही होगा।",
      voiceGender: "FEMALE",
      ageGroup: "YOUNG_ADULT",
      voiceEffect: "THEATER_ECHO",
      pitchMultiplier: 1.12,
      speedMultiplier: 1.05,
      matchOriginalEmotion: true,
      updatedAt: new Date(Date.now() - 400),
    })
  );

  await assertSucceeds(lineRef.delete());
  await assertSucceeds(projectRef.delete());
});

test("Cross-user access is strictly denied", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await db
      .collection("users")
      .doc(BOB_UID)
      .collection("projects")
      .doc("bob_proj")
      .set(validProjectPayload(BOB_UID));
    await db
      .collection("users")
      .doc(BOB_UID)
      .collection("projects")
      .doc("bob_proj")
      .collection("lines")
      .doc("bob_line")
      .set(validLinePayload(BOB_UID, "bob_proj"));
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobProjRef = aliceDb
    .collection("users")
    .doc(BOB_UID)
    .collection("projects")
    .doc("bob_proj");

  await assertFails(
    aliceDb.collection("users").doc(BOB_UID).collection("projects").get()
  );
  await assertFails(bobProjRef.get());
  await assertFails(bobProjRef.update({ title: "Hijacked" }));
  await assertFails(bobProjRef.collection("lines").get());
  await assertFails(bobProjRef.collection("lines").doc("bob_line").delete());
});

test("Schema validation rejects ghost fields, @DocumentId id fields, invalid enums, and immutable tampering", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const projectsCol = aliceDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("projects");

  // Reject ghost field
  await assertFails(
    projectsCol.doc("bad_ghost").set(
      validProjectPayload(ALICE_UID, {
        isAdmin: true,
      })
    )
  );

  // Reject @DocumentId 'id' inside payload
  await assertFails(
    projectsCol.doc("bad_id_field").set(
      validProjectPayload(ALICE_UID, {
        id: "bad_id_field",
      })
    )
  );

  // Reject invalid gender enum
  await assertFails(
    projectsCol.doc("bad_gender").set(
      validProjectPayload(ALICE_UID, {
        defaultGender: "ALIEN",
      })
    )
  );

  // Reject future timestamp
  await assertFails(
    projectsCol.doc("bad_time").set(
      validProjectPayload(ALICE_UID, {
        createdAt: new Date(Date.now() + 60000),
      })
    )
  );

  // Create valid project and verify immutable createdAt cannot be mutated
  await assertSucceeds(
    projectsCol.doc("proj_valid").set(validProjectPayload(ALICE_UID))
  );
  await assertFails(
    projectsCol.doc("proj_valid").update({
      createdAt: new Date(Date.now() - 100),
      updatedAt: new Date(Date.now() - 100),
    })
  );

  // Reject orphan line without parent project
  await assertFails(
    projectsCol
      .doc("nonexistent_proj")
      .collection("lines")
      .doc("orphan_line")
      .set(validLinePayload(ALICE_UID, "nonexistent_proj"))
  );
});
