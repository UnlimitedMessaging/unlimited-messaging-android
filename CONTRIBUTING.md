# Contributing

Thanks for helping. A few things make reviews quick.

## Before you start

- For anything larger than a small fix, open an issue first to agree on the
  approach.
- Read [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md). Most decisions there
  (long-polling, no database, no DI framework) are deliberate.
- A change to what the app sends or expects from the server also changes
  [docs/API_CONTRACT.md](docs/API_CONTRACT.md), in the same pull request.

## Workflow

1. Fork, then branch from `main`.
2. Make the change, with tests for any logic that can run on the JVM.
3. Run what CI runs:

   ```sh
   ./gradlew lintDebug testDebugUnitTest assembleDebug
   ```

4. Test on a device or emulator when you touch sending, receiving, the
   service or sign-in ([docs/DEVELOPMENT.md](docs/DEVELOPMENT.md) explains
   how to simulate SMS).
5. Open a pull request describing what changed and how you tested it.

## Commit messages

[Conventional Commits](https://www.conventionalcommits.org/):
`type(scope): description`, imperative mood.

```text
feat(sms): report the part count with each status
fix(auth): keep the refresh token when none is returned
docs(reliability): document Honor auto-start screen
```

Types: `feat`, `fix`, `refactor`, `perf`, `test`, `docs`, `ci`, `chore`.

## Style

- Kotlin official style, 4-space indent (see `.editorconfig`).
- User-facing text goes in `res/values/strings.xml` and its French
  counterpart `res/values-fr/strings.xml`. Other translations are welcome as
  new `values-xx` folders.
- No em dashes in text, comments or docs: use a hyphen, a comma or a period.
- Comments explain why, not what.

## New dependencies

The app holds SMS permissions, so every dependency is code that runs with
them. Prefer the platform and the libraries already used; justify any new
one in the pull request.
