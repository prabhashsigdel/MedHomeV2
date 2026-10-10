# MedHome tools

Two small Node projects next to the Android app. Neither is part of the app build.

| Folder | What it does |
| --- | --- |
| `rules-tests/` | Tests `firestore.rules` against the local Firestore emulator. |
| `seed/` | Writes the fictional doctors catalogue to Firestore with the Admin SDK. |

## What you need installed

- **Node.js 20 or newer** (`node --version`).
- **Java 11 or newer** for the Firestore emulator (`java -version`).
- Nothing global: the Firebase CLI is a dev dependency of `rules-tests/` (run it with `npx firebase`).

## Rules tests

```sh
cd tools/rules-tests
npm ci          # first time only
npm test        # starts the emulator, runs every test, stops it
```

The first run downloads the emulator (about 60 MB). Tests live in `rules-tests/test/` and
cover users (sign-up as patient only, no role/email/createdAt changes by anyone, admins
included, no cross-user access, owner delete), doctors (signed-in read of active ones, no deletes),
bookings (`bookings.rules.test.js`: booking a slot, double booking, unverified email, past and
off-schedule slots, booking for someone else, the 3-upcoming limit, the `patientName` copy of
the profile name, reading and cancelling with `cancelledBy: "patient"` and `cancelledAt` at the
server time, never changed afterwards, and a deleted account blanking `patientName` on its own
bookings, to `""` only) and admins
(`admin.rules.test.js`: adding and editing doctors with every field validated, show / hide, the
`updatedAt` / `updatedBy` stamp, no deletes; patients, doctors and signed-out users can't write
doctors or make themselves admins; only admins with a verified email may list every doctor's
bookings by status (the Bookings tab), and patients still list only their own; admins can't
read patient profiles, and the only booking write they may make is cancelling an upcoming one
for the clinic, naming themselves in `cancelledByUid`, stamping `cancelledAt` at the server
time, giving a `cancelReason` code (and optionally a one-line `cancelNote` of at most 150
characters; patients can never set either, and neither changes afterwards) and freeing exactly
its slot lock and quota place).

## Seeding doctors

By default the seed only **adds doctors that don't exist yet** (every doctor in
`seed/doctors.js` has a fixed ID, `doc-001` ...). A doctor already in Firestore is left exactly
as it is, so changes admins made in the app survive a re-run. To replace existing doctors'
details from `doctors.js` on purpose, pass `--overwrite`: each catalogue field is replaced in
full, but `active` is kept (a doctor an admin hid stays hidden). Doctors not in `doctors.js`
(for example ones added in the app) are never touched. Everything the seed writes is stamped
`updatedBy: "seed"`. All names and hospitals are fictional.

### 1. Get a service account key (once)

1. Open the [Firebase console](https://console.firebase.google.com/) and pick the MedHome project.
2. Click the gear next to **Project Overview**, then **Project settings**.
3. Open the **Service accounts** tab. **Firebase Admin SDK** is selected; Node.js is fine.
4. Click **Generate new private key**, then **Generate key**. A JSON file downloads.
5. Move it **outside this repository**, for example `~/keys/medhome-admin.json` (Windows:
   `C:\Users\<you>\keys\medhome-admin.json`). The seed script refuses a key inside the project
   folder, and `.gitignore` ignores `*serviceAccount*.json` and `*-firebase-adminsdk-*.json` as a
   second guard.

The key has full admin access to the project: never commit it, share it or paste it anywhere.
If it ever leaks, delete it under **Service accounts** in Google Cloud IAM and make a new one.

### 2. Run the seed

```sh
cd tools/seed
npm ci                                        # first time only
npm run check                                 # validates doctors.js, writes nothing
node seed.js --key ~/keys/medhome-admin.json  # adds any of the 12 doctors that are missing
node seed.js --key ~/keys/medhome-admin.json --overwrite  # also replaces existing ones (undoes admin edits)
```

Or set the key once per shell instead of `--key`:

```sh
export GOOGLE_APPLICATION_CREDENTIALS=~/keys/medhome-admin.json              # macOS/Linux, Git Bash
$env:GOOGLE_APPLICATION_CREDENTIALS = "$HOME\keys\medhome-admin.json"        # PowerShell
node seed.js
```

To try it on the emulator instead (no key needed), with the emulator running:

```sh
FIRESTORE_EMULATOR_HOST=127.0.0.1:8080 node seed.js --project demo-medhome
```

## Publishing the rules and indexes

Run the rules tests first. Then:

```sh
cd tools/rules-tests
npx firebase login                     # once; opens the browser
npx firebase deploy --config ../../firebase.json --only firestore:indexes --project <your-project-id>
npx firebase deploy --config ../../firebase.json --only firestore:rules --project <your-project-id>
```

Deploy the indexes first: the Bookings tab (`bookings` by `patientUid`, newest first), taken
slots (`slotLocks` by `doctorId` and `startAt`), the admin's upcoming bookings of a doctor
(`bookings` by `doctorId`, `status` and `startAt`) and the admin's Bookings tab (`bookings` by
`status` and `startAt`, one index each way) need the composite indexes in
`firestore.indexes.json`, and their queries fail until those finish building (a few minutes;
see Firestore > Indexes in the console).

The project ID is shown in Project settings (it is also `project_id` in `app/google-services.json`).
Deploying publishes `firestore.rules` exactly as it is in your working copy.

The rules and the app change together: the rules require `patientName` on new bookings,
`cancelledBy` on cancels, and `cancelledByUid` (the admin's own uid) and `cancelReason` on clinic
cancels, which older app builds don't write (their booking and cancelling would be refused). Install the matching app
build when you deploy the rules. Deploy the rules before releasing a build: a new build on old
rules can't cancel for the clinic or delete an account (blanking the booking names is refused),
while on new rules only older admin builds lose clinic cancels until updated.

## Making an account an admin

Admins can add, edit and hide doctors and see and cancel each doctor's upcoming bookings in the app. The
role is never granted in the app: set it by hand.

1. Sign up in the app as usual (this creates `users/{uid}` with `role: "patient"`) and verify
   the email: the rules refuse every admin read and write from an unverified account.
2. In the Firebase console open **Firestore Database**, collection `users`, and find your
   document (its ID is your UID, shown under **Authentication > Users**).
3. Change the `role` field from `patient` to `admin` and save.
4. Sign out of the app and sign in again (or restart it): the doctors panel opens instead of
   the patient tabs.

To undo it, set `role` back to `patient`. `doctor` gives the doctor placeholder screen.
