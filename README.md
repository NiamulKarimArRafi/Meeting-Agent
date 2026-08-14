# Meeting Agent Android

Bangla + English + Banglish meeting recorder. It records in 5-minute M4A chunks for long meetings, transcribes with Groq Whisper Large V3, and summarizes with OpenRouter's free-model router.

## Build without Android Studio
Upload this entire folder to GitHub. The included GitHub Actions workflow automatically builds `app-debug.apk`. Open Actions -> Build Meeting Agent APK -> latest run -> Artifacts -> Meeting-Agent-debug-apk.

You need your own Groq and OpenRouter API keys. Enter them in the app; they are not embedded in the repository.

This is an MVP/personal-use build. For production, API calls should go through a secure backend rather than storing provider keys on-device.
