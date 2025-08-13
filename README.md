# TrueX Client-Side Google IMA Android Reference App

## Reference app for Android TV and Fire TV TAR (TruexAdRenderer) integration
This is an AndroidTV / FireTV application that builds on top of Google's custom IMA example, https://github.com/googleads/googleads-ima-android (Advanced sample).  This app showcases how to integrate the TrueX library in combination with Google Client Side IMA ads.  The library is also often referenced to as the TrueX Ad Renderer, or TAR.


### Access the TrueX Ad Renderer Library
Add the maven repository to your build.gradle

```
repositories {
    maven {
        url "https://s3.amazonaws.com/android.truex.com/maven"
    }
}
```

Add the TAR dependency to your project
```
    // We recommend using a specific version, but using the latest patch release for any critical hotfixes
    implementation 'com.truex:TruexAdRenderer-Android:2.8.2'
```

## Steps
The following steps are a guideline for the TAR integration.  This assumes you have setup the TAR dependency above to access the renderer.
* The key IMA implementation logic happens in the VideoPlayerController, starting particularly in the AdsLoadedListener class
* The various AdEventTypes effectively control the player and ad playback through the adsManager.
* There exists a case where the user chooses not to watch a TrueX ad and needs to fallback to the regular ads.  There is special handling for this. In this case, `displayRegularAds` gets called, which will skip the TrueX placeholder ad.  However there is an undesirable UX flicker when doing this.  To get around this, the ads container is hidden before the interactive ad is shown, and it is only visible again when there is ad content to be played, primarily driven by `CONTENT_RESUME_REQUESTED` 
* The TrueX implementation the same as other integrations. Reference the `truexAdRenderer` object and the event listener `onTruexAdEvent`

## Setup

### Pre-Requisites

* [Install Android Studio](https://developer.android.com/studio/)

### Install Steps

* Clone the `master` branch of the `ReferenceApp` repository
    * `git clone https://github.com/socialvibe/truex-android-google-ima-csai-ref-app.git`

* Open ReferenceApp with Android Studio
    * Open Android Studio
    * Select `Open an existing Android Studio project` and select the ReferenceApp folder

### Run Steps

#### Run on Virtual Device
* Create a Virtual Device
    * Select `Run 'ReferenceApp'` or `Debug 'ReferenceApp'` in Android Studio
    * Select `Create New Virtual Device`
    * Select the `TV` category
    * Select a device definition
    * Select a system image
    * Select `Finish` to finish creating the Virtual Device
* Select `Run 'app'` or `Debug 'app'` in Android Studio
* Select the virtual device and press `OK`