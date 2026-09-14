# PackRide Google Play Data Safety worksheet

Validate against the final SDK list and Play Console wording immediately before submission.

| Data type | Collected | Shared | Purpose | Required/optional |
|---|---:|---:|---|---|
| Email address | Yes | Service providers | Account management and app functionality | Required for account |
| User IDs | Yes | Service providers | Authentication, sync, notifications, safety delivery | Required |
| Name and profile fields | Yes | Other users and service providers | Social and group features | Name required; others optional |
| Precise location | Yes | User-selected riders/groups and service providers | Tracking, maps, group rides, Need Help, crash safety | Feature-dependent |
| Photos | Yes | User-selected audiences and service providers | Avatar, cover, feed posts | Optional |
| User-generated content | Yes | Other users and service providers | Feed, comments, communities, invitations | Optional |
| Ride/activity data | Yes | User-selected audiences and service providers | History, analytics, sharing | Feature-dependent |
| Audio | Transient processing | Agora | Live group voice | Optional; not recorded by PackRide |
| Device and other IDs | Yes | Service providers | Notifications, membership, ads | Feature-dependent |
| Advertising data | Yes | Google AdMob | Advertising, measurement, fraud prevention | Consent/region dependent |

- Data is encrypted in transit by the service providers.
- Account deletion is available in-app and through the published external page.
- Do not claim an independent security review unless one is completed.
- Reconcile Firebase, AdMob, Maps/Places, and Agora SDK disclosures before submission.
