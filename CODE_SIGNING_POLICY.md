# Code signing policy

Free code signing provided by [SignPath.io](https://about.signpath.io), certificate
by [SignPath Foundation](https://signpath.org).

## Team roles and members

The project is maintained by a single maintainer, so the roles below are held by
one person and combined. That is stated openly rather than dressed up as a team.

| Role | Who | What they may do |
|---|---|---|
| **Authors** | the repository owner | commit to the source repository without a second review |
| **Reviewers** | the repository owner, plus anyone the maintainer invites to a team as reviewer | review pull requests from people who are not committers |
| **Approvers** | the repository owner | approve or refuse each code signing request |

Because the maintainer is also the approver, a second pair of eyes for signing
decisions comes from outside: SignPath verifies that the signed binary was built
from the source in this repository, and that verification is automatic and
technically enforced rather than promised.

## What is signed

Only artifacts built from the source code in this repository:

- `Kladovka-<version>.apk` — the Android application.
- `Kladovka-<version>.exe` — the Windows application.

Third-party libraries are **not** signed by this project. The desktop build
includes Compose Desktop, OkHttp, sqlite-jdbc, Kotlin stdlib and the JDK, all
under Apache License 2.0 or as system libraries. They are built into the
packages and are covered by their own upstream projects, not by our certificate.

## How signing happens

1. The release is built from the `master` branch in CI, from the sources in this
   repository. The build scripts are part of the source, so a change to how the
   artifact is produced is visible in a review.
2. A maintainer requests signing for that exact build.
3. SignPath verifies the artifact came from this repository's source and signs it
   with the SignPath Foundation certificate.

Every release needs an explicit signing request. Nothing is signed automatically.

## Privacy policy

The application stores your things on **your own** server at
`https://kladovka.dr6ter.ru`. Data is sent there only when you ask for it: by
pressing sync, by importing, or by the app reconnecting after you have signed in
and left the account signed in.

The application transfers no information to any other networked system. There is
no analytics, no crash reporting, no advertising and no telemetry. The only
third-party network destination is the one you configure or accept by using the
built-in server address, plus ordinary DNS resolution of that address.

Photos you attach to a thing are uploaded to the same server, and only when you
press sync.

If you use an account you entered yourself elsewhere, that server's own privacy
terms apply to your data there.

## System changes

The Windows build extracts its own runtime into `%LOCALAPPDATA%\Kladovka` on first
launch. That is a change to the file system and it is disclosed here rather than
done silently. Nothing else on the system is modified: no registry keys, no
startup entries, no services.

To remove the application on Windows: delete `Kladovka.exe` and the folder
`%LOCALAPPDATA%\Kladovka`. To remove it on Android: uninstall the app. Your data
stays on the server until you delete it there.