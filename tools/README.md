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
cover users (sign-up as patient only, no role/email/createdAt changes, no cross-user access,
admin role changes, owner delete) and doctors (signed-in read only, no client writes).

## Seeding doctors

The seed is idempotent: every doctor has a fixed ID (`doc-001` ...) and is overwritten in
full, so running it twice gives the same result. Doctors not in `seed/doctors.js` are left
alone. All names and hospitals are fictional.

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
node seed.js --key ~/keys/medhome-admin.json  # writes the 12 doctors
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

## Publishing the rules

Run the rules tests first. Then:

```sh
cd tools/rules-tests
npx firebase login                     # once; opens the browser
npx firebase deploy --config ../../firebase.json --only firestore:rules --project <your-project-id>
```

The project ID is shown in Project settings (it is also `project_id` in `app/google-services.json`).
Deploying publishes `firestore.rules` exactly as it is in your working copy.
