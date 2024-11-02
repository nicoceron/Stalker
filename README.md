
# Stalker App

## Overview

Stalker is an Android app that shows real-time user locations on a map using Firebase and Google Maps. Users can toggle location sharing, view other users online, and have profile pictures as map markers. A light sensor also changes the map style between day and night modes.

## Features

- **Real-time Location**: Displays the current user’s marker on the map.
- **Toggle Location Sharing**: Share location with others via a toggle.
- **View Online Users**: See others’ locations in real-time.
- **Profile Picture Markers**: Uses profile pictures as map markers.
- **Day/Night Mode**: Changes map style based on ambient light.

## Prerequisites

- **Android Studio** for building the app.
- **Firebase** project with Authentication, Realtime Database, and Storage enabled.
- **Google Maps API Key** to display maps.
- **Permissions** for location and camera in the manifest.

## Setup

1. Clone the repository:
   ```bash
   git clone https://github.com/yourusername/stalker-app.git
   cd stalker-app
   ```

2. Add Firebase **google-services.json** to `app/`.

3. Add your **Google Maps API Key** in `AndroidManifest.xml`:
   ```xml
   <meta-data
       android:name="com.google.android.geo.API_KEY"
       android:value="YOUR_API_KEY" />
   ```

4. Build and run the project in Android Studio.

## Usage

1. **Register/Login** to start.
2. **Toggle Sharing**: Enable location sharing to see other users.
3. **Upload Profile Picture**: Take/upload a photo for your map marker.
4. **Day/Night Mode**: The map style adapts to ambient light automatically.

## Firebase Rules

```json
{
  "rules": {
    "users": {
      "$userId": {
        ".read": "auth != null",
        ".write": "auth != null && auth.uid == $userId"
      }
    }
  }
}
```

## Dependencies

- **Google Maps**: `com.google.android.gms:play-services-maps`
- **Firebase**: Authentication, Realtime Database, Storage
- **Glide**: `com.github.bumptech.glide:glide` for profile image loading

## License

This project is licensed under the MIT License.
