# FleetControl Android

A multi-tenant fleet management application for logistics businesses. Enables fleet owners to track trips, manage drivers, handle fuel requests, and monitor profitability in real-time.

## Features

### Owner Dashboard
- **Real-time Trip Tracking**: Monitor all driver trips with live sync
- **Pending Approvals**: Approve/reject driver-submitted trips and fuel requests
- **Profit Analytics**: Daily, monthly, and custom date range profit reports
- **Driver Management**: Add drivers, set rate slabs, track performance

### Driver App
- **Trip Logging**: Submit trips with company, client, pickup, and bag count
- **Fuel Requests**: Submit fuel expenses for owner approval
- **Earnings Dashboard**: View personal earnings and trip history

### Multi-Tenant Architecture
- Complete data isolation between fleet owners
- Drivers join via invite codes
- Real-time bidirectional sync (Firestore)

## Tech Stack

| Layer | Technology |
|-------|------------|
| UI | Jetpack Compose + Material 3 |
| Architecture | MVVM + Repository Pattern |
| Local Database | Room (with offline-first) |
| Cloud | Firebase Firestore |
| Auth | Firebase Auth (Email/Password) |
| Sync | Real-time Firestore listeners |
| DI | Manual (AppContainer) |

## Project Structure

```
com.fleetcontrol/
├── core/          # App config, session, settings
├── data/          # Room DB, DAOs, Repositories
├── domain/        # Business logic, calculators
├── security/      # Rate limiting, validation
├── services/      # Auth, backup, billing
├── ui/            # Screens, components, theme
├── viewmodel/     # ViewModels (MVVM)
└── work/          # Background sync workers
```

## Setup

1. Clone the repository
2. Add `google-services.json` to `/app/`
3. Create `keystore.properties` for release signing:
   ```properties
   storeFile=path/to/keystore.jks
   storePassword=xxx
   keyAlias=xxx
   keyPassword=xxx
   ```
4. Build: `./gradlew assembleDebug`

## Security

- Firebase Security Rules enforce multi-tenant data isolation
- Rate limiting on auth operations
- ProGuard enabled for release builds
- No hardcoded secrets in codebase

## License

Proprietary - All rights reserved
