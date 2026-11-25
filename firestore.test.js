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

function makeValidEntry(uid, now = new Date(Date.now() - 2000)) {
  return {
    userId: uid,
    dateKey: "2026-11-25",
    title: "Sera di pioggia e pensieri veloci",
    rawTranscript: "Oggi giornata lunga ma alla fine ho chiuso tutto.",
    reorganizedText: "Giornata intensa, chiusa con un momento di calma.",
    mainTheme: "Ritmo lavorativo",
    themes: ["lavoro", "calma"],
    sentimentLabel: "Stanco ma sollevato",
    sentimentValence: 4,
    mentalClarity: 3,
    reflectionQuestion: "Qual e' stata la prima cosa che hai lasciato andare appena hai chiuso il computer?",
    reflectionAnswer: "",
    takeaway: "Chiudere in orario fa la differenza.",
    usedVoice: true,
    createdAt: now,
    updatedAt: now,
  };
}

function makeValidSummary(uid, now = new Date(Date.now() - 2000)) {
  return {
    userId: uid,
    periodLabel: "19 - 25 Nov 2026",
    headline: "Una settimana in cerca di spazio",
    summary: "Hai parlato spesso di scadenze, ma nelle ultime sere emerge piu' leggerezza.",
    recurringSentiments: ["Lucido", "Stanco ma sereno"],
    recurringThemes: ["lavoro", "riposo"],
    recurringPatterns: "Quando stacchi prima delle 20, il tono sale.",
    weeklyQuestion: "Cosa vorresti proteggere di piu' la prossima settimana?",
    entryCount: 5,
    averageValence: 3.6,
    createdAt: now,
    updatedAt: now,
  };
}

function makeValidProfile(uid, now = new Date(Date.now() - 2000)) {
  return {
    userId: uid,
    preferredName: "Ale",
    ageRange: "26-35",
    lifestyleTags: ["Lavoro intenso", "Sport"],
    lifestyleNotes: "Orari pieni fino alle 19, poi cerco di staccare.",
    keyRelationships: ["Partner", "Amici stretti"],
    relationshipsNote: "Vedo poco gli amici durante la settimana.",
    mainConcerns: ["Equilibrio lavoro-vita", "Sonno"],
    concernsNote: "Faccio fatica a spegnere la testa la sera.",
    reflectionStyle: "Domande dirette e pratiche",
    onboardingCompleted: true,
    onboardingSkipped: false,
    createdAt: now,
    updatedAt: now,
  };
}

function makeValidAphorism(uid, now = new Date(Date.now() - 2000)) {
  return {
    userId: uid,
    entryId: "entry_1",
    quoteText: "Non serve risolvere domani stasera: basta posare il peso sul comodino.",
    attribution: "Originale · Buonanotte",
    momentLabel: "Buonanotte",
    themeTag: "Ritmo lavorativo",
    createdAt: now,
    updatedAt: now,
  };
}

test("Unauthenticated user cannot read or write entries, weeklySummaries, profile, or aphorisms", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection(`users/${ALICE_UID}/entries`).get());
  await assertFails(
    unauthDb
      .collection(`users/${ALICE_UID}/entries`)
      .doc("entry_1")
      .set(makeValidEntry(ALICE_UID))
  );
  await assertFails(
    unauthDb.collection(`users/${ALICE_UID}/weeklySummaries`).get()
  );
  await assertFails(
    unauthDb
      .collection(`users/${ALICE_UID}/profile`)
      .doc("main")
      .set(makeValidProfile(ALICE_UID))
  );
  await assertFails(
    unauthDb
      .collection(`users/${ALICE_UID}/aphorisms`)
      .doc("aph_1")
      .set(makeValidAphorism(ALICE_UID))
  );
});

test("Owner can create, list, partially update, and delete diary entries", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const entriesRef = aliceDb.collection(`users/${ALICE_UID}/entries`);

  await assertSucceeds(entriesRef.doc("entry_1").set(makeValidEntry(ALICE_UID)));
  await assertSucceeds(entriesRef.orderBy("createdAt", "desc").get());

  await assertSucceeds(
    entriesRef.doc("entry_1").update({
      reflectionAnswer: "Ho posato il telefono in un'altra stanza.",
      updatedAt: new Date(Date.now() - 500),
    })
  );

  await assertFails(
    entriesRef.doc("entry_2").set({
      id: "entry_2",
      ...makeValidEntry(ALICE_UID),
    })
  );
  await assertFails(
    entriesRef.doc("entry_1").update({
      createdAt: new Date(Date.now() - 500),
      updatedAt: new Date(Date.now() - 500),
    })
  );
  await assertFails(
    entriesRef.doc("entry_1").update({
      sentimentValence: 99,
      updatedAt: new Date(Date.now() - 500),
    })
  );
  await assertFails(
    entriesRef.doc("entry_3").set(
      makeValidEntry(ALICE_UID, new Date(Date.now() + 60000))
    )
  );

  await assertSucceeds(entriesRef.doc("entry_1").delete());
});

test("Cross-user access to entries, weeklySummaries, profile, and aphorisms is denied", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx
      .firestore()
      .collection(`users/${BOB_UID}/entries`)
      .doc("bob_1")
      .set(makeValidEntry(BOB_UID));
    await ctx
      .firestore()
      .collection(`users/${BOB_UID}/weeklySummaries`)
      .doc("sum_1")
      .set(makeValidSummary(BOB_UID));
    await ctx
      .firestore()
      .collection(`users/${BOB_UID}/profile`)
      .doc("main")
      .set(makeValidProfile(BOB_UID));
    await ctx
      .firestore()
      .collection(`users/${BOB_UID}/aphorisms`)
      .doc("aph_1")
      .set(makeValidAphorism(BOB_UID));
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(aliceDb.collection(`users/${BOB_UID}/entries`).get());
  await assertFails(
    aliceDb.collection(`users/${BOB_UID}/entries`).doc("bob_1").get()
  );
  await assertFails(
    aliceDb
      .collection(`users/${BOB_UID}/entries`)
      .doc("bob_1")
      .update({ title: "Hijacked", updatedAt: new Date(Date.now() - 500) })
  );
  await assertFails(
    aliceDb.collection(`users/${BOB_UID}/weeklySummaries`).get()
  );
  await assertFails(
    aliceDb.collection(`users/${BOB_UID}/profile`).doc("main").get()
  );
  await assertFails(
    aliceDb.collection(`users/${BOB_UID}/aphorisms`).get()
  );
});

test("Owner can create, update, and read user profile; rejects invalid writes", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const profileRef = aliceDb.collection(`users/${ALICE_UID}/profile`).doc("main");

  await assertSucceeds(profileRef.set(makeValidProfile(ALICE_UID)));
  await assertSucceeds(profileRef.get());
  await assertSucceeds(
    profileRef.update({
      preferredName: "Alessandro",
      updatedAt: new Date(Date.now() - 500),
    })
  );
  await assertFails(
    profileRef.update({
      createdAt: new Date(Date.now() - 500),
      updatedAt: new Date(Date.now() - 500),
    })
  );
});

test("Owner can create, list, and delete weeklySummaries and aphorisms", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const summariesRef = aliceDb.collection(`users/${ALICE_UID}/weeklySummaries`);
  const aphorismsRef = aliceDb.collection(`users/${ALICE_UID}/aphorisms`);

  await assertSucceeds(summariesRef.doc("sum_1").set(makeValidSummary(ALICE_UID)));
  await assertSucceeds(summariesRef.orderBy("createdAt", "desc").get());
  await assertFails(
    summariesRef.doc("sum_1").update({
      userId: BOB_UID,
      updatedAt: new Date(Date.now() - 500),
    })
  );
  await assertSucceeds(summariesRef.doc("sum_1").delete());

  await assertSucceeds(aphorismsRef.doc("aph_1").set(makeValidAphorism(ALICE_UID)));
  await assertSucceeds(aphorismsRef.orderBy("createdAt", "desc").get());
  await assertFails(
    aphorismsRef.doc("aph_2").set({
      ...makeValidAphorism(ALICE_UID),
      quoteText: "",
    })
  );
  await assertSucceeds(aphorismsRef.doc("aph_1").delete());
});
