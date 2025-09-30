<div align="center">

<img src="metadata/en-US/images/icon.png" width=160 height=160 align="center">

# Xenic

### A modern, Material 3 local music player with AI-powered music classification

[![Platform](https://img.shields.io/badge/Platform-Android-green.svg?logo=android)](https://android.com/)
[![API](https://img.shields.io/badge/API-26%2B-green.svg?style=flat&logo=android)](https://android-arsenal.com/api?level=26)
[![License: GPL v3](https://img.shields.io/github/license/xenhusk/XenhuskMusic?color=orange&label=License&logo=gnu)](LICENSE.txt)

</div>

## 🎶 Key Features 

- **🤖 AI Music Classification**: Automatically classify your music as Christian or Secular using machine learning
- **🎵 Local Music Player**: Play your local music files with a beautiful Material 3 interface
- **🎧 Gapless Playback**: Enjoy uninterrupted transitions between tracks
- **🎛️ Built-in Equalizer**: Fine-tune your audio with customizable equalizer settings
- **📝 Automatic Lyrics**: Download and sync lyrics with your music
- **🎨 Material You**: Adaptive theming that matches your device's color scheme
- **📱 Modern UI**: Clean, intuitive interface designed for Android

## 🚀 Music Classification

Xenic features an advanced AI-powered music classification system that can automatically categorize your music library:

- **Christian Music Detection**: Identifies Christian music based on audio characteristics
- **Secular Music Classification**: Distinguishes secular music genres
- **Batch Processing**: Classify your entire music library at once
- **Smart Playlists**: Automatically generate playlists based on classification results
- **Local Processing**: Fast classification using optimized machine learning models

### 🤖 AI Model Details

The classification system uses a custom-trained machine learning model with:
- **Audio-Based Analysis**: 65+ audio features including tempo, harmony, rhythm, and spectral properties
- **High Accuracy**: 84.1% test accuracy with balanced Christian/Secular detection
- **Offline Operation**: No internet connection required for classification
- **Fast Processing**: ~4.9 files/second with parallel processing

**Model Source**: The AI model was trained using the [Christian Music Classifier](https://github.com/xenhusk/christian_music_classifier) project, which includes comprehensive audio feature extraction and machine learning pipeline.

## 📱 Screenshots

<div align="center">
  <img src="metadata/en-US/images/phoneScreenshots/1.jpg" width="200" alt="Screenshot 1">
  <img src="metadata/en-US/images/phoneScreenshots/2.jpg" width="200" alt="Screenshot 2">
  <img src="metadata/en-US/images/phoneScreenshots/3.jpg" width="200" alt="Screenshot 3">
</div>

## 🛠️ Technical Details

- **Minimum Android Version**: API 26 (Android 8.0)
- **Target Android Version**: API 35 (Android 15)
- **Architecture**: MVVM with Repository pattern
- **UI Framework**: Jetpack Compose with Material 3
- **Database**: Room for local data storage
- **Audio Processing**: Custom audio feature extraction and classification
- **AI Model**: Trained Random Forest classifier with 65+ audio features
- **Machine Learning**: Based on [Christian Music Classifier](https://github.com/xenhusk/christian_music_classifier) research
- **Foundation**: Built upon [Booming Music](https://github.com/mardous/BoomingMusic) by mardous

## 📋 Requirements

- Android 8.0 (API 26) or higher
- Storage permission for music files
- Internet connection (for lyrics download and initial classification)

## 🔧 Development

### Prerequisites
- Android Studio Arctic Fox or later
- JDK 11 or later
- Android SDK 26+

### Building from Source
```bash
git clone https://github.com/xenhusk/XenhuskMusic.git
cd XenhuskMusic
./gradlew assembleDebug
```

## 📄 License

This project is licensed under the GNU General Public License v3.0 - see the [LICENSE.txt](LICENSE.txt) file for details.

## 🧠 Machine Learning Research

The AI music classification system in Xenic is powered by custom machine learning research conducted in the [Christian Music Classifier](https://github.com/xenhusk/christian_music_classifier) project. This research includes:

- **Audio Feature Engineering**: 65+ carefully selected audio features
- **Model Training**: Random Forest and SVM classifiers with cross-validation
- **Performance Optimization**: 84.1% accuracy with balanced class detection
- **Offline Capability**: Complete audio analysis without internet dependency
- **Comprehensive Testing**: Extensive validation on 531+ audio files

The research demonstrates how audio characteristics can effectively distinguish between Christian and secular music without relying on lyrics or metadata.

## 👨‍💻 Author

**David Paul Desuyo (xenhusk)**
- GitHub: [@xenhusk](https://github.com/xenhusk)
- LinkedIn: [xenhusk](https://linkedin.com/in/xenhusk)
- Email: desuyodavidpaul@gmail.com

## 🙏 Acknowledgments

### Foundation Project
Xenic is built upon [Booming Music](https://github.com/mardous/BoomingMusic) by Christians Martínez Alvarado (mardous), a modern Material 3 music player for Android. This project provided the solid foundation including:

- **Modern UI Framework**: Jetpack Compose with Material 3 design
- **Music Player Core**: Gapless playback, equalizer, and audio processing
- **Library Management**: Song, album, artist, and playlist organization
- **Android Integration**: Android Auto support, widgets, and system integration
- **MVVM Architecture**: Clean, maintainable codebase structure

Special thanks to mardous and the contributors for creating such a robust and feature-rich music player that made Xenic possible.

### AI Research
The AI classification system builds upon machine learning research conducted in the [Christian Music Classifier](https://github.com/xenhusk/christian_music_classifier) project, which developed the audio-based classification algorithms and trained models used in Xenic.

---

<div align="center">
  <strong>Made with ❤️ by David Paul Desuyo</strong>
</div>