# PackRide Shared Firebase Contract

This is the canonical wire contract shared by PackRide iOS and Android. Neither
client may create platform-prefixed copies of these records.

## Identity

- Firebase Auth UID owns accounts, profiles, social relationships, feed data,
  invitations, scheduled rides, safety requests, and shared lap sessions.
- Stable installation/device ID keys live group riders and community members.
- Every device-keyed member record includes `authUID`, which bridges it to the
  account for privacy checks, notifications, follows, and migration.
- `users/{authUID}/deviceID` identifies the current installation for database
  rules and legacy ownership checks.

## Realtime Database paths

| Path | Canonical key / purpose |
|---|---|
| `users/{uid}` | Private account data, profile, FCM token, social inboxes |
| `publicRiders/{uid}` | Public rider discovery profile |
| `users/{uid}/rideInvites/{senderUID}` | Current group-ride invitation from one sender |
| `rides/{rideCode}/riders/{deviceID}` | Live group rider state; contains `authUID` |
| `rideMembers/{rideCode}/{uid}` | Private authorization membership |
| `communities/{communityID}` | Community metadata and device-keyed members |
| `users/{uid}/communityMemberships/{communityID}` | Account reverse index, value `true` |
| `scheduledRides/{rideID}` | Canonical scheduled ride and RSVP data |
| `feedPosts/{postID}` | Feed post, reactions, comments, and media references |
| `locationFeeds/{viewerUID}/{riderUID}` | Private per-viewer location fan-out |
| `groupLocationFeeds/{rideCode}/{viewerUID}` | Private group location fan-out |
| `helpRequests/{senderUID}` | Sender-owned active Need Help request |
| `crashIncidents/{incidentID}` | Crash event and acknowledgement state |
| `moderationReports/{reportID}` | Append-only moderation report |
| `users/{uid}/sharedLapSessions/{ownerUID}_{sessionID}` | Shared lap inbox item |

## Ride invitation fields

`senderName`, `rideCode`, `destinationName`, `stopCount`, and `timestamp`.
Android also tolerantly reads the retired `fromUID`, `fromName`, `id`, and
`message` fields so invitations written by older builds are not lost.

## Storage paths

- Feed photos use the same URL/reference stored by `feedPosts` on both clients.
- Shared lap GPX uses `sharedLapGpx/{ownerUID}/{sessionID}.gpx`.
- Ride-history GPX references must remain readable after reinstall and offline
  caching; clients must not reinterpret another platform's path.

## Values and time

- Firebase numeric values must be decoded as generic numbers (`NSNumber` /
  `Number`) before conversion; never assume an integer or floating wire type.
- Normal event timestamps are Unix seconds as `Double`.
- `users/{uid}/rideHistory.date` retains the historical iOS Codable reference
  date representation (seconds since 2001-01-01); Android converts at its
  persistence boundary.
- Speeds stored in shared live records use the existing field-specific units;
  a client must convert only at UI boundaries.

## Lifecycle guarantees

- Login restores local profile and community caches from the account records.
- Joining/creating a community writes both the device member and account index.
- Missing device member records are repaired without resetting a live rider's
  status/location. Legacy Android auth-UID-keyed members migrate to device IDs.
- A missing community removes its stale local cache and reverse-index entry; it
  must never be recreated as a partial node.
- Blocking and privacy enforcement happens in shared Cloud Functions/rules, not
  only by hiding data in one client.
- Schema evolution must use tolerant readers first, canonical writers second,
  and remove legacy decoding only after deployed-version adoption is known.

## Release gate

Before release, deploy the reviewed database rules and Cloud Functions, then
run Android-to-iPhone and iPhone-to-Android tests for invitations, group rides,
communities, live locations, follows, feed media/comments, scheduled rides,
Need Help, crash notifications, blocking, and Agora voice.
